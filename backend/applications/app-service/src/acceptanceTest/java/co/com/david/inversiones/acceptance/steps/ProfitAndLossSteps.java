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
 * Step definitions for {@code profit_and_loss.feature}, the mandatory end-to-end scenario proving
 * the exchange-rate effect is reported separately from the asset gain, and that every monetary
 * amount survives the wire as a string.
 */
public class ProfitAndLossSteps {

    private final ApiClient api;
    private final ScenarioState state;
    private final WireMockSupport wireMock;

    public ProfitAndLossSteps(ApiClient api, ScenarioState state, WireMockSupport wireMock) {
        this.api = api;
        this.state = state;
        this.wireMock = wireMock;
    }

    @Given("the seeded portfolio holds QQQ at Hapi in dollars and TERPEL at Trii in pesos")
    public void theSeededPortfolioHoldsQqqAndTerpel() {
        var response = api.get("/portfolio", state.token());
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        List<String> tickers = JsonPath.read(response.getBody(), "$.positions[*].ticker");
        assertThat(tickers).contains("QQQ", "TERPEL");
        List<String> qqqAtHapi = JsonPath.read(response.getBody(),
                "$.positions[?(@.ticker=='QQQ' && @.brokerName=='Hapi')].brokerName");
        List<String> terpelAtTrii = JsonPath.read(response.getBody(),
                "$.positions[?(@.ticker=='TERPEL' && @.brokerName=='Trii')].brokerName");
        assertThat(qqqAtHapi).as("QQQ at Hapi: %s", response.getBody()).isNotEmpty();
        assertThat(terpelAtTrii).as("TERPEL at Trii: %s", response.getBody()).isNotEmpty();
    }

    @Given("the current exchange rate is {double} COP per USD")
    public void theCurrentExchangeRateIs(double rate) {
        wireMock.stubJson("/trm-hoy", "{\"trm\": " + rate + "}");
    }

    @Given("the TRM source is down")
    public void theTrmSourceIsDown() {
        wireMock.stubFailure("/trm-hoy");
        // The prior step already requested the portfolio, which may have populated the TRM cache
        // with a real rate; evict it so this scenario actually observes the failure.
        wireMock.evictTrmCache();
    }

    @When("the user requests the total return report")
    public void theUserRequestsTheTotalReturnReport() {
        state.lastResponse(api.get("/portfolio/returns", state.token()));
    }

    @Then("the asset gain and the exchange rate effect are reported as separate amounts")
    public void theAssetGainAndTheExchangeRateEffectAreReportedSeparately() {
        String body = state.lastResponse().getBody();
        Object assetGain = JsonPath.read(body, "$.assetGainInCop.amount");
        Object exchangeEffect = JsonPath.read(body, "$.exchangeEffect.amount");
        Object totalReturn = JsonPath.read(body, "$.totalReturnInCop.amount");
        assertThat(assetGain).as("assetGainInCop.amount must be present").isNotNull();
        assertThat(exchangeEffect).as("exchangeEffect.amount must be present").isNotNull();
        assertThat(totalReturn).as("totalReturnInCop.amount must be present").isNotNull();
    }

    @Then("the portfolio total is expressed in a single declared base currency")
    public void thePortfolioTotalIsExpressedInASingleDeclaredBaseCurrency() {
        String body = state.lastResponse().getBody();
        String totalCurrency = JsonPath.read(body, "$.totalReturnInCop.currency");
        String assetGainCurrency = JsonPath.read(body, "$.assetGainInCop.currency");
        String exchangeEffectCurrency = JsonPath.read(body, "$.exchangeEffect.currency");
        assertThat(totalCurrency).isEqualTo("COP");
        assertThat(assetGainCurrency).isEqualTo("COP");
        assertThat(exchangeEffectCurrency).isEqualTo("COP");
    }

    /**
     * The Critical-defect regression, end to end: before the fix, an unreachable TRM source made
     * the API fall back to the configured base TRM stamped with today's date, which made
     * {@code exchangeEffect} come out as an exact, confident zero. This asserts the opposite — the
     * field is absent from the JSON altogether, and {@code currencyEffectAvailable} says why, so a
     * client cannot mistake "unknown" for "no currency movement".
     */
    @Then("the exchange rate effect is reported as unavailable rather than zero")
    public void theExchangeRateEffectIsReportedAsUnavailableRatherThanZero() {
        String body = state.lastResponse().getBody();
        Boolean currencyEffectAvailable = JsonPath.read(body, "$.currencyEffectAvailable");
        assertThat(currencyEffectAvailable).as("currencyEffectAvailable: %s", body).isFalse();
        // null, not a zero amount: a present-but-zero exchangeEffect is exactly the defect this
        // scenario exists to catch, so the field must be JSON null (and current rate too).
        assertThat((Object) JsonPath.read(body, "$.exchangeEffect"))
                .as("exchangeEffect must be null, not a zero amount: %s", body).isNull();
        assertThat((Object) JsonPath.read(body, "$.currentRate"))
                .as("currentRate must be null when the TRM source is down: %s", body).isNull();
        assertThat((Object) JsonPath.read(body, "$.assetGainInCop"))
                .as("assetGainInCop must be null when the rate is unknown: %s", body).isNull();
        assertThat((Object) JsonPath.read(body, "$.totalReturnInCop"))
                .as("totalReturnInCop must be null when the rate is unknown: %s", body).isNull();
    }

    @Then("every monetary amount in the response is encoded as a JSON string")
    public void everyMonetaryAmountIsEncodedAsAJsonString() {
        List<Object> amounts = JsonPath.read(state.lastResponse().getBody(), "$..amount");
        assertThat(amounts).isNotEmpty();
        assertThat(amounts).allSatisfy(amount ->
                assertThat(amount).as("every 'amount' field must be a JSON string, not a number")
                        .isInstanceOf(String.class));
    }
}
