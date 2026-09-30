package co.com.david.inversiones.model.gateways;

import co.com.david.inversiones.model.AiSynthesis;
import co.com.david.inversiones.model.Ticker;

import java.util.List;
import java.util.Set;

/**
 * Source of the AI-generated synthesis for a set of tickers, backed by n8n.
 *
 * <p>Unlike {@link SignalProvider}, a ticker the upstream workflow could not analyze is not simply
 * omitted: it comes back with {@code hasData() == false} so the caller can list it explicitly rather
 * than silently dropping it (see {@code AGENT_SPEC.md}'s {@code sin_datos} marker). A ticker n8n has
 * never heard of at all is omitted, exactly like every other provider in this module.
 */
public interface AiSynthesisProvider {

    /** Fetches the latest synthesis for each requested ticker n8n has any record of, with or without data. */
    List<AiSynthesis> fetchSyntheses(Set<Ticker> tickers);
}
