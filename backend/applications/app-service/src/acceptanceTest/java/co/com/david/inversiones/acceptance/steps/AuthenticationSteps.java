package co.com.david.inversiones.acceptance.steps;

import co.com.david.inversiones.acceptance.support.ApiClient;
import co.com.david.inversiones.acceptance.support.ScenarioState;
import com.jayway.jsonpath.JsonPath;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

import static co.com.david.inversiones.acceptance.steps.CommonSteps.loginBody;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Step definitions for {@code authentication.feature}. Every call goes through {@link ApiClient}
 * to {@code POST /api/v1/auth/login} and {@code GET /api/v1/portfolio}, the real HTTP endpoints,
 * never a use case invoked directly.
 */
public class AuthenticationSteps {

    private final ApiClient api;
    private final ScenarioState state;

    public AuthenticationSteps(ApiClient api, ScenarioState state) {
        this.api = api;
        this.state = state;
    }

    @When("the user logs in with username {string} and password {string}")
    public void theUserLogsInWith(String username, String password) {
        state.lastResponse(api.post("/auth/login", loginBody(username, password), null));
    }

    @Then("the response contains a non-blank bearer token")
    public void theResponseContainsANonBlankBearerToken() {
        String token = JsonPath.read(state.lastResponse().getBody(), "$.token");
        assertThat(token).isNotBlank();
        state.token(token);
    }

    @When("the user requests the portfolio using the issued token")
    public void theUserRequestsThePortfolioUsingTheIssuedToken() {
        state.lastResponse(api.get("/portfolio", state.token()));
    }

    @When("the user requests the portfolio without a token")
    public void theUserRequestsThePortfolioWithoutAToken() {
        state.lastResponse(api.get("/portfolio", null));
    }
}
