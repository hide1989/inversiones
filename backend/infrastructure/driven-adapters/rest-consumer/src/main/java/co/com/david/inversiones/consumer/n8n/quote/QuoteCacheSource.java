package co.com.david.inversiones.consumer.n8n.quote;

import co.com.david.inversiones.consumer.config.CacheConfig;
import co.com.david.inversiones.consumer.n8n.N8nGateway;
import co.com.david.inversiones.consumer.n8n.N8nSafeCall;
import co.com.david.inversiones.model.Quote;
import co.com.david.inversiones.model.Ticker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Fetches and merges the full set of current quotes from both price endpoints, cached for five
 * minutes.
 *
 * <p>Separated from {@link QuoteProviderAdapter} so the {@link Cacheable} proxy sits on a distinct
 * bean: a method cannot intercept its own calls through Spring AOP, so caching the merge here — and
 * having {@code QuoteProviderAdapter} call across to this bean — is what makes a cache hit skip
 * {@link N8nGateway} (and therefore the network) entirely, while a cache miss still goes through
 * {@code N8nGateway}'s own per-endpoint circuit breakers and retries exactly once.
 *
 * <p>A failure in either endpoint (via {@link N8nSafeCall}) simply contributes nothing to the merge
 * rather than failing the whole lookup — one exchange being down should not blank out the other.
 */
@Component
class QuoteCacheSource {

    private static final Logger log = LoggerFactory.getLogger(QuoteCacheSource.class);

    private final N8nGateway gateway;

    QuoteCacheSource(N8nGateway gateway) {
        this.gateway = gateway;
    }

    @Cacheable(cacheNames = CacheConfig.QUOTES_CACHE, key = "'all'")
    Map<Ticker, Quote> allQuotes() {
        Map<Ticker, Quote> merged = new LinkedHashMap<>();
        merged.putAll(QuoteMapper.fromPreciosHoy(
                N8nSafeCall.safely(log, "/precios-hoy", gateway::fetchPreciosHoy)));
        merged.putAll(QuoteMapper.fromBvcPrecios(
                N8nSafeCall.safely(log, "/bvc-precios", gateway::fetchBvcPrecios)));
        return merged;
    }
}
