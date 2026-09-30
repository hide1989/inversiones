package co.com.david.inversiones.consumer.n8n.quote;

import co.com.david.inversiones.model.Quote;
import co.com.david.inversiones.model.Ticker;
import co.com.david.inversiones.model.gateways.QuoteProvider;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * {@link QuoteProvider} backed by n8n, merging NYSE/NASDAQ prices ({@code /precios-hoy}, in US
 * dollars) with Colombian Stock Exchange prices ({@code /bvc-precios}, in pesos).
 *
 * <p>The merge and its caching live in {@link QuoteCacheSource}; this class only filters the cached
 * result down to the tickers the caller asked for, so a request for a subset of tickers is still a
 * cache hit against the same shared entry.
 */
@Component
public class QuoteProviderAdapter implements QuoteProvider {

    private final QuoteCacheSource cacheSource;

    public QuoteProviderAdapter(QuoteCacheSource cacheSource) {
        this.cacheSource = cacheSource;
    }

    @Override
    public Map<Ticker, Quote> findQuotes(Set<Ticker> tickers) {
        Map<Ticker, Quote> all = cacheSource.allQuotes();
        if (tickers.isEmpty()) {
            return Map.of();
        }
        Map<Ticker, Quote> filtered = new LinkedHashMap<>(all);
        filtered.keySet().retainAll(tickers);
        return Map.copyOf(filtered);
    }
}
