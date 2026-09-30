package co.com.david.inversiones.consumer.n8n;

import co.com.david.inversiones.consumer.support.Fixtures;
import co.com.david.inversiones.consumer.support.TestRestClients;
import co.com.david.inversiones.consumer.n8n.dto.SintesisIaResponseDto;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Proves that {@code /sintesis-ia} really is backed by a different, longer-timeout {@link
 * RestClient} than every other n8n endpoint — not merely that a {@code slow-read-timeout} property
 * exists.
 *
 * <p>Both clients here point at the same {@link MockWebServer} response, delayed longer than the
 * fast client's read timeout but shorter than the slow client's. If the two {@code N8nGateway}
 * constructor parameters were ever accidentally wired to the same underlying client (for example, a
 * regression that drops the {@code @Qualifier}), this test fails: either both calls would time out,
 * or neither would.
 */
class N8nGatewayTimeoutTest {

    private static final Duration FAST_TIMEOUT = Duration.ofSeconds(1);
    private static final Duration SLOW_TIMEOUT = Duration.ofSeconds(3);
    private static final long DELAY_SECONDS = 2;

    private MockWebServer mockWebServer;
    private N8nGateway gateway;

    @BeforeEach
    void setUp() throws IOException {
        mockWebServer = new MockWebServer();
        mockWebServer.start();

        RestClient fastClient = TestRestClients.forMockServer(mockWebServer, FAST_TIMEOUT);
        RestClient slowClient = TestRestClients.forMockServer(mockWebServer, SLOW_TIMEOUT);
        gateway = new N8nGateway(fastClient, slowClient);
    }

    @AfterEach
    void tearDown() throws IOException {
        mockWebServer.shutdown();
    }

    private static MockResponse delayedFixture(String name) {
        return new MockResponse()
                .setHeadersDelay(DELAY_SECONDS, TimeUnit.SECONDS)
                .setResponseCode(HttpStatus.OK.value())
                .addHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .setBody(Fixtures.load(name));
    }

    @Test
    void sintesisIaSucceedsThroughTheSlowClientAtADelayThatWouldTimeOutOnTheFastClient() {
        mockWebServer.enqueue(delayedFixture("sintesis-ia-mixed"));

        SintesisIaResponseDto response = gateway.fetchSintesisIa();

        assertThat(response).isNotNull();
        assertThat(response.sintesis()).isNotEmpty();
    }

    @Test
    void aFastEndpointWithTheSameDelayTimesOutRatherThanWaitingForTheSlowTimeout() {
        mockWebServer.enqueue(delayedFixture("watchlist"));

        assertThatThrownBy(() -> gateway.fetchWatchlist())
                .isInstanceOf(ResourceAccessException.class)
                .hasMessageContaining("Read timed out");
    }
}
