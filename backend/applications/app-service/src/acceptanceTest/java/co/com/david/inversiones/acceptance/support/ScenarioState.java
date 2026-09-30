package co.com.david.inversiones.acceptance.support;

import io.cucumber.spring.ScenarioScope;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

/**
 * Mutable state shared across every step-definition class within one scenario: the bearer token
 * issued by the last login, the most recent HTTP response, and the id of the last transaction a
 * step registered.
 *
 * <p>{@link ScenarioScope} (from {@code cucumber-spring}) gives this bean a fresh instance per
 * scenario, so state from one scenario can never leak into the next even though the surrounding
 * Spring context is reused for the whole suite.
 */
@Component
@ScenarioScope
public class ScenarioState {

    private String token;
    private ResponseEntity<String> lastResponse;
    private Long lastTransactionId;
    private String lastTransactionTicker;
    private String lastTransactionBroker;

    public String token() {
        return token;
    }

    public void token(String token) {
        this.token = token;
    }

    public ResponseEntity<String> lastResponse() {
        return lastResponse;
    }

    public void lastResponse(ResponseEntity<String> lastResponse) {
        this.lastResponse = lastResponse;
    }

    public Long lastTransactionId() {
        return lastTransactionId;
    }

    public void lastTransactionId(Long lastTransactionId) {
        this.lastTransactionId = lastTransactionId;
    }

    public String lastTransactionTicker() {
        return lastTransactionTicker;
    }

    public void lastTransactionTicker(String lastTransactionTicker) {
        this.lastTransactionTicker = lastTransactionTicker;
    }

    public String lastTransactionBroker() {
        return lastTransactionBroker;
    }

    public void lastTransactionBroker(String lastTransactionBroker) {
        this.lastTransactionBroker = lastTransactionBroker;
    }
}
