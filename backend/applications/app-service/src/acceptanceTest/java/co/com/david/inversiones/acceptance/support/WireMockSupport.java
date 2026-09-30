package co.com.david.inversiones.acceptance.support;

import com.github.tomakehurst.wiremock.WireMockServer;
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
 * or {@link #stubJson} afterward to override just that endpoint for its scenario.
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

    public WireMockSupport(WireMockServer server, N8nFixtures fixtures, CacheManager cacheManager) {
        this.server = server;
        this.fixtures = fixtures;
        this.cacheManager = cacheManager;
    }

    /** Restores every endpoint to its real fixture payload and evicts the quote/TRM caches. */
    public void resetToHappyPath() {
        server.resetAll();
        HAPPY_PATH.forEach((path, fixture) -> stubJson(path, fixtures.read(fixture)));
        evict("quotes");
        evict("trm");
    }

    /** Overrides one endpoint with an arbitrary raw JSON body. */
    public void stubJson(String path, String rawJson) {
        server.stubFor(get(urlEqualTo(path)).willReturn(okJson(rawJson)));
    }

    /** Overrides one endpoint to answer with a 500, simulating n8n being down. */
    public void stubFailure(String path) {
        server.stubFor(get(urlEqualTo(path)).willReturn(serverError()));
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
