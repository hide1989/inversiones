package co.com.david.inversiones.usecase.market;

import co.com.david.inversiones.model.AiSynthesis;
import co.com.david.inversiones.model.Position;
import co.com.david.inversiones.model.Ticker;
import co.com.david.inversiones.model.gateways.AiSynthesisProvider;
import co.com.david.inversiones.model.gateways.TransactionRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Builds the AI-synthesis view for every instrument currently held.
 *
 * <p>A ticker n8n marked {@code sin_datos} (its LLM call failed after retries — see WF-17 in
 * {@code AGENT_SPEC.md}) is excluded from {@link AiSynthesisReport#averageScore()} and listed
 * separately in {@link AiSynthesisReport#withoutData()}, exactly as {@code
 * GetSignalsDashboardUseCase} excludes a source-less ticker from its own average: folding a
 * placeholder score into the average would both drag the number down and present an assessment the
 * AI never actually made.
 *
 * <p>This use case never falls back to a previously stored synthesis when n8n has nothing fresh to
 * say: a user cannot tell a stale AI read from a current one, which makes presenting one as current
 * worse than an honest gap. This is why {@code ai_syntheses} (the persistence table in the schema)
 * is deliberately not read here.
 */
public class GetAiSynthesisUseCase {

    private static final int SCALE = 2;

    private final TransactionRepository transactionRepository;
    private final AiSynthesisProvider synthesisProvider;

    public GetAiSynthesisUseCase(TransactionRepository transactionRepository, AiSynthesisProvider synthesisProvider) {
        this.transactionRepository = transactionRepository;
        this.synthesisProvider = synthesisProvider;
    }

    public AiSynthesisReport execute() {
        Set<Ticker> held = heldTickers();
        List<AiSynthesis> syntheses = synthesisProvider.fetchSyntheses(held);

        List<AiSynthesis> withData = new ArrayList<>();
        List<Ticker> withoutData = new ArrayList<>();
        for (AiSynthesis synthesis : syntheses) {
            if (synthesis.hasData()) {
                withData.add(synthesis);
            } else {
                withoutData.add(synthesis.ticker());
            }
        }

        return new AiSynthesisReport(withData, withoutData, averageOf(withData));
    }

    private Set<Ticker> heldTickers() {
        return Position.deriveAll(transactionRepository.findAll()).stream()
                .map(Position::ticker)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private BigDecimal averageOf(List<AiSynthesis> withData) {
        if (withData.isEmpty()) {
            return BigDecimal.ZERO.setScale(SCALE, RoundingMode.HALF_UP);
        }
        BigDecimal total = withData.stream()
                .map(AiSynthesis::iaScore)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return total.divide(new BigDecimal(withData.size()), SCALE, RoundingMode.HALF_UP);
    }
}
