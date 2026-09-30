package co.com.david.inversiones.consumer.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

@Configuration
public class RestConsumerConfig {

    private final String url;

    public RestConsumerConfig(@Value("${adapter.restconsumer.url}") String url) {
        this.url = url;
    }

    // Marked @Primary so the sample RestConsumer (and any other unqualified RestClient injection
    // point outside this module's own n8n adapters) keeps resolving to this bean now that
    // N8nClientConfig also defines a RestClient; the n8n gateway always disambiguates explicitly
    // with @Qualifier("n8nRestClient").
    @Bean
    @Primary
    public RestClient getRestClient(RestClient.Builder builder) {
        return builder
                .baseUrl(url)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

}
