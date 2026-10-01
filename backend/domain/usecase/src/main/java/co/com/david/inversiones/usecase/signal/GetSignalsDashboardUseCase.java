package co.com.david.inversiones.usecase.signal;

import co.com.david.inversiones.model.*;
import co.com.david.inversiones.model.gateways.SignalSnapshotRepository;
import co.com.david.inversiones.model.gateways.TransactionRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Builds the signals dashboard for every instrument currently held.
 *
 * <p>Rows are ordered by combined score descending, highest conviction first. A ticker with no
 * snapshot, or a snapshot with no sources, is listed in
 * {@link SignalsDashboard#tickersWithoutData()} and excluded from the average and the level counts:
 * a zero-source score maps to a strong sell, and including it would fabricate a recommendation
 * nobody made.
 *
 * <p>The latest snapshot for every held ticker is fetched in a single
 * {@link SignalSnapshotRepository#findLatestForAll} call rather than looped one ticker at a time:
 * looping {@code findLatest} turned a single-query dashboard build into one query (plus a lazy
 * association load) per held ticker — an N+1 of the same shape an earlier fix already closed on a
 * different finder, missed here because "this finder returns at most one row" is not the same
 * question as "how many times does this use case call it".
 */
public class GetSignalsDashboardUseCase {

    private static final int SCALE = 2;

    private final TransactionRepository transactionRepository;
    private final SignalSnapshotRepository snapshotRepository;

    public GetSignalsDashboardUseCase(TransactionRepository transactionRepository,
                                      SignalSnapshotRepository snapshotRepository) {
        this.transactionRepository = transactionRepository;
        this.snapshotRepository = snapshotRepository;
    }

    public SignalsDashboard execute() {
        Set<Ticker> held = heldTickers();
        Map<Ticker, SignalSnapshot> latestByTicker = snapshotRepository.findLatestForAll(held);

        List<TickerSignal> signals = new ArrayList<>();
        List<Ticker> withoutData = new ArrayList<>();

        for (Ticker ticker : held) {
            SignalSnapshot snapshot = Optional.ofNullable(latestByTicker.get(ticker))
                    .orElseGet(() -> SignalSnapshot.builder()
                            .ticker(ticker).snapshotDate(LocalDate.EPOCH).scores(Map.of()).build());
            CombinedScore score = CombinedScore.from(snapshot);
            signals.add(new TickerSignal(ticker, snapshot, score));
            if (score.usedSources().isEmpty()) {
                withoutData.add(ticker);
            }
        }

        signals.sort(Comparator.comparing((TickerSignal signal) -> signal.score().value()).reversed());

        List<TickerSignal> scored = signals.stream()
                .filter(signal -> !signal.score().usedSources().isEmpty())
                .toList();

        return SignalsDashboard.builder()
                .signals(signals)
                .averageScore(averageOf(scored))
                .levelCounts(countLevels(scored))
                .tickersWithoutData(withoutData)
                .build();
    }

    private Set<Ticker> heldTickers() {
        return Position.deriveAll(transactionRepository.findAll()).stream()
                .map(Position::ticker)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private BigDecimal averageOf(List<TickerSignal> scored) {
        if (scored.isEmpty()) {
            return BigDecimal.ZERO.setScale(SCALE, RoundingMode.HALF_UP);
        }
        BigDecimal total = scored.stream()
                .map(signal -> signal.score().value())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return total.divide(new BigDecimal(scored.size()), SCALE, RoundingMode.HALF_UP);
    }

    private Map<SignalLevel, Integer> countLevels(List<TickerSignal> scored) {
        Map<SignalLevel, Integer> counts = new EnumMap<>(SignalLevel.class);
        for (TickerSignal signal : scored) {
            counts.merge(signal.score().level(), 1, Integer::sum);
        }
        return counts;
    }
}
