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
import java.util.stream.Collectors;

/**
 * Pulls today's signals for every held instrument and stores them as a historical snapshot.
 *
 * <p>Intended to run on a schedule after the upstream workflows have refreshed their own caches. A
 * provider that returns nothing stores nothing and reports zero, leaving the previous day's
 * snapshots intact rather than erasing history over a transient upstream failure.
 */
public class RefreshSignalSnapshotUseCase {

    private final TransactionRepository transactionRepository;
    private final SignalProvider signalProvider;
    private final SignalSnapshotRepository snapshotRepository;
    private final TimeProvider timeProvider;

    public RefreshSignalSnapshotUseCase(TransactionRepository transactionRepository,
                                        SignalProvider signalProvider,
                                        SignalSnapshotRepository snapshotRepository,
                                        TimeProvider timeProvider) {
        this.transactionRepository = transactionRepository;
        this.signalProvider = signalProvider;
        this.snapshotRepository = snapshotRepository;
        this.timeProvider = timeProvider;
    }

    /** @return how many snapshots were stored */
    public int execute() {
        Set<Ticker> held = Position.deriveAll(transactionRepository.findAll()).stream()
                .map(Position::ticker)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        if (held.isEmpty()) {
            return 0;
        }

        List<SignalSnapshot> fetched = signalProvider.fetchSnapshots(held, timeProvider.today());
        if (fetched.isEmpty()) {
            return 0;
        }

        return snapshotRepository.saveAll(fetched).size();
    }
}
