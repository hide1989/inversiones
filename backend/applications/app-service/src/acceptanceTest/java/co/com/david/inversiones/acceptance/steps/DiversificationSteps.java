package co.com.david.inversiones.acceptance.steps;

import co.com.david.inversiones.acceptance.support.ApiClient;
import co.com.david.inversiones.acceptance.support.ScenarioState;
import co.com.david.inversiones.acceptance.support.WireMockSupport;
import com.jayway.jsonpath.JsonPath;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Step definitions for {@code diversification.feature}.
 *
 * <p>The "Given" step stubs {@code /precios-hoy} to answer with a price for QQQ alone, several
 * orders of magnitude above its real one. Every other USD position then has no quote in that
 * response, so {@code GetPortfolioUseCase} carries them at cost (see its Javadoc) while QQQ is
 * valued at the inflated price, deterministically pushing QQQ's weight over the 25% single-fund
 * ceiling without touching the seeded transaction data.
 */
public class DiversificationSteps {

    private final ApiClient api;
    private final ScenarioState state;
    private final WireMockSupport wireMock;

    public DiversificationSteps(ApiClient api, ScenarioState state, WireMockSupport wireMock) {
        this.api = api;
        this.state = state;
        this.wireMock = wireMock;
    }

    @Given("QQQ's current price makes it dominate the portfolio's market value")
    public void qqqsCurrentPriceMakesItDominateThePortfolio() {
        wireMock.stubJson("/precios-hoy", """
                {"datos":{"QQQ":{"precio_actual": 5000000}}}""");
    }

    @When("the user requests the diversification report")
    public void theUserRequestsTheDiversificationReport() {
        state.lastResponse(api.get("/diversification", state.token()));
    }

    @Then("a diversification violation is reported for subject {string} with its actual weight above the allowed weight")
    public void aDiversificationViolationIsReportedForSubject(String subject) {
        String body = state.lastResponse().getBody();
        List<String> subjects = JsonPath.read(body, "$.violations[*].subject");
        assertThat(subjects).as("violations were: %s", body).contains(subject);

        List<String> actualWeights = JsonPath.read(body, "$.violations[?(@.subject=='" + subject + "')].actualWeight");
        List<String> allowedWeights = JsonPath.read(body, "$.violations[?(@.subject=='" + subject + "')].allowedWeight");
        assertThat(actualWeights).isNotEmpty();
        assertThat(allowedWeights).isNotEmpty();
        String actualWeight = actualWeights.get(0);
        String allowedWeight = allowedWeights.get(0);
        assertThat(actualWeight).isInstanceOf(String.class);
        assertThat(allowedWeight).isInstanceOf(String.class);
        assertThat(new BigDecimal(actualWeight)).isGreaterThan(new BigDecimal(allowedWeight));
    }
}
