package co.com.david.inversiones.acceptance.steps;

import co.com.david.inversiones.acceptance.support.ApiClient;
import co.com.david.inversiones.acceptance.support.ScenarioState;
import co.com.david.inversiones.acceptance.support.WireMockSupport;
import com.jayway.jsonpath.JsonPath;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Step definitions for {@code degradation.feature}: every n8n endpoint is stubbed to fail, and the
 * API must still answer with 200s, degrading to honestly-reported absent data rather than
 * returning a 500.
 */
public class DegradationSteps {

    private final ApiClient api;
    private final ScenarioState state;
    private final WireMockSupport wireMock;

    public DegradationSteps(ApiClient api, ScenarioState state, WireMockSupport wireMock) {
        this.api = api;
        this.state = state;
        this.wireMock = wireMock;
    }

    @Given("every n8n endpoint is down")
    public void everyN8nEndpointIsDown() {
        wireMock.stubEveryEndpointFailing();
    }

    @When("the user requests the earnings calendar")
    public void theUserRequestsTheEarningsCalendar() {
        state.lastResponse(api.get("/earnings", state.token()));
    }

    @When("the user requests the watchlist")
    public void theUserRequestsTheWatchlist() {
        state.lastResponse(api.get("/watchlist", state.token()));
    }

    @Then("every position is reported without a live market price rather than the request failing")
    public void everyPositionIsReportedWithoutALiveMarketPrice() {
        String body = state.lastResponse().getBody();
        List<Boolean> pricedFlags = JsonPath.read(body, "$.positions[*].priced");
        assertThat(pricedFlags).as("portfolio positions: %s", body).isNotEmpty().allMatch(priced -> !priced);
        List<String> unpriced = JsonPath.read(body, "$.unpricedTickers");
        assertThat(unpriced).isNotEmpty();
    }

    @Then("the earnings calendar is empty rather than the request failing")
    public void theEarningsCalendarIsEmpty() {
        List<Object> events = JsonPath.read(state.lastResponse().getBody(), "$[*]");
        assertThat(events).isEmpty();
    }

    @Then("the watchlist is empty rather than the request failing")
    public void theWatchlistIsEmpty() {
        List<Object> items = JsonPath.read(state.lastResponse().getBody(), "$[*]");
        assertThat(items).isEmpty();
    }
}
