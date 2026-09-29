package co.com.david.inversiones.model.gateways;

import co.com.david.inversiones.model.SignalSnapshot;
import co.com.david.inversiones.model.Ticker;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/** Persistence port for historical {@link SignalSnapshot} records. */
public interface SignalSnapshotRepository {

    /** Persists the given snapshot, returning the stored state. */
    SignalSnapshot save(SignalSnapshot snapshot);

    /** Persists every given snapshot, returning the stored state of each. */
    List<SignalSnapshot> saveAll(List<SignalSnapshot> snapshots);

    /** Returns the most recent snapshot by date for this ticker, if one has ever been stored. */
    Optional<SignalSnapshot> findLatest(Ticker ticker);

    /** Returns every snapshot for this ticker with a date between {@code from} and {@code to}, inclusive. */
    List<SignalSnapshot> findBetween(Ticker ticker, LocalDate from, LocalDate to);
}
