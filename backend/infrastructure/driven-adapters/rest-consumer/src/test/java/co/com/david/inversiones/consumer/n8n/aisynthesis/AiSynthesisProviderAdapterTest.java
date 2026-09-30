package co.com.david.inversiones.consumer.n8n.aisynthesis;

import co.com.david.inversiones.consumer.n8n.N8nGateway;
import co.com.david.inversiones.consumer.support.Fixtures;
import co.com.david.inversiones.consumer.support.TestRestClients;
import co.com.david.inversiones.model.AiSynthesis;
import co.com.david.inversiones.model.Ticker;
import co.com.david.inversiones.model.gateways.TimeProvider;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Unit tests against the real and a hand-built {@code /sintesis-ia} payload captured/derived in
 * {@code src/test/resources/n8n}. The live capture on 2026-09-30 had every one of its 15 tickers
 * marked {@code sin_datos} (a bad LLM day upstream), so a second fixture,
 * {@code sintesis-ia-mixed.json}, was hand-built from the real field shapes to exercise the
 * "some tickers have data, one does not" case this adapter must handle correctly.
 */
@ExtendWith(MockitoExtension.class)
class AiSynthesisProviderAdapterTest {

    private MockWebServer mockWebServer;
    private AiSynthesisProviderAdapter adapter;

    @Mock
    private TimeProvider timeProvider;

    @BeforeEach
    void setUp() throws IOException {
        mockWebServer = new MockWebServer();
        mockWebServer.start();
        RestClient restClient = TestRestClients.forMockServer(mockWebServer);
        adapter = new AiSynthesisProviderAdapter(new N8nGateway(restClient), timeProvider);
    }

    @AfterEach
    void tearDown() throws IOException {
        mockWebServer.shutdown();
    }

    @Test
    void mapsTheRealCapturedPayloadAndMarksEveryTickerAsSinDatos() {
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(HttpStatus.OK.value())
                .addHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .setBody(Fixtures.load("sintesis-ia")));

        List<AiSynthesis> result = adapter.fetchSyntheses(Set.of(Ticker.of("AMZN"), Ticker.of("GOOGL")));

        assertThat(result).hasSize(2);
        assertThat(result).allSatisfy(s -> assertThat(s.hasData()).isFalse());
    }

    @Test
    void mapsTheMixedFixtureDistinguishingTickersWithAndWithoutData() {
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(HttpStatus.OK.value())
                .addHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .setBody(Fixtures.load("sintesis-ia-mixed")));

        List<AiSynthesis> result = adapter.fetchSyntheses(
                Set.of(Ticker.of("NVDA"), Ticker.of("MSFT"), Ticker.of("TSLA")));

        AiSynthesis nvda = result.stream().filter(s -> s.ticker().equals(Ticker.of("NVDA"))).findFirst().orElseThrow();
        AiSynthesis tsla = result.stream().filter(s -> s.ticker().equals(Ticker.of("TSLA"))).findFirst().orElseThrow();

        assertThat(nvda.hasData()).isTrue();
        assertThat(nvda.iaScore()).isEqualByComparingTo("8");
        assertThat(nvda.signal()).isEqualTo("FUERTE COMPRA");
        assertThat(nvda.catalysts()).containsExactly("Demanda de datacenters", "Nuevos contratos hyperscaler");
        assertThat(tsla.hasData()).isFalse();
    }

    @Test
    void filtersToOnlyTheRequestedTickers() {
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(HttpStatus.OK.value())
                .addHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .setBody(Fixtures.load("sintesis-ia-mixed")));

        List<AiSynthesis> result = adapter.fetchSyntheses(Set.of(Ticker.of("NVDA")));

        assertThat(result).extracting(s -> s.ticker().symbol()).containsExactly("NVDA");
    }

    @Test
    void anUpstreamFailureDegradesToAnEmptyListRatherThanThrowing() {
        mockWebServer.enqueue(new MockResponse().setResponseCode(HttpStatus.INTERNAL_SERVER_ERROR.value()));

        List<AiSynthesis> result = adapter.fetchSyntheses(Set.of(Ticker.of("NVDA")));

        assertThat(result).isEmpty();
    }

    @Test
    void fallsBackToTodayWhenTheGeneradoTimestampCannotBeParsed() {
        String malformed = Fixtures.load("sintesis-ia-mixed").replace(
                "\"2026-09-28T13:01:09.470Z\"", "\"not-a-date\"");
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(HttpStatus.OK.value())
                .addHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .setBody(malformed));
        when(timeProvider.today()).thenReturn(LocalDate.of(2026, 9, 30));

        List<AiSynthesis> result = adapter.fetchSyntheses(Set.of(Ticker.of("NVDA")));

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().snapshotDate()).isEqualTo(LocalDate.of(2026, 9, 30));
    }
}
