package co.com.david.inversiones.consumer.n8n.exchangerate;

import co.com.david.inversiones.consumer.config.CacheConfig;
import co.com.david.inversiones.consumer.n8n.N8nGateway;
import co.com.david.inversiones.consumer.n8n.N8nSafeCall;
import co.com.david.inversiones.consumer.n8n.dto.TrmResponseDto;
import co.com.david.inversiones.model.ExchangeRate;
import co.com.david.inversiones.model.gateways.TimeProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Fetches today's TRM, cached until local midnight under {@value CacheConfig#TRM_CACHE}.
 *
 * <p>Separated from {@link ExchangeRateProviderAdapter} for the same reason as
 * {@code QuoteCacheSource}: the {@code Cacheable} proxy needs to sit on a bean distinct from the
 * one that calls it, or self-invocation would bypass the cache entirely.
 *
 * <p><b>Why this returns {@code Optional.empty()} instead of {@code inversiones.base-trm} on
 * failure.</b> An earlier version of this class fell back to the configured base TRM — a
 * historical weighted-average <em>purchase</em> rate, currently 3568.42 versus a real TRM around
 * 3912 — stamped with {@code timeProvider.today()}. That made a fabricated number
 * indistinguishable from a real reading: nothing in the domain or on the wire could tell a caller
 * "this is a guess". Worse, {@code CalculateTotalReturnUseCase} computes the currency effect as
 * {@code invested x (currentTrm - baseTrm)}, so when the fallback equals {@code baseTrm} that
 * effect came out as an exact, confident zero — reporting "no exchange-rate movement" when the
 * truth was "the rate is unknown". This project already treats absence as a first-class outcome
 * elsewhere (a missing quote carries a position at cost and is flagged {@code priced=false}; a
 * NULL signal score is excluded rather than treated as zero; the AI synthesis provider refuses to
 * serve persisted data as current) and the TRM fallback was the one place that rule was broken. If
 * {@code /trm-hoy} is unreachable even after retries and the circuit breaker has nothing to offer
 * (caught via {@link N8nSafeCall}), the honest answer is "unknown", and the callers that need a
 * rate must report that unavailability explicitly rather than receive a substitute value.
 */
@Component
class ExchangeRateCacheSource {

    private static final Logger log = LoggerFactory.getLogger(ExchangeRateCacheSource.class);

    private final N8nGateway gateway;
    private final TimeProvider timeProvider;

    ExchangeRateCacheSource(N8nGateway gateway, TimeProvider timeProvider) {
        this.gateway = gateway;
        this.timeProvider = timeProvider;
    }

    @Cacheable(cacheNames = CacheConfig.TRM_CACHE, key = "'today'")
    Optional<ExchangeRate> currentRate() {
        Optional<TrmResponseDto> response = N8nSafeCall.safely(log, "/trm-hoy", gateway::fetchTrm);
        return response
                .filter(dto -> dto.trm() != null)
                .map(dto -> ExchangeRate.copPerUsd(dto.trm(),
                        dto.fecha() != null ? dto.fecha() : timeProvider.today()));
    }
}
