package co.com.david.inversiones.consumer.n8n.aisynthesis;

import co.com.david.inversiones.consumer.n8n.N8nGateway;
import co.com.david.inversiones.consumer.n8n.N8nSafeCall;
import co.com.david.inversiones.model.AiSynthesis;
import co.com.david.inversiones.model.Ticker;
import co.com.david.inversiones.model.gateways.AiSynthesisProvider;
import co.com.david.inversiones.model.gateways.TimeProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

/**
 * {@link AiSynthesisProvider} backed by n8n's {@code /sintesis-ia} (WF-17).
 *
 * <p>Not cached, and deliberately never falls back to a previously stored synthesis when this call
 * fails: a stale AI read presented as current is worse than an honest "no data," since the user has
 * no way to tell the two apart. A failure here therefore degrades to an empty list — every requested
 * ticker ends up with no synthesis at all, exactly as if n8n had never heard of it — rather than
 * substituting anything from the {@code ai_syntheses} persistence table.
 */
@Component
public class AiSynthesisProviderAdapter implements AiSynthesisProvider {

    private static final Logger log = LoggerFactory.getLogger(AiSynthesisProviderAdapter.class);

    private final N8nGateway gateway;
    private final TimeProvider timeProvider;

    public AiSynthesisProviderAdapter(N8nGateway gateway, TimeProvider timeProvider) {
        this.gateway = gateway;
        this.timeProvider = timeProvider;
    }

    @Override
    public List<AiSynthesis> fetchSyntheses(Set<Ticker> tickers) {
        List<AiSynthesis> syntheses = AiSynthesisMapper.fromSintesisIa(
                N8nSafeCall.safely(log, "/sintesis-ia", gateway::fetchSintesisIa), timeProvider.today());

        return syntheses.stream()
                .filter(synthesis -> tickers.contains(synthesis.ticker()))
                .toList();
    }
}
