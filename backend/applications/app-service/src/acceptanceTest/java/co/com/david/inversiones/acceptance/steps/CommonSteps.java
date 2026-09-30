package co.com.david.inversiones.acceptance.steps;

import co.com.david.inversiones.acceptance.support.ApiClient;
import co.com.david.inversiones.acceptance.support.ScenarioState;
import com.jayway.jsonpath.JsonPath;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Steps shared by every feature file: logging in, and generic assertions against the most
 * recently received HTTP response.
 */
public class CommonSteps {

    static final String SEEDED_USERNAME = "david";
    static final String SEEDED_PASSWORD = "Inversiones2026!";

    private final ApiClient api;
    private final ScenarioState state;

    public CommonSteps(ApiClient api, ScenarioState state) {
        this.api = api;
        this.state = state;
    }

    @Given("the user is authenticated")
    public void theUserIsAuthenticated() {
        ResponseEntity<String> response = api.post("/auth/login",
                loginBody(SEEDED_USERNAME, SEEDED_PASSWORD), null);
        state.lastResponse(response);
        assertThat(response.getStatusCode().value())
                .as("login with the seeded credentials must succeed: %s", response.getBody())
                .isEqualTo(200);
        state.token(JsonPath.read(response.getBody(), "$.token"));
    }

    @Then("the response has status {int}")
    public void theResponseHasStatus(int expectedStatus) {
        assertThat(state.lastResponse().getStatusCode().value())
                .as("response body was: %s", state.lastResponse().getBody())
                .isEqualTo(expectedStatus);
    }

    static String loginBody(String username, String password) {
        return """
                {"username":"%s","password":"%s"}""".formatted(username, password);
    }
}
