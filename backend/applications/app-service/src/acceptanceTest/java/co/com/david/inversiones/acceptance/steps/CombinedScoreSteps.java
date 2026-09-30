package co.com.david.inversiones.acceptance.steps;

import co.com.david.inversiones.acceptance.support.ApiClient;
import co.com.david.inversiones.acceptance.support.ScenarioState;
import co.com.david.inversiones.acceptance.support.WireMockSupport;
import com.jayway.jsonpath.JsonPath;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

import java.math.BigDecimal;
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

    @Given("technical, news and analyst signals are available for TSLA with scores {double}, {double} and {double}")
    public void technicalNewsAndAnalystSignalsAreAvailableForTsla(double technical, double news, double analyst) {
        // These three stay on the happy-path fixtures GlobalHooks already stubbed (from
        // indicadores-tecnicos.json, news-sentiment.json and ratings-analistas.json); nothing to
        // stub here. The three numbers in the step text are exactly what those fixtures carry for
        // TSLA — asserted here so a fixture edit that silently changes them breaks this scenario
        // loudly, instead of leaving "3.79" in the Then step quietly wrong.
        assertThat(technical).isEqualTo(4.5);
        assertThat(news).isEqualTo(4.6);
        assertThat(analyst).isEqualTo(1.27);
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

    /**
     * Pins the exact numeric outcome of proportional redistribution, not merely that one
     * unfavourable level was avoided: {@code sum(score x weight) / sum(weight)} over the three
     * surviving sources — TECHNICAL 4.5 x 0.30, NEWS 4.6 x 0.20, ANALYST 1.27 x 0.15, divided by
     * 0.30 + 0.20 + 0.15 = 0.65 — is 2.4605 / 0.65 = 3.7853..., which {@link
     * co.com.david.inversiones.model.CombinedScore#from} rounds HALF_UP to scale 2: "3.79". That
     * number is reachable only if the missing sources' weight was redistributed across the three
     * that answered; treating them as zero-score instead would use the full 1.00 denominator and
     * yield 2.46 (a STRONG_SELL) rather than 3.79 (a SELL) — so an exact match here is proof
     * redistribution happened, where merely excluding STRONG_SELL would not be.
     */
    @Then("the combined score for {string} equals {string}, the value proportional redistribution of the three surviving sources' weights produces")
    public void theCombinedScoreEqualsTheRedistributedValue(String ticker, String expectedScore) {
        List<String> values = JsonPath.read(state.lastResponse().getBody(),
                "$.signals[?(@.ticker=='" + ticker + "')].score.value");
        assertThat(values).isNotEmpty();
        assertThat(new BigDecimal(values.get(0))).isEqualByComparingTo(new BigDecimal(expectedScore));
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
