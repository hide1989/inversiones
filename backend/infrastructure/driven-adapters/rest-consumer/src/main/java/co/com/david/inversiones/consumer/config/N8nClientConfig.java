package co.com.david.inversiones.consumer.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.HttpClientSettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.List;

/**
 * Wiring for the n8n market-data workflows this module consumes: {@code QuoteProvider},
 * {@code ExchangeRateProvider} and {@code SignalProvider}.
 *
 * <p>This class lives under {@code config} and is excluded from the coverage gate by convention
 * (see {@code main.gradle}), which is why it holds framework wiring rather than any mapping logic.
 */
@Configuration
public class N8nClientConfig {

    private final String baseUrl;
    private final Duration connectTimeout;
    private final Duration readTimeout;
    private final Duration slowReadTimeout;

    public N8nClientConfig(
            @Value("${inversiones.n8n.base-url}") String baseUrl,
            @Value("${inversiones.n8n.connect-timeout}") Duration connectTimeout,
            @Value("${inversiones.n8n.read-timeout}") Duration readTimeout,
            @Value("${inversiones.n8n.slow-read-timeout:90s}") Duration slowReadTimeout) {
        this.baseUrl = baseUrl;
        this.connectTimeout = connectTimeout;
        this.readTimeout = readTimeout;
        this.slowReadTimeout = slowReadTimeout;
    }

    /**
     * The Jackson mapper used to deserialize every n8n payload.
     *
     * <p>These workflows are edited independently of this codebase, so
     * {@link DeserializationFeature#FAIL_ON_UNKNOWN_PROPERTIES} is disabled: an upstream field this
     * adapter does not yet know about must not break deserialization. Floating-point JSON numbers
     * are read into {@link java.math.BigDecimal} rather than {@code double}
     * ({@link DeserializationFeature#USE_BIG_DECIMAL_FOR_FLOATS}), because a price or a rate parsed
     * through {@code double} first can lose precision before it ever reaches domain code.
     */
    @Bean
    public ObjectMapper n8nObjectMapper() {
        return JsonMapper.builder()
                .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
                .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .findAndAddModules()
                .build();
    }

    /**
     * The {@link RestClient} the n8n gateway issues every call through.
     *
     * <p>A single client is shared by every endpoint group; per-endpoint behaviour (circuit
     * breaking, retrying, caching) is layered on top in the gateway and provider adapters, not
     * here.
     */
    @Bean
    public RestClient n8nRestClient(RestClient.Builder builder, ObjectMapper n8nObjectMapper) {
        return buildClient(builder, n8nObjectMapper, readTimeout);
    }

    /**
     * A second {@link RestClient}, identical to {@link #n8nRestClient} except for its read timeout,
     * used only for {@code /sintesis-ia}.
     *
     * <p>That endpoint's upstream work (WF-17, an LLM call per ticker) takes roughly 55 seconds on a
     * cold n8n cache, but is normally served from n8n's own cache in about a second. The shared
     * {@link #n8nRestClient}'s {@code read-timeout} (30s) exists to fail fast on the other eight
     * endpoints, which have no reason to ever take that long; applying it here as well would abort a
     * slow-but-successful cold call and report it as "no data" — exactly the kind of false absence
     * the {@code sin_datos} handling elsewhere in this module exists to avoid. Spring's
     * {@code RestClient} has no per-request timeout override, so a dedicated client with its own
     * request factory is the minimal way to give one endpoint a different timeout without loosening
     * it for the rest.
     */
    @Bean
    public RestClient n8nSlowRestClient(RestClient.Builder builder, ObjectMapper n8nObjectMapper) {
        return buildClient(builder, n8nObjectMapper, slowReadTimeout);
    }

    private RestClient buildClient(RestClient.Builder builder, ObjectMapper n8nObjectMapper, Duration effectiveReadTimeout) {
        HttpClientSettings settings = HttpClientSettings.defaults()
                .withConnectTimeout(connectTimeout)
                .withReadTimeout(effectiveReadTimeout);
        var requestFactory = ClientHttpRequestFactoryBuilder.detect().build(settings);

        MappingJackson2HttpMessageConverter jacksonConverter =
                new MappingJackson2HttpMessageConverter(n8nObjectMapper);
        List<HttpMessageConverter<?>> converters = List.of(jacksonConverter);

        return builder
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .messageConverters(converters)
                .build();
    }
}
