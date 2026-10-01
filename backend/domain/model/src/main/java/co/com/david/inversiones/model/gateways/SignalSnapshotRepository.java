package co.com.david.inversiones.model.gateways;

import co.com.david.inversiones.model.SignalSnapshot;
import co.com.david.inversiones.model.Ticker;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Persistence port for historical {@link SignalSnapshot} records. */
public interface SignalSnapshotRepository {

    /** Persists the given snapshot, returning the stored state. */
    SignalSnapshot save(SignalSnapshot snapshot);

    /** Persists every given snapshot, returning the stored state of each. */
    List<SignalSnapshot> saveAll(List<SignalSnapshot> snapshots);

    /** Returns the most recent snapshot by date for this ticker, if one has ever been stored. */
    Optional<SignalSnapshot> findLatest(Ticker ticker);

    /**
     * Returns the most recent snapshot by date for every ticker in {@code tickers} that has ever
     * had one stored, keyed by ticker. A ticker with no stored snapshot is simply absent from the
     * result rather than mapped to {@code null} or an empty placeholder — the caller already has
     * to handle "no entry" for a {@link Map}, so this does not need a second way to say the same
     * thing.
     *
     * <p>This exists so a caller that needs the latest snapshot for many tickers at once — a
     * dashboard over every held instrument, say — can ask for all of them in one call instead of
     * looping {@link #findLatest(Ticker)} once per ticker: a loop like that turns an O(1) query
     * into an O(N) one by construction, regardless of how efficient each individual call is.
     */
    Map<Ticker, SignalSnapshot> findLatestForAll(Set<Ticker> tickers);

    /** Returns every snapshot for this ticker with a date between {@code from} and {@code to}, inclusive. */
    List<SignalSnapshot> findBetween(Ticker ticker, LocalDate from, LocalDate to);
}
