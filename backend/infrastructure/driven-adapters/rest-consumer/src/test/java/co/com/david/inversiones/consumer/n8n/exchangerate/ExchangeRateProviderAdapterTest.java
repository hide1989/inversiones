package co.com.david.inversiones.consumer.n8n.exchangerate;

import co.com.david.inversiones.consumer.n8n.N8nGateway;
import co.com.david.inversiones.consumer.support.Fixtures;
import co.com.david.inversiones.consumer.support.TestRestClients;
import co.com.david.inversiones.model.ExchangeRate;
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
import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Unit tests against the real {@code /trm-hoy} payload captured in {@code src/test/resources/n8n}.
 *
 * <p>Instantiates {@link N8nGateway} and {@link ExchangeRateCacheSource} directly, without a Spring
 * context, so there is no AOP proxy in front of them — see {@code N8nGateway}'s Javadoc for why the
 * classes are still safe to call this way.
 */
@ExtendWith(MockitoExtension.class)
class ExchangeRateProviderAdapterTest {

    private MockWebServer mockWebServer;
    private ExchangeRateProviderAdapter adapter;

    @Mock
    private TimeProvider timeProvider;

    @BeforeEach
    void setUp() throws IOException {
        mockWebServer = new MockWebServer();
        mockWebServer.start();
        RestClient restClient = TestRestClients.forMockServer(mockWebServer);
        N8nGateway gateway = new N8nGateway(restClient);
        ExchangeRateCacheSource cacheSource =
                new ExchangeRateCacheSource(gateway, timeProvider, new BigDecimal("3568.42"));
        adapter = new ExchangeRateProviderAdapter(cacheSource);
    }

    @AfterEach
    void tearDown() throws IOException {
        mockWebServer.shutdown();
    }

    @Test
    void mapsTheCapturedTrmHoyPayloadToTheRightRateAndDate() {
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(HttpStatus.OK.value())
                .addHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .setBody(Fixtures.load("trm-hoy")));

        ExchangeRate rate = adapter.currentCopPerUsd();

        assertThat(rate.rate()).isEqualByComparingTo("3366.901593");
        assertThat(rate.rateDate()).isEqualTo(LocalDate.of(2026, 9, 30));
    }

    @Test
    void fallsBackToTheConfiguredBaseTrmWhenTrmHoyIsUnreachable() {
        mockWebServer.enqueue(new MockResponse().setResponseCode(HttpStatus.INTERNAL_SERVER_ERROR.value()));
        mockWebServer.enqueue(new MockResponse().setResponseCode(HttpStatus.INTERNAL_SERVER_ERROR.value()));
        when(timeProvider.today()).thenReturn(LocalDate.of(2026, 9, 30));

        ExchangeRate rate = adapter.currentCopPerUsd();

        assertThat(rate.rate()).isEqualByComparingTo("3568.42");
        assertThat(rate.rateDate()).isEqualTo(LocalDate.of(2026, 9, 30));
    }
}
