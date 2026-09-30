package co.com.david.inversiones.acceptance.support;

import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

/**
 * Drives the real, running application over actual HTTP, through {@link TestRestTemplate} against
 * the random port {@code @SpringBootTest} bound it to.
 *
 * <p>This is the only path every step definition in this suite uses to reach the API: no step
 * definition in this package calls a use case, a repository or an adapter directly. Going through
 * real HTTP — rather than {@code MockMvc}, which bypasses the servlet container and parts of the
 * filter chain — is what makes this suite able to catch a broken controller, a broken security
 * filter chain, a broken serialization step or a broken error mapping, none of which a unit test
 * wired straight to a use case could ever observe.
 */
@Component
public class ApiClient {

    private final TestRestTemplate restTemplate;

    public ApiClient(TestRestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public ResponseEntity<String> get(String path, String token) {
        return exchange(HttpMethod.GET, path, null, token);
    }

    public ResponseEntity<String> post(String path, String jsonBody, String token) {
        return exchange(HttpMethod.POST, path, jsonBody, token);
    }

    public ResponseEntity<String> put(String path, String jsonBody, String token) {
        return exchange(HttpMethod.PUT, path, jsonBody, token);
    }

    public ResponseEntity<String> delete(String path, String token) {
        return exchange(HttpMethod.DELETE, path, null, token);
    }

    private ResponseEntity<String> exchange(HttpMethod method, String path, String jsonBody, String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (token != null) {
            headers.setBearerAuth(token);
        }
        HttpEntity<String> entity = new HttpEntity<>(jsonBody, headers);
        // A relative path, not an absolute URL: @AutoConfigureTestRestTemplate equips this bean
        // with a root URI handler that resolves "local.server.port" lazily, per request, against
        // the embedded server's actual port. Resolving the port ourselves at construction time
        // would race the servlet container, which has not necessarily published that property yet
        // when this singleton bean is created during context refresh.
        return restTemplate.exchange("/api/v1" + path, method, entity, String.class);
    }
}
