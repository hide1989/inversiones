package co.com.david.inversiones.usecase.signal;

import co.com.david.inversiones.model.Position;
import co.com.david.inversiones.model.SignalSnapshot;
import co.com.david.inversiones.model.Ticker;
import co.com.david.inversiones.model.gateways.SignalProvider;
import co.com.david.inversiones.model.gateways.SignalSnapshotRepository;
import co.com.david.inversiones.model.gateways.TimeProvider;
import co.com.david.inversiones.model.gateways.TransactionRepository;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

/**
 * Pulls today's signals for every held instrument and stores them as a historical snapshot.
 *
 * <p>Two callers can trigger this: the daily {@code @Scheduled} job and a manual-refresh HTTP
 * endpoint. A run can take tens of seconds — it fans out to n8n endpoints as slow as the
 * {@code /sintesis-ia}-class calls — so {@link #running} guards against two triggers (scheduled or
 * manual, in any combination) executing concurrently against the same day's snapshot rows.
 * {@link AtomicBoolean#compareAndSet} makes the check-and-mark atomic, so two overlapping triggers
 * cannot both observe "not running" and both proceed. The guard lives here, one field shared by
 * every caller through this single Spring-managed bean, rather than duplicated in each caller: a
 * separate {@code AtomicBoolean} per caller would give zero mutual exclusion between them.
 *
 * <p>A provider that returns nothing stores nothing and reports zero, leaving the previous day's
 * snapshots intact rather than erasing history over a transient upstream failure.
 */
public class RefreshSignalSnapshotUseCase {

    private final TransactionRepository transactionRepository;
    private final SignalProvider signalProvider;
    private final SignalSnapshotRepository snapshotRepository;
    private final TimeProvider timeProvider;

    private final AtomicBoolean running = new AtomicBoolean(false);

    public RefreshSignalSnapshotUseCase(TransactionRepository transactionRepository,
                                        SignalProvider signalProvider,
                                        SignalSnapshotRepository snapshotRepository,
                                        TimeProvider timeProvider) {
        this.transactionRepository = transactionRepository;
        this.signalProvider = signalProvider;
        this.snapshotRepository = snapshotRepository;
        this.timeProvider = timeProvider;
    }

    /**
     * @return {@link RefreshOutcome.Completed} with how many snapshots were stored, or
     *     {@link RefreshOutcome.AlreadyRunning} if another run currently holds the guard
     */
    public RefreshOutcome execute() {
        if (!running.compareAndSet(false, true)) {
            return new RefreshOutcome.AlreadyRunning();
        }
        try {
            Set<Ticker> held = Position.deriveAll(transactionRepository.findAll()).stream()
                    .map(Position::ticker)
                    .collect(Collectors.toCollection(LinkedHashSet::new));

            if (held.isEmpty()) {
                return new RefreshOutcome.Completed(0);
            }

            List<SignalSnapshot> fetched = signalProvider.fetchSnapshots(held, timeProvider.today());
            if (fetched.isEmpty()) {
                return new RefreshOutcome.Completed(0);
            }

            return new RefreshOutcome.Completed(snapshotRepository.saveAll(fetched).size());
        } finally {
            running.set(false);
        }
    }
}
