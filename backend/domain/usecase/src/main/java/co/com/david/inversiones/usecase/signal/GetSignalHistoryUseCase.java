package co.com.david.inversiones.usecase.signal;

import co.com.david.inversiones.model.SignalSnapshot;
import co.com.david.inversiones.model.Ticker;
import co.com.david.inversiones.model.gateways.SignalSnapshotRepository;

import java.time.LocalDate;
import java.util.List;

/**
 * Fetches the historical signal snapshots recorded for one ticker within a date range.
 *
 * <p>Added for Task 9: {@link SignalSnapshotRepository#findBetween(Ticker, LocalDate, LocalDate)}
 * had no use case exposing it, and the API layer must never call a repository port directly from
 * a controller. This use case is the thin pass-through that keeps that layering intact for the
 * {@code GET /api/v1/signals/history} endpoint.
 */
public class GetSignalHistoryUseCase {

    private final SignalSnapshotRepository snapshotRepository;

    public GetSignalHistoryUseCase(SignalSnapshotRepository snapshotRepository) {
        this.snapshotRepository = snapshotRepository;
    }

    public List<SignalSnapshot> execute(Ticker ticker, LocalDate from, LocalDate to) {
        return snapshotRepository.findBetween(ticker, from, to);
    }
}
