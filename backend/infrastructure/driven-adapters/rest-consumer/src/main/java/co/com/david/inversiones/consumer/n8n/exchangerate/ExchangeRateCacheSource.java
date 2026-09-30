package co.com.david.inversiones.consumer.n8n.exchangerate;

import co.com.david.inversiones.consumer.config.CacheConfig;
import co.com.david.inversiones.consumer.n8n.N8nGateway;
import co.com.david.inversiones.consumer.n8n.N8nSafeCall;
import co.com.david.inversiones.consumer.n8n.dto.TrmResponseDto;
import co.com.david.inversiones.model.ExchangeRate;
import co.com.david.inversiones.model.gateways.TimeProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * Fetches today's TRM, cached until local midnight under {@value CacheConfig#TRM_CACHE}.
 *
 * <p>Separated from {@link ExchangeRateProviderAdapter} for the same reason as
 * {@code QuoteCacheSource}: the {@code Cacheable} proxy needs to sit on a bean distinct from the
 * one that calls it, or self-invocation would bypass the cache entirely.
 *
 * <p>If {@code /trm-hoy} is unreachable even after retries and the circuit breaker has nothing to
 * offer (caught via {@link N8nSafeCall}), this falls back to {@code inversiones.base-trm} — a
 * configured, deliberately conservative rate — rather than throwing. A rate that is a few days stale
 * still lets the rest of the portfolio value; refusing to answer at all would take down every
 * USD/COP conversion in the application over one flaky webhook.
 */
@Component
class ExchangeRateCacheSource {

    private static final Logger log = LoggerFactory.getLogger(ExchangeRateCacheSource.class);

    private final N8nGateway gateway;
    private final TimeProvider timeProvider;
    private final BigDecimal baseTrm;

    ExchangeRateCacheSource(N8nGateway gateway, TimeProvider timeProvider,
            @Value("${inversiones.base-trm}") BigDecimal baseTrm) {
        this.gateway = gateway;
        this.timeProvider = timeProvider;
        this.baseTrm = baseTrm;
    }

    @Cacheable(cacheNames = CacheConfig.TRM_CACHE, key = "'today'")
    ExchangeRate currentRate() {
        Optional<TrmResponseDto> response = N8nSafeCall.safely(log, "/trm-hoy", gateway::fetchTrm);
        return response
                .filter(dto -> dto.trm() != null)
                .map(dto -> ExchangeRate.copPerUsd(dto.trm(),
                        dto.fecha() != null ? dto.fecha() : timeProvider.today()))
                .orElseGet(() -> ExchangeRate.copPerUsd(baseTrm, timeProvider.today()));
    }
}
