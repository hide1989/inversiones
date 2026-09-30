package co.com.david.inversiones.usecase.market;

import co.com.david.inversiones.model.EarningsEvent;
import co.com.david.inversiones.model.Position;
import co.com.david.inversiones.model.Ticker;
import co.com.david.inversiones.model.gateways.EarningsProvider;
import co.com.david.inversiones.model.gateways.TimeProvider;
import co.com.david.inversiones.model.gateways.TransactionRepository;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Builds the earnings calendar for every instrument currently held, ordered by proximity.
 *
 * <p>{@code daysRemaining} is always recomputed here from {@link TimeProvider#today()} and the
 * event's report date, never trusted from the upstream {@code dias_restantes} field: n8n computes
 * that field from its own server clock at fetch time, which a test cannot control and which can be
 * stale by the time this use case runs. Recomputing from an injected {@link TimeProvider} is what
 * keeps this deterministic and testable.
 *
 * <p>An event whose report date has already passed is excluded rather than shown with a negative
 * "days remaining": the upstream workflow itself only queries a 90-day forward window (see
 * {@code WF-13}'s "Fechas Rango" node), so a past-dated event reaching this use case would mean the
 * calendar is stale, and the dashboard's "grid of cards: date, days remaining" has no sensible
 * rendering for a negative count.
 */
public class GetEarningsUseCase {

    private final TransactionRepository transactionRepository;
    private final EarningsProvider earningsProvider;
    private final TimeProvider timeProvider;

    public GetEarningsUseCase(TransactionRepository transactionRepository, EarningsProvider earningsProvider,
            TimeProvider timeProvider) {
        this.transactionRepository = transactionRepository;
        this.earningsProvider = earningsProvider;
        this.timeProvider = timeProvider;
    }

    public List<EarningsEvent> execute() {
        Set<Ticker> held = heldTickers();
        LocalDate today = timeProvider.today();

        return earningsProvider.fetchEvents(held).stream()
                .map(event -> recomputeDaysRemaining(event, today))
                .filter(event -> event.daysRemaining() >= 0)
                .sorted(Comparator.comparingLong(EarningsEvent::daysRemaining))
                .toList();
    }

    private Set<Ticker> heldTickers() {
        return Position.deriveAll(transactionRepository.findAll()).stream()
                .map(Position::ticker)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private static EarningsEvent recomputeDaysRemaining(EarningsEvent event, LocalDate today) {
        long daysRemaining = ChronoUnit.DAYS.between(today, event.reportDate());
        return EarningsEvent.builder()
                .ticker(event.ticker())
                .reportDate(event.reportDate())
                .daysRemaining(daysRemaining)
                .estimatedEps(event.estimatedEps())
                .historicalSurprise(event.historicalSurprise())
                .build();
    }
}
