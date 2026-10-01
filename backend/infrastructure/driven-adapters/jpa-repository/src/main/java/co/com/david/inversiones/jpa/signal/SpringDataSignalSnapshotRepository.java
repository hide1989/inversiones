package co.com.david.inversiones.jpa.signal;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** Spring Data repository for {@link SignalSnapshotData}. */
public interface SpringDataSignalSnapshotRepository extends JpaRepository<SignalSnapshotData, Long> {

    /**
     * Derived query ordering by {@code snapshot_date DESC} and limiting to one row in SQL (backed
     * by the {@code signal_snapshots_instrument_date_idx} index). Loading every snapshot for an
     * instrument and picking the maximum date in Java would not scale: this table grows one row
     * per instrument per day forever.
     *
     * <p>{@code @EntityGraph} fetches {@code instrument} in the same statement for the same reason
     * as {@link #findByInstrumentTickerAndSnapshotDateBetweenOrderBySnapshotDateAsc}: this method
     * returning at most one row made it easy to assume a lazy load on that one row could not become
     * an N+1, but rows-per-call was never the right test — {@code GetSignalsDashboardUseCase} calls
     * this once per held ticker, so without the graph a dashboard over N tickers issued 1 + 2N
     * statements (N calls to this method, each a SELECT plus a lazy-load SELECT, plus the one query
     * for the held tickers themselves). See also {@link #findLatestForTickers} for the batched
     * alternative that replaces the loop entirely.
     */
    @EntityGraph(attributePaths = {"instrument"})
    Optional<SignalSnapshotData> findFirstByInstrumentTickerOrderBySnapshotDateDesc(String ticker);

    /**
     * The most recent snapshot row per instrument, among the given tickers, in one statement.
     *
     * <p>This is the fix {@code GetSignalsDashboardUseCase} actually needs: looping
     * {@link #findFirstByInstrumentTickerOrderBySnapshotDateDesc} once per held ticker turns an O(1)
     * query into an O(N) one no matter how each individual call is optimized. The correlated
     * subquery picks, for each candidate row, only the one whose date equals the maximum date ever
     * stored for that same instrument — i.e. exactly the "latest per ticker" set — and
     * {@code @EntityGraph} fetches {@code instrument} in the same statement so the mapper's read of
     * the lazy association costs nothing extra.
     */
    @EntityGraph(attributePaths = {"instrument"})
    @Query("SELECT s FROM SignalSnapshotData s WHERE s.instrument.ticker IN :tickers "
            + "AND s.snapshotDate = (SELECT MAX(s2.snapshotDate) FROM SignalSnapshotData s2 "
            + "WHERE s2.instrument = s.instrument)")
    List<SignalSnapshotData> findLatestForTickers(@Param("tickers") Collection<String> tickers);

    /**
     * All snapshots for a ticker with a date in {@code [from, to]}, ascending by date.
     *
     * <p>{@code @EntityGraph} fetches {@code instrument} — {@code FetchType.LAZY} on
     * {@link SignalSnapshotData} — in the same SQL statement via a JOIN, rather than leaving it as a
     * lazy proxy: {@link SignalSnapshotMapper#toDomain} reads it for every row this returns, so
     * without this a transaction-wrapped call still issues one extra query per row (an N+1) even
     * though it no longer throws {@code LazyInitializationException}. A transaction keeps the
     * session open long enough for a lazy load to succeed; it does not stop one from happening. A
     * history range can return many rows, so this is the method where an N+1 would actually bite.
     */
    @EntityGraph(attributePaths = {"instrument"})
    List<SignalSnapshotData> findByInstrumentTickerAndSnapshotDateBetweenOrderBySnapshotDateAsc(
            String ticker, LocalDate from, LocalDate to);

    /**
     * Looks up the existing row for the unique {@code (instrument_id, snapshot_date)} pair, so the
     * adapter can turn a second save for the same instrument and day into an update rather than a
     * unique-constraint violation.
     */
    Optional<SignalSnapshotData> findByInstrumentIdAndSnapshotDate(Long instrumentId, LocalDate snapshotDate);
}
