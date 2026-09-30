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

    public N8nClientConfig(
            @Value("${inversiones.n8n.base-url}") String baseUrl,
            @Value("${inversiones.n8n.connect-timeout}") Duration connectTimeout,
            @Value("${inversiones.n8n.read-timeout}") Duration readTimeout) {
        this.baseUrl = baseUrl;
        this.connectTimeout = connectTimeout;
        this.readTimeout = readTimeout;
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
        HttpClientSettings settings = HttpClientSettings.defaults()
                .withConnectTimeout(connectTimeout)
                .withReadTimeout(readTimeout);
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
