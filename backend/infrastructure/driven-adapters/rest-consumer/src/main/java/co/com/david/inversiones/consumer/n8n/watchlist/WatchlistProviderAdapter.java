package co.com.david.inversiones.consumer.n8n.watchlist;

import co.com.david.inversiones.consumer.n8n.N8nGateway;
import co.com.david.inversiones.consumer.n8n.N8nSafeCall;
import co.com.david.inversiones.model.WatchlistItem;
import co.com.david.inversiones.model.gateways.WatchlistProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

/** {@link WatchlistProvider} backed by n8n's {@code /watchlist} (WF-18). A failure degrades to an empty list. */
@Component
public class WatchlistProviderAdapter implements WatchlistProvider {

    private static final Logger log = LoggerFactory.getLogger(WatchlistProviderAdapter.class);

    private final N8nGateway gateway;

    public WatchlistProviderAdapter(N8nGateway gateway) {
        this.gateway = gateway;
    }

    @Override
    public List<WatchlistItem> fetchWatchlist() {
        return WatchlistMapper.fromWatchlist(N8nSafeCall.safely(log, "/watchlist", gateway::fetchWatchlist));
    }
}
