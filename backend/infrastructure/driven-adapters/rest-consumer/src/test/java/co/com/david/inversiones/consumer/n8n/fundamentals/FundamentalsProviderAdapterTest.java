package co.com.david.inversiones.consumer.n8n.fundamentals;

import co.com.david.inversiones.consumer.n8n.N8nGateway;
import co.com.david.inversiones.consumer.support.Fixtures;
import co.com.david.inversiones.consumer.support.TestRestClients;
import co.com.david.inversiones.model.Fundamentals;
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
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests against the real {@code /analizar-empresa} (MSFT, an equity) and {@code /etf-ficha}
 * (SPY, an ETF) payloads captured in {@code src/test/resources/n8n}.
 *
 * <p>The captured {@code analizar-empresa} response reports {@code es_etf: false} for MSFT, which
 * settled the "does FundamentalsProvider need /etf-ficha too" question this task's spec raised:
 * yes, because an ETF ticker needs a second call to a workflow that actually has its multiples.
 */
class FundamentalsProviderAdapterTest {

    private MockWebServer mockWebServer;
    private FundamentalsProviderAdapter adapter;
    private final Map<String, Supplier<MockResponse>> responses = new HashMap<>();

    @BeforeEach
    void setUp() throws IOException {
        mockWebServer = new MockWebServer();
        mockWebServer.start();
        RestClient restClient = TestRestClients.forMockServer(mockWebServer);
        adapter = new FundamentalsProviderAdapter(new N8nGateway(restClient, restClient));
        responses.clear();
        mockWebServer.setDispatcher(new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest request) {
                Supplier<MockResponse> supplier = responses.get(request.getPath());
                if (supplier == null) {
                    throw new IllegalStateException("Unexpected path: " + request.getPath());
                }
                return supplier.get();
            }
        });
    }

    @AfterEach
    void tearDown() throws IOException {
        mockWebServer.shutdown();
    }

    private static Supplier<MockResponse> okFixture(String name) {
        return () -> new MockResponse()
                .setResponseCode(HttpStatus.OK.value())
                .addHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .setBody(Fixtures.load(name));
    }

    @Test
    void mapsTheCapturedEquityPayloadWithoutCallingEtfFicha() {
        responses.put("/analizar-empresa", okFixture("analizar-empresa"));
        responses.put("/etf-ficha", () -> {
            throw new AssertionError("etf-ficha must not be called for a non-ETF ticker");
        });

        Optional<Fundamentals> result = adapter.fetchFundamentals(Ticker.of("MSFT"));

        assertThat(result).isPresent();
        Fundamentals fundamentals = result.get();
        assertThat(fundamentals.isEtf()).isFalse();
        assertThat(fundamentals.hasData()).isTrue();
        assertThat(fundamentals.per()).isEqualByComparingTo("27.9164");
        assertThat(fundamentals.roe()).isEqualByComparingTo("33.22");
        assertThat(fundamentals.debtToEquity()).isEqualByComparingTo("0.2416");
        assertThat(fundamentals.dividendYield()).isEqualByComparingTo("0.79485");
    }

    @Test
    void anEquityMarkedAsEtfTriggersASecondCallToEtfFichaAndMapsItsResponse() {
        responses.put("/analizar-empresa", okFixture("analizar-empresa"));
        responses.put("/etf-ficha", okFixture("etf-ficha"));
        // Force es_etf true so the second call is exercised, using the real analizar-empresa shape.
        String forcedEtf = Fixtures.load("analizar-empresa").replace("\"es_etf\":false", "\"es_etf\":true");
        responses.put("/analizar-empresa", () -> new MockResponse()
                .setResponseCode(HttpStatus.OK.value())
                .addHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .setBody(forcedEtf));

        Optional<Fundamentals> result = adapter.fetchFundamentals(Ticker.of("SPY"));

        assertThat(result).isPresent();
        Fundamentals fundamentals = result.get();
        assertThat(fundamentals.isEtf()).isTrue();
        assertThat(fundamentals.hasData()).isTrue();
        assertThat(fundamentals.beta()).isEqualByComparingTo("1.0172414");
        assertThat(fundamentals.priceToBook()).isNull();
        assertThat(fundamentals.roe()).isNull();
    }

    @Test
    void anEtfWhoseSecondCallFailsStillReturnsARecognizedButDatalessResult() {
        String forcedEtf = Fixtures.load("analizar-empresa").replace("\"es_etf\":false", "\"es_etf\":true");
        responses.put("/analizar-empresa", () -> new MockResponse()
                .setResponseCode(HttpStatus.OK.value())
                .addHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .setBody(forcedEtf));
        responses.put("/etf-ficha", () -> new MockResponse().setResponseCode(HttpStatus.INTERNAL_SERVER_ERROR.value()));

        Optional<Fundamentals> result = adapter.fetchFundamentals(Ticker.of("SPY"));

        assertThat(result).isPresent();
        assertThat(result.get().isEtf()).isTrue();
        assertThat(result.get().hasData()).isTrue();
        assertThat(result.get().beta()).isNull();
    }

    @Test
    void aFailureOfAnalizarEmpresaDegradesToEmptyRatherThanThrowing() {
        responses.put("/analizar-empresa", () -> new MockResponse().setResponseCode(HttpStatus.INTERNAL_SERVER_ERROR.value()));

        Optional<Fundamentals> result = adapter.fetchFundamentals(Ticker.of("MSFT"));

        assertThat(result).isEmpty();
    }
}
