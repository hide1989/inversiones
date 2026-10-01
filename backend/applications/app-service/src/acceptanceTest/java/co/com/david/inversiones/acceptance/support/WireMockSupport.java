package co.com.david.inversiones.acceptance.support;

import com.github.tomakehurst.wiremock.WireMockServer;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;

import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.serverError;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;

/**
 * Stubs every n8n webhook this application calls, standing in for n8n during the acceptance
 * suite, and resets the two caches ({@code quotes}, {@code trm}) that would otherwise let an
 * earlier scenario's successful call mask a later scenario's WireMock override.
 *
 * <p>{@link #resetToHappyPath()} restores every GET endpoint to the real fixture payload it
 * returns in production; a step definition that needs one source down calls {@link #stubFailure}
 * or {@link #stubJson} afterward to override just that endpoint for its scenario. It also resets
 * every Resilience4j circuit breaker to {@code CLOSED}: a scenario that drives enough failures to
 * trip one (the degradation suite, or the TRM-down scenario below) would otherwise leave it open
 * for every later scenario in the same Cucumber run, since all scenarios share one Spring context
 * and circuit breaker state is not scoped per request the way the WireMock stubs are.
 */
@Component
public class WireMockSupport {

    /** Every n8n GET endpoint this application calls, and the fixture answering it in the happy path. */
    private static final Map<String, String> HAPPY_PATH = Map.ofEntries(
            Map.entry("/trm-hoy", "trm-hoy.json"),
            Map.entry("/precios-hoy", "precios-hoy.json"),
            Map.entry("/bvc-precios", "bvc-precios.json"),
            Map.entry("/indicadores-tecnicos", "indicadores-tecnicos.json"),
            Map.entry("/news-sentiment", "news-sentiment.json"),
            Map.entry("/ratings-analistas", "ratings-analistas.json"),
            Map.entry("/insider-trading", "insider-trading.json"),
            Map.entry("/stocktwits", "stocktwits.json"),
            Map.entry("/reddit", "reddit.json"),
            Map.entry("/sintesis-ia", "sintesis-ia.json"),
            Map.entry("/earnings", "earnings-sample.json"),
            Map.entry("/watchlist", "watchlist.json"));

    private final WireMockServer server;
    private final N8nFixtures fixtures;
    private final CacheManager cacheManager;
    private final CircuitBreakerRegistry circuitBreakerRegistry;

    public WireMockSupport(WireMockServer server, N8nFixtures fixtures, CacheManager cacheManager,
            CircuitBreakerRegistry circuitBreakerRegistry) {
        this.server = server;
        this.fixtures = fixtures;
        this.cacheManager = cacheManager;
        this.circuitBreakerRegistry = circuitBreakerRegistry;
    }

    /**
     * Restores every endpoint to its real fixture payload, evicts the quote/TRM caches, and resets
     * every circuit breaker to {@code CLOSED}.
     *
     * <p>Without the circuit breaker reset, a scenario that fails an endpoint enough times to trip
     * its breaker (every n8n endpoint down, or the TRM source down) leaves that breaker open for
     * every scenario that runs afterward in the same suite, since Resilience4j's breaker state
     * lives on the shared Spring context, not per request. A later scenario would then see "source
     * unavailable" even though WireMock has already been reset back to the happy path for it.
     */
    public void resetToHappyPath() {
        server.resetAll();
        HAPPY_PATH.forEach((path, fixture) -> stubJson(path, fixtures.read(fixture)));
        evict("quotes");
        evict("trm");
        circuitBreakerRegistry.getAllCircuitBreakers().forEach(CircuitBreaker::reset);
    }

    /** Overrides one endpoint with an arbitrary raw JSON body. */
    public void stubJson(String path, String rawJson) {
        server.stubFor(get(urlEqualTo(path)).willReturn(okJson(rawJson)));
    }

    /** Overrides one endpoint to answer with a 500, simulating n8n being down. */
    public void stubFailure(String path) {
        server.stubFor(get(urlEqualTo(path)).willReturn(serverError()));
    }

    /**
     * Evicts the cached TRM so the next read actually calls {@code /trm-hoy} again rather than
     * returning whatever rate an earlier step in the same scenario already cached. A step that
     * takes the TRM source down after another request has already populated the cache must call
     * this, or the override is invisible until the cache's own midnight expiry.
     */
    public void evictTrmCache() {
        evict("trm");
    }

    /** Makes every n8n GET endpoint fail, simulating n8n being entirely down. */
    public void stubEveryEndpointFailing() {
        HAPPY_PATH.keySet().forEach(this::stubFailure);
    }

    private void evict(String cacheName) {
        Cache cache = cacheManager.getCache(cacheName);
        if (cache != null) {
            cache.clear();
        }
    }
}
