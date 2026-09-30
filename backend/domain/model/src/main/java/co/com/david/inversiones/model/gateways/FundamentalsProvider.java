package co.com.david.inversiones.model.gateways;

import co.com.david.inversiones.model.Fundamentals;
import co.com.david.inversiones.model.Ticker;

import java.util.Optional;

/**
 * Source of on-demand fundamentals analysis for one ticker at a time, backed by n8n.
 *
 * <p>Unlike the other three providers in this task, this one is not a bulk feed: n8n's
 * {@code /analizar-empresa} (and, for ETFs, {@code /etf-ficha}) is invoked per ticker, mirroring the
 * dashboard's on-demand "analizar fundamentales" action rather than a scheduled batch workflow.
 */
public interface FundamentalsProvider {

    /** Fetches fundamentals for one ticker. Empty when n8n could not analyze it at all. */
    Optional<Fundamentals> fetchFundamentals(Ticker ticker);
}
