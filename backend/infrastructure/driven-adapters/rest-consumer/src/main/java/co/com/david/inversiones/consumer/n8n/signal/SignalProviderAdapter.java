package co.com.david.inversiones.consumer.n8n.signal;

import co.com.david.inversiones.consumer.n8n.N8nGateway;
import co.com.david.inversiones.consumer.n8n.N8nSafeCall;
import co.com.david.inversiones.model.SignalSnapshot;
import co.com.david.inversiones.model.SignalSource;
import co.com.david.inversiones.model.Ticker;
import co.com.david.inversiones.model.gateways.SignalProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * {@link SignalProvider} backed by six independent n8n workflows, one per {@link SignalSource}.
 *
 * <p>Each source is fetched independently through {@link N8nSafeCall}, which turns any failure from
 * that one endpoint — a 500, a timeout, malformed JSON, or an open circuit breaker — into an absent
 * result for that source only; the other five are fetched and merged regardless. This class never
 * distinguishes "the endpoint failed" from "the endpoint had nothing for this ticker": either way,
 * that source is simply missing from the merged snapshot, and {@code CombinedScore.from} already
 * knows how to redistribute the missing weight across whichever sources are present. A snapshot is
 * only produced for a requested ticker that has at least one source available; a ticker with zero
 * data across all six endpoints is omitted rather than represented by an all-absent snapshot.
 *
 * <p>Not cached: a signal is meant to reflect what n8n currently reports, and re-fetching on every
 * call is how a source that just recovered gets noticed again.
 */
@Component
public class SignalProviderAdapter implements SignalProvider {

    private static final Logger log = LoggerFactory.getLogger(SignalProviderAdapter.class);

    private final N8nGateway gateway;

    public SignalProviderAdapter(N8nGateway gateway) {
        this.gateway = gateway;
    }

    @Override
    public List<SignalSnapshot> fetchSnapshots(Set<Ticker> tickers, LocalDate date) {
        Map<Ticker, Map<SignalSource, BigDecimal>> byTicker = new LinkedHashMap<>();

        merge(byTicker, SignalSource.TECHNICAL, SignalMapper.fromIndicadoresTecnicos(
                N8nSafeCall.safely(log, "/indicadores-tecnicos", gateway::fetchIndicadoresTecnicos)));
        merge(byTicker, SignalSource.NEWS, SignalMapper.fromNewsSentiment(
                N8nSafeCall.safely(log, "/news-sentiment", gateway::fetchNewsSentiment)));
        merge(byTicker, SignalSource.ANALYST, SignalMapper.fromRatingsAnalistas(
                N8nSafeCall.safely(log, "/ratings-analistas", gateway::fetchRatingsAnalistas)));
        merge(byTicker, SignalSource.INSIDER, SignalMapper.fromInsiderTrading(
                N8nSafeCall.safely(log, "/insider-trading", gateway::fetchInsiderTrading)));
        merge(byTicker, SignalSource.SOCIAL, SignalMapper.fromStocktwits(
                N8nSafeCall.safely(log, "/stocktwits", gateway::fetchStocktwits)));
        merge(byTicker, SignalSource.WSB, SignalMapper.fromReddit(
                N8nSafeCall.safely(log, "/reddit", gateway::fetchReddit)));

        return tickers.stream()
                .filter(byTicker::containsKey)
                .map(ticker -> SignalSnapshot.builder()
                        .ticker(ticker)
                        .snapshotDate(date)
                        .scores(byTicker.get(ticker))
                        .build())
                .toList();
    }

    private static void merge(Map<Ticker, Map<SignalSource, BigDecimal>> byTicker, SignalSource source,
            Map<Ticker, BigDecimal> scoresForSource) {
        scoresForSource.forEach((ticker, score) ->
                byTicker.computeIfAbsent(ticker, t -> new LinkedHashMap<>()).put(source, score));
    }
}
