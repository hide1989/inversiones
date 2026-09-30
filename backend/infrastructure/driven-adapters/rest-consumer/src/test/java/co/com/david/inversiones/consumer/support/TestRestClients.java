package co.com.david.inversiones.consumer.support;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.json.JsonMapper;
import okhttp3.mockwebserver.MockWebServer;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.List;

/**
 * Builds a {@link RestClient} configured exactly like {@code N8nClientConfig} does in production —
 * snake_case property mapping, unknown fields ignored, floating-point JSON numbers read as
 * {@link java.math.BigDecimal} — but pointed at a local {@link MockWebServer} instead of the real
 * n8n host, for tests that instantiate the adapters directly without a Spring context.
 */
public final class TestRestClients {

    private TestRestClients() {
    }

    public static ObjectMapper n8nObjectMapper() {
        return JsonMapper.builder()
                .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
                .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .findAndAddModules()
                .build();
    }

    public static RestClient forMockServer(MockWebServer server) {
        return forMockServer(server, Duration.ofSeconds(5));
    }

    /** As {@link #forMockServer(MockWebServer)}, but with an explicit read timeout — for tests that
     * simulate a slow upstream and need the client to time out quickly rather than waiting. */
    public static RestClient forMockServer(MockWebServer server, Duration readTimeout) {
        MappingJackson2HttpMessageConverter converter =
                new MappingJackson2HttpMessageConverter(n8nObjectMapper());
        List<HttpMessageConverter<?>> converters = List.of(converter);

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setReadTimeout(readTimeout);

        return RestClient.builder()
                .baseUrl(server.url("/").toString())
                .requestFactory(requestFactory)
                .messageConverters(converters)
                .build();
    }
}
