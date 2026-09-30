package co.com.david.inversiones.consumer.n8n;

import co.com.david.inversiones.consumer.RestConsumerTestApplication;
import co.com.david.inversiones.consumer.support.Fixtures;
import co.com.david.inversiones.model.Quote;
import co.com.david.inversiones.model.SignalSnapshot;
import co.com.david.inversiones.model.Ticker;
import co.com.david.inversiones.model.gateways.ExchangeRateProvider;
import co.com.david.inversiones.model.gateways.QuoteProvider;
import co.com.david.inversiones.model.gateways.SignalProvider;
import co.com.david.inversiones.model.gateways.TimeProvider;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Boots the real Spring context (proxies included) to prove two behaviours that only exist through
 * Spring AOP and therefore cannot be exercised by instantiating the adapters directly:
 *
 * <ul>
 *   <li>the five-minute quote cache actually collapses repeated calls into one upstream request;
 *   <li>a circuit breaker actually opens after its configured failure count and then stops calling
 *       upstream at all, rather than merely degrading each individual failed call.
 * </ul>
 *
 * <p>Resilience4j and Spring's cache abstraction both work by wrapping a bean in a dynamic proxy;
 * a plain {@code new SomeAdapter(...)} in a unit test never goes through that proxy, so this is the
 * only place these two properties are actually checked rather than assumed.
 */
@SpringBootTest(classes = RestConsumerTestApplication.class)
class N8nResilienceSpringTest {

    private static MockWebServer mockWebServer;
    private static final AtomicInteger STOCKTWITS_CALLS = new AtomicInteger();
    private static final Map<String, AtomicInteger> REQUEST_COUNTS = new ConcurrentHashMap<>();

    @Autowired
    private QuoteProvider quoteProvider;

    @Autowired
    private SignalProvider signalProvider;

    @Autowired
    private ExchangeRateProvider exchangeRateProvider;

    @BeforeAll
    static void startServer() throws IOException {
        mockWebServer = new MockWebServer();
        mockWebServer.setDispatcher(new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest request) {
                String path = request.getPath();
                REQUEST_COUNTS.computeIfAbsent(path, p -> new AtomicInteger()).incrementAndGet();
                if ("/stocktwits".equals(path)) {
                    STOCKTWITS_CALLS.incrementAndGet();
                    return new MockResponse().setResponseCode(HttpStatus.INTERNAL_SERVER_ERROR.value());
                }
                String fixture = switch (path) {
                    case "/precios-hoy" -> "precios-hoy";
                    case "/bvc-precios" -> "bvc-precios";
                    case "/indicadores-tecnicos" -> "indicadores-tecnicos";
                    case "/news-sentiment" -> "news-sentiment";
                    case "/ratings-analistas" -> "ratings-analistas";
                    case "/insider-trading" -> "insider-trading";
                    case "/reddit" -> "reddit";
                    case "/trm-hoy" -> "trm-hoy";
                    default -> throw new IllegalStateException("Unexpected path: " + path);
                };
                return new MockResponse()
                        .setResponseCode(HttpStatus.OK.value())
                        .addHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .setBody(Fixtures.load(fixture));
            }
        });
        mockWebServer.start();
    }

    @AfterAll
    static void stopServer() throws IOException {
        mockWebServer.shutdown();
    }

    @DynamicPropertySource
    static void n8nBaseUrl(DynamicPropertyRegistry registry) {
        registry.add("inversiones.n8n.base-url", () -> mockWebServer.url("/").toString());
    }

    @TestConfiguration
    static class StubTimeProviderConfig {
        @Bean
        TimeProvider timeProvider() {
            return new TimeProvider() {
                @Override
                public LocalDate today() {
                    return LocalDate.of(2026, 9, 30);
                }

                @Override
                public Instant now() {
                    return Instant.parse("2026-09-30T12:00:00Z");
                }
            };
        }
    }

    @Test
    void twoCallsWithinTheCacheTtlProduceOnlyOneUpstreamRequestPerQuoteEndpoint() {
        Set<Ticker> tickers = Set.of(Ticker.of("QQQ"), Ticker.of("TERPEL"));

        quoteProvider.findQuotes(tickers);
        quoteProvider.findQuotes(tickers);

        assertThat(REQUEST_COUNTS.getOrDefault("/precios-hoy", new AtomicInteger()).get()).isEqualTo(1);
        assertThat(REQUEST_COUNTS.getOrDefault("/bvc-precios", new AtomicInteger()).get()).isEqualTo(1);
    }

    @Test
    void theCircuitBreakerOpensAfterTheConfiguredFailureCountAndStopsCallingUpstream() {
        for (int i = 0; i < 6; i++) {
            signalProvider.fetchSnapshots(Set.of(Ticker.of("QQQ")), LocalDate.of(2026, 9, 30));
        }
        int callsAfterWarmup = STOCKTWITS_CALLS.get();

        for (int i = 0; i < 6; i++) {
            signalProvider.fetchSnapshots(Set.of(Ticker.of("QQQ")), LocalDate.of(2026, 9, 30));
        }
        int callsAfterMore = STOCKTWITS_CALLS.get();

        // Once the breaker for the "stocktwits" instance opens, further calls are short-circuited
        // (CallNotPermittedException) rather than reaching the mock server, so the request count
        // must stop growing well before another six calls would have added six more requests.
        assertThat(callsAfterMore - callsAfterWarmup).isLessThan(6);
    }
}
