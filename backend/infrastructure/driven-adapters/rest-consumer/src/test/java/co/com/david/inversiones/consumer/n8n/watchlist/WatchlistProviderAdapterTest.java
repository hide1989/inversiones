package co.com.david.inversiones.consumer.n8n.watchlist;

import co.com.david.inversiones.consumer.n8n.N8nGateway;
import co.com.david.inversiones.consumer.support.Fixtures;
import co.com.david.inversiones.consumer.support.TestRestClients;
import co.com.david.inversiones.model.Ticker;
import co.com.david.inversiones.model.WatchlistItem;
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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Unit tests against the real {@code /watchlist} payload captured in {@code src/test/resources/n8n}. */
class WatchlistProviderAdapterTest {

    private MockWebServer mockWebServer;
    private WatchlistProviderAdapter adapter;

    @BeforeEach
    void setUp() throws IOException {
        mockWebServer = new MockWebServer();
        mockWebServer.start();
        RestClient restClient = TestRestClients.forMockServer(mockWebServer);
        adapter = new WatchlistProviderAdapter(new N8nGateway(restClient));
    }

    @AfterEach
    void tearDown() throws IOException {
        mockWebServer.shutdown();
    }

    @Test
    void mapsTheCapturedPayloadIncludingNullScoresForUnpricedRows() {
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(HttpStatus.OK.value())
                .addHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .setBody(Fixtures.load("watchlist")));

        List<WatchlistItem> items = adapter.fetchWatchlist();

        assertThat(items).hasSize(32);
        WatchlistItem spy = items.stream().filter(i -> i.ticker().equals(Ticker.of("SPY"))).findFirst().orElseThrow();
        assertThat(spy.price()).isNull();
        assertThat(spy.technicalScore()).isNull();
        assertThat(spy.wsbScore()).isEqualByComparingTo("9.5");
        assertThat(spy.combinedScore()).isEqualByComparingTo("9.5");
        assertThat(spy.signal()).isEqualTo("COMPRA");

        WatchlistItem nvda = items.stream().filter(i -> i.ticker().equals(Ticker.of("NVDA"))).findFirst().orElseThrow();
        assertThat(nvda.price()).isEqualByComparingTo("228.86");
        assertThat(nvda.technicalScore()).isEqualByComparingTo("8");
        assertThat(nvda.signal()).isEqualTo("FUERTE COMPRA");
    }

    @Test
    void aServerErrorDegradesToAnEmptyListRatherThanThrowing() {
        mockWebServer.enqueue(new MockResponse().setResponseCode(HttpStatus.INTERNAL_SERVER_ERROR.value()));

        List<WatchlistItem> items = adapter.fetchWatchlist();

        assertThat(items).isEmpty();
    }
}
