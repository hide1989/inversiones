package co.com.david.inversiones.acceptance.steps;

import co.com.david.inversiones.acceptance.support.WireMockSupport;
import io.cucumber.java.Before;

/**
 * Runs before every scenario: restores every n8n endpoint to its real fixture payload and evicts
 * the quote/TRM caches, so a scenario always observes WireMock's current stubs rather than a
 * cached value left over from a previous scenario.
 */
public class GlobalHooks {

    private final WireMockSupport wireMock;

    public GlobalHooks(WireMockSupport wireMock) {
        this.wireMock = wireMock;
    }

    @Before
    public void resetInfrastructureDoubles() {
        wireMock.resetToHappyPath();
    }
}
