package co.com.david.inversiones.usecase.market;

import co.com.david.inversiones.model.WatchlistItem;
import co.com.david.inversiones.model.gateways.WatchlistProvider;

import java.util.List;

/**
 * Fetches n8n's externally-sourced watchlist as-is.
 *
 * <p>Unlike the other three use cases in this task, this one is not scoped to the held portfolio:
 * the watchlist is n8n's own curated list of tickers worth watching, independent of what is owned.
 */
public class GetWatchlistUseCase {

    private final WatchlistProvider watchlistProvider;

    public GetWatchlistUseCase(WatchlistProvider watchlistProvider) {
        this.watchlistProvider = watchlistProvider;
    }

    public List<WatchlistItem> execute() {
        return watchlistProvider.fetchWatchlist();
    }
}
