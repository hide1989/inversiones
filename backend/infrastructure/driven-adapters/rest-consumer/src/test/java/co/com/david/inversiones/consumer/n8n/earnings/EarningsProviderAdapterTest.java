package co.com.david.inversiones.consumer.n8n.earnings;

import co.com.david.inversiones.consumer.n8n.N8nGateway;
import co.com.david.inversiones.consumer.support.Fixtures;
import co.com.david.inversiones.consumer.support.TestRestClients;
import co.com.david.inversiones.model.EarningsEvent;
import co.com.david.inversiones.model.Ticker;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests against a hand-built {@code /earnings} payload, following the exact field shape read
 * from the WF-13 workflow source ({@code n8n-workflows/WF-13_Earnings_Calendar.json}'s "Calcular
 * Earnings" / "Consolidar" nodes). The live capture on 2026-09-30 returned HTTP 200 with a fully
 * empty body, so a real payload could not be captured for the happy path; the actual empty-body
 * case is what {@link #anEmptyUpstreamBodyDegradesToAnEmptyListRatherThanThrowing()} exercises,
 * unmodified from what n8n returned.
 */
class EarningsProviderAdapterTest {

    private MockWebServer mockWebServer;
    private EarningsProviderAdapter adapter;

    @BeforeEach
    void setUp() throws IOException {
        mockWebServer = new MockWebServer();
        mockWebServer.start();
        RestClient restClient = TestRestClients.forMockServer(mockWebServer);
        adapter = new EarningsProviderAdapter(new N8nGateway(restClient));
    }

    @AfterEach
    void tearDown() throws IOException {
        mockWebServer.shutdown();
    }

    @Test
    void mapsTheSampleEarningsPayloadSkippingEntriesWithNoReportDate() {
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(HttpStatus.OK.value())
                .addHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .setBody(Fixtures.load("earnings-sample")));

        List<EarningsEvent> events = adapter.fetchEvents(
                Set.of(Ticker.of("TSLA"), Ticker.of("MSFT"), Ticker.of("NU"), Ticker.of("GOOGL")));

        // NU has no proximo_reporte in the fixture and must be skipped, not represented with a null date.
        assertThat(events).extracting(e -> e.ticker().symbol())
                .containsExactlyInAnyOrder("TSLA", "MSFT", "GOOGL");
        EarningsEvent tsla = events.stream().filter(e -> e.ticker().equals(Ticker.of("TSLA"))).findFirst().orElseThrow();
        assertThat(tsla.reportDate()).isEqualTo(LocalDate.of(2026, 10, 12));
        assertThat(tsla.estimatedEps()).isEqualByComparingTo("1.23");
        assertThat(tsla.historicalSurprise()).isEqualByComparingTo("15.8");
    }

    @Test
    void filtersToOnlyTheRequestedTickers() {
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(HttpStatus.OK.value())
                .addHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .setBody(Fixtures.load("earnings-sample")));

        List<EarningsEvent> events = adapter.fetchEvents(Set.of(Ticker.of("TSLA")));

        assertThat(events).extracting(e -> e.ticker().symbol()).containsExactly("TSLA");
    }

    @Test
    void anEmptyUpstreamBodyDegradesToAnEmptyListRatherThanThrowing() {
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(HttpStatus.OK.value())
                .addHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .setBody(Fixtures.load("earnings")));

        List<EarningsEvent> events = adapter.fetchEvents(Set.of(Ticker.of("TSLA")));

        assertThat(events).isEmpty();
    }

    @Test
    void aServerErrorDegradesToAnEmptyListRatherThanThrowing() {
        mockWebServer.enqueue(new MockResponse().setResponseCode(HttpStatus.INTERNAL_SERVER_ERROR.value()));

        List<EarningsEvent> events = adapter.fetchEvents(Set.of(Ticker.of("TSLA")));

        assertThat(events).isEmpty();
    }
}
