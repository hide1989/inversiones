package co.com.david.inversiones.consumer.n8n.earnings;

import co.com.david.inversiones.consumer.n8n.N8nGateway;
import co.com.david.inversiones.consumer.n8n.N8nSafeCall;
import co.com.david.inversiones.model.EarningsEvent;
import co.com.david.inversiones.model.Ticker;
import co.com.david.inversiones.model.gateways.EarningsProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

/** {@link EarningsProvider} backed by n8n's {@code /earnings} (WF-13). Not cached; a failure degrades to an empty list. */
@Component
public class EarningsProviderAdapter implements EarningsProvider {

    private static final Logger log = LoggerFactory.getLogger(EarningsProviderAdapter.class);

    private final N8nGateway gateway;

    public EarningsProviderAdapter(N8nGateway gateway) {
        this.gateway = gateway;
    }

    @Override
    public List<EarningsEvent> fetchEvents(Set<Ticker> tickers) {
        List<EarningsEvent> events = EarningsMapper.fromEarnings(
                N8nSafeCall.safely(log, "/earnings", gateway::fetchEarnings));

        return events.stream()
                .filter(event -> tickers.contains(event.ticker()))
                .toList();
    }
}
