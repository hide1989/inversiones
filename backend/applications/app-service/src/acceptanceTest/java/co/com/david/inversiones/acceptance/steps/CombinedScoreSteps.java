package co.com.david.inversiones.acceptance.steps;

import co.com.david.inversiones.acceptance.support.ApiClient;
import co.com.david.inversiones.acceptance.support.ScenarioState;
import co.com.david.inversiones.acceptance.support.WireMockSupport;
import com.jayway.jsonpath.JsonPath;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

import java.time.Duration;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Step definitions for {@code combined_score.feature}.
 *
 * <p>{@code GetSignalsDashboardUseCase} reads from the persisted {@code signal_snapshots} table,
 * populated only by the real, production {@code RefreshSignalSnapshotUseCase} running on its
 * schedule (sped up to every two seconds for this suite — see
 * {@link co.com.david.inversiones.acceptance.support.CucumberSpringConfiguration}). The "When"
 * step therefore polls {@code GET /api/v1/signals} until that scheduled run has caught up with the
 * WireMock stubs the "Given" steps just set, rather than invoking the use case directly.
 */
public class CombinedScoreSteps {

    private static final Duration POLL_TIMEOUT = Duration.ofSeconds(30);
    private static final Duration POLL_INTERVAL = Duration.ofMillis(500);

    private final ApiClient api;
    private final ScenarioState state;
    private final WireMockSupport wireMock;

    public CombinedScoreSteps(ApiClient api, ScenarioState state, WireMockSupport wireMock) {
        this.api = api;
        this.state = state;
        this.wireMock = wireMock;
    }

    @Given("technical, news and analyst signals are available for TSLA")
    public void technicalNewsAndAnalystSignalsAreAvailableForTsla() {
        // These three stay on the happy-path fixtures GlobalHooks already stubbed; nothing to do.
    }

    @Given("the insider, social and WSB sources are unavailable")
    public void theInsiderSocialAndWsbSourcesAreUnavailable() {
        wireMock.stubFailure("/insider-trading");
        wireMock.stubFailure("/stocktwits");
        wireMock.stubFailure("/reddit");
    }

    @When("the user requests the signals dashboard")
    public void theUserRequestsTheSignalsDashboard() {
        long deadline = System.currentTimeMillis() + POLL_TIMEOUT.toMillis();
        String lastBody = null;
        while (System.currentTimeMillis() < deadline) {
            var response = api.get("/signals", state.token());
            state.lastResponse(response);
            lastBody = response.getBody();
            List<String> usedSources = usedSourcesFor(lastBody, "TSLA");
            if (usedSources != null && usedSources.size() == 3) {
                return;
            }
            sleep(POLL_INTERVAL);
        }
        throw new AssertionError("TSLA's signals never settled on exactly 3 sources within "
                + POLL_TIMEOUT + "; last dashboard was: " + lastBody);
    }

    @Then("the combined score for {string} is computed from exactly the sources {string}")
    public void theCombinedScoreIsComputedFromExactlyTheSources(String ticker, String expectedSourcesCsv) {
        List<String> usedSources = usedSourcesFor(state.lastResponse().getBody(), ticker);
        Set<String> expected = Set.of(expectedSourcesCsv.split(","));
        assertThat(usedSources).isNotNull();
        assertThat(Set.copyOf(usedSources)).isEqualTo(expected);
    }

    @Then("{string} is not reported as a sell purely because data was missing")
    public void tickerIsNotReportedAsASellPurelyBecauseDataWasMissing(String ticker) {
        List<String> levels = JsonPath.read(state.lastResponse().getBody(),
                "$.signals[?(@.ticker=='" + ticker + "')].score.level");
        assertThat(levels).isNotEmpty();
        String level = levels.get(0);
        // Treating a missing source's weight as a zero score (instead of redistributing it across
        // the sources that actually answered) would push this ticker's combined score down into
        // STRONG_SELL territory; redistribution keeps it no worse than what its available data
        // actually supports.
        assertThat(level).isNotEqualTo("STRONG_SELL");
    }

    @SuppressWarnings("unchecked")
    private static List<String> usedSourcesFor(String dashboardJson, String ticker) {
        List<List<String>> matches = JsonPath.read(dashboardJson,
                "$.signals[?(@.ticker=='" + ticker + "')].score.usedSources");
        return matches.isEmpty() ? null : matches.get(0);
    }

    private static void sleep(Duration duration) {
        try {
            Thread.sleep(duration.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
