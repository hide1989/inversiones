package co.com.david.inversiones.model.gateways;

import co.com.david.inversiones.model.WatchlistItem;

import java.util.List;

/**
 * Source of the externally-sourced watchlist (WSB trending tickers, sector picks), backed by n8n.
 *
 * <p>Unlike {@link QuoteProvider} or {@link SignalProvider}, this is not scoped to the tickers the
 * portfolio holds: the watchlist is n8n's own curated list, independent of what is actually owned.
 */
public interface WatchlistProvider {

    /** Fetches the current watchlist in full. Returns an empty list when n8n has nothing to report. */
    List<WatchlistItem> fetchWatchlist();
}
