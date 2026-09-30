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

    @Then("every monetary amount in the response is encoded as a JSON string")
    public void everyMonetaryAmountIsEncodedAsAJsonString() {
        List<Object> amounts = JsonPath.read(state.lastResponse().getBody(), "$..amount");
        assertThat(amounts).isNotEmpty();
        assertThat(amounts).allSatisfy(amount ->
                assertThat(amount).as("every 'amount' field must be a JSON string, not a number")
                        .isInstanceOf(String.class));
    }
}
