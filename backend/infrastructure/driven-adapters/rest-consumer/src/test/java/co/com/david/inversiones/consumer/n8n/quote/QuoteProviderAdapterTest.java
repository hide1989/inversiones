package co.com.david.inversiones.consumer.n8n.quote;

import co.com.david.inversiones.consumer.n8n.N8nGateway;
import co.com.david.inversiones.consumer.support.Fixtures;
import co.com.david.inversiones.consumer.support.TestRestClients;
import co.com.david.inversiones.model.Currency;
import co.com.david.inversiones.model.Quote;
import co.com.david.inversiones.model.Ticker;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests against the real {@code /precios-hoy} and {@code /bvc-precios} payloads captured in
 * {@code src/test/resources/n8n}.
 *
 * <p>The one rule that matters most here: a quote's currency comes from which endpoint produced it,
 * never from a default. This project previously shipped a Critical defect from exactly this kind of
 * mistake on the amount side, so the currency is asserted explicitly, not inferred from the amount.
 */
class QuoteProviderAdapterTest {

    private MockWebServer mockWebServer;
    private QuoteProviderAdapter adapter;

    @BeforeEach
    void setUp() throws IOException {
        mockWebServer = new MockWebServer();
        mockWebServer.start();
        mockWebServer.setDispatcher(new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest request) {
                String body = switch (request.getPath()) {
                    case "/precios-hoy" -> Fixtures.load("precios-hoy");
                    case "/bvc-precios" -> Fixtures.load("bvc-precios");
                    default -> throw new IllegalStateException("Unexpected path: " + request.getPath());
                };
                return new MockResponse()
                        .setResponseCode(HttpStatus.OK.value())
                        .addHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .setBody(body);
            }
        });

        RestClient restClient = TestRestClients.forMockServer(mockWebServer);
        N8nGateway gateway = new N8nGateway(restClient, restClient);
        QuoteCacheSource cacheSource = new QuoteCacheSource(gateway);
        adapter = new QuoteProviderAdapter(cacheSource);
    }

    @AfterEach
    void tearDown() throws IOException {
        mockWebServer.shutdown();
    }

    @Test
    void aBvcPriceMapsToCopAndANasdaqPriceMapsToUsd() {
        Ticker terpel = Ticker.of("TERPEL");
        Ticker qqq = Ticker.of("QQQ");

        Map<Ticker, Quote> quotes = adapter.findQuotes(Set.of(terpel, qqq));

        Quote terpelQuote = quotes.get(terpel);
        assertThat(terpelQuote).isNotNull();
        assertThat(terpelQuote.price().currency()).isEqualTo(Currency.COP);
        assertThat(terpelQuote.price().amount()).isEqualByComparingTo("19100");

        Quote qqqQuote = quotes.get(qqq);
        assertThat(qqqQuote).isNotNull();
        assertThat(qqqQuote.price().currency()).isEqualTo(Currency.USD);
        assertThat(qqqQuote.price().amount()).isEqualByComparingTo("737.93");
    }

    @Test
    void omitsTickersNotPresentInEitherUpstreamPayload() {
        Map<Ticker, Quote> quotes = adapter.findQuotes(Set.of(Ticker.of("NOPE")));

        assertThat(quotes).isEmpty();
    }
}
