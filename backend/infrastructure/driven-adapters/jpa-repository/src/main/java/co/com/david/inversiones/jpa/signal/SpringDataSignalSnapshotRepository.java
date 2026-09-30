package co.com.david.inversiones.jpa.signal;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/** Spring Data repository for {@link SignalSnapshotData}. */
public interface SpringDataSignalSnapshotRepository extends JpaRepository<SignalSnapshotData, Long> {

    /**
     * Derived query ordering by {@code snapshot_date DESC} and limiting to one row in SQL (backed
     * by the {@code signal_snapshots_instrument_date_idx} index). Loading every snapshot for an
     * instrument and picking the maximum date in Java would not scale: this table grows one row
     * per instrument per day forever.
     */
    Optional<SignalSnapshotData> findFirstByInstrumentTickerOrderBySnapshotDateDesc(String ticker);

    /** All snapshots for a ticker with a date in {@code [from, to]}, ascending by date. */
    List<SignalSnapshotData> findByInstrumentTickerAndSnapshotDateBetweenOrderBySnapshotDateAsc(
            String ticker, LocalDate from, LocalDate to);

    /**
     * Looks up the existing row for the unique {@code (instrument_id, snapshot_date)} pair, so the
     * adapter can turn a second save for the same instrument and day into an update rather than a
     * unique-constraint violation.
     */
    Optional<SignalSnapshotData> findByInstrumentIdAndSnapshotDate(Long instrumentId, LocalDate snapshotDate);
}
