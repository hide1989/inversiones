package co.com.david.inversiones.acceptance.steps;

import co.com.david.inversiones.acceptance.support.ApiClient;
import co.com.david.inversiones.acceptance.support.ScenarioState;
import com.jayway.jsonpath.JsonPath;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Step definitions for {@code transaction_lifecycle.feature}: register, list, edit and delete a
 * trade, each step a real call to {@code POST}/{@code GET}/{@code PUT}/{@code DELETE}
 * {@code /api/v1/transactions}.
 */
public class TransactionLifecycleSteps {

    private final ApiClient api;
    private final ScenarioState state;

    public TransactionLifecycleSteps(ApiClient api, ScenarioState state) {
        this.api = api;
        this.state = state;
    }

    @When("the user registers a BUY of {string} shares of {string} at {string} USD with broker {string} traded on {string}")
    public void theUserRegistersABuy(String quantity, String ticker, String unitPrice, String broker, String tradedAt) {
        register(quantity, ticker, unitPrice, broker, tradedAt);
    }

    @Given("the user has registered a BUY of {string} shares of {string} at {string} USD with broker {string} traded on {string}")
    public void theUserHasRegisteredABuy(String quantity, String ticker, String unitPrice, String broker, String tradedAt) {
        register(quantity, ticker, unitPrice, broker, tradedAt);
        assertThat(state.lastResponse().getStatusCode().value())
                .as("setup registration must succeed: %s", state.lastResponse().getBody())
                .isEqualTo(201);
    }

    private void register(String quantity, String ticker, String unitPrice, String broker, String tradedAt) {
        String body = """
                {"ticker":"%s","brokerName":"%s","type":"BUY","quantity":"%s","unitPrice":"%s",\
                "currency":"USD","fees":"0","tradedAt":"%s"}""".formatted(ticker, broker, quantity, unitPrice, tradedAt);
        var response = api.post("/transactions", body, state.token());
        state.lastResponse(response);
        if (response.getStatusCode().value() == 201) {
            state.lastTransactionId(((Number) JsonPath.read(response.getBody(), "$.id")).longValue());
            state.lastTransactionTicker(ticker);
            state.lastTransactionBroker(broker);
        }
    }

    @When("the user lists transactions filtered by ticker {string}")
    public void theUserListsTransactionsFilteredByTicker(String ticker) {
        state.lastResponse(api.get("/transactions?ticker=" + ticker, state.token()));
    }

    @When("the user requests the portfolio")
    public void theUserRequestsThePortfolio() {
        state.lastResponse(api.get("/portfolio", state.token()));
    }

    @When("the user edits that transaction to quantity {string} at unit price {string} USD")
    public void theUserEditsThatTransaction(String quantity, String unitPrice) {
        String body = """
                {"ticker":"%s","brokerName":"%s","type":"BUY","quantity":"%s","unitPrice":"%s",\
                "currency":"USD","fees":"0","tradedAt":"2026-09-01"}"""
                .formatted(state.lastTransactionTicker(), state.lastTransactionBroker(), quantity, unitPrice);
        state.lastResponse(api.put("/transactions/" + state.lastTransactionId(), body, state.token()));
    }

    @When("the user deletes that transaction")
    public void theUserDeletesThatTransaction() {
        state.lastResponse(api.delete("/transactions/" + state.lastTransactionId(), state.token()));
    }

    @Then("the transaction response field {string} equals {string}")
    public void theTransactionResponseFieldEquals(String field, String expected) {
        String actual = JsonPath.read(state.lastResponse().getBody(), "$." + field).toString();
        // "quantity" (and any other decimal-string field) comes back at Money/Quantity's full
        // scale (e.g. "3.0000000000"), not the value as typed in the request; compare numerically
        // so this step works for both decimal fields and plain strings like "ticker".
        if (isDecimal(actual) && isDecimal(expected)) {
            assertThat(new BigDecimal(actual)).isEqualByComparingTo(new BigDecimal(expected));
        } else {
            assertThat(actual).isEqualTo(expected);
        }
    }

    private static boolean isDecimal(String value) {
        try {
            new BigDecimal(value);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    @Then("the transaction list contains the last registered transaction id")
    public void theTransactionListContainsTheLastRegisteredTransactionId() {
        List<Number> ids = JsonPath.read(state.lastResponse().getBody(), "$[*].id");
        assertThat(ids.stream().map(Number::longValue)).contains(state.lastTransactionId());
    }

    @Then("the transaction list does not contain the last registered transaction id")
    public void theTransactionListDoesNotContainTheLastRegisteredTransactionId() {
        List<Number> ids = JsonPath.read(state.lastResponse().getBody(), "$[*].id");
        assertThat(ids.stream().map(Number::longValue)).doesNotContain(state.lastTransactionId());
    }

    @Then("the portfolio contains a position for ticker {string}")
    public void thePortfolioContainsAPositionForTicker(String ticker) {
        List<String> tickers = JsonPath.read(state.lastResponse().getBody(), "$.positions[*].ticker");
        assertThat(tickers).contains(ticker);
    }
}
