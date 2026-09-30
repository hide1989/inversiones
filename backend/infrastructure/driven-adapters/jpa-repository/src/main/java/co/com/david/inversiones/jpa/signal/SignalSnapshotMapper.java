package co.com.david.inversiones.jpa.signal;

import co.com.david.inversiones.jpa.instrument.InstrumentData;
import co.com.david.inversiones.model.CombinedScore;
import co.com.david.inversiones.model.SignalSnapshot;
import co.com.david.inversiones.model.SignalSource;
import co.com.david.inversiones.model.Ticker;

/**
 * Hand-written mapping between {@link SignalSnapshotData} and {@link SignalSnapshot}.
 *
 * <p><b>Null versus zero.</b> Each of the six score columns is independently nullable, and the
 * domain's {@link SignalSnapshot} distinguishes "this source reported a score" from "this source
 * has no data" only by whether the corresponding map entry exists at all — see that class's
 * Javadoc. {@link CombinedScore#from(SignalSnapshot)} then redistributes a missing source's weight
 * across the sources that are present. So on the way in, a column read as {@code null} must never
 * become a map entry (not even one holding {@link java.math.BigDecimal#ZERO}): doing so would tell
 * the combined score that the source reported the worst possible result, when the truth is simply
 * that nobody asked it — an ETF with no insider activity, a news API with no coverage for a thin
 * ticker, a provider call that timed out. On the way out the same rule runs in reverse: a source
 * absent from {@link SignalSnapshot#availableSources()} must write SQL {@code NULL}, not zero.
 *
 * <p><b>Combined score is write-only.</b> {@code combined_score} and {@code signal_level} are
 * persisted on every write (computed via {@link CombinedScore#from(SignalSnapshot)}) so the columns
 * are available for reporting and future SQL queries, but they are never read back into the domain
 * here: {@link CombinedScore#from(SignalSnapshot)} recomputes the value from the live scores and
 * weights every time, and a value stored days or weeks ago under a formula that has since changed
 * must never be allowed to win over the current computation.
 *
 * <p><b>Raw payload is opaque.</b> {@code raw_payload} is captured for a future re-scoring pass but
 * has no field on the domain {@link SignalSnapshot}; this mapper writes {@code null} for it and
 * ignores it entirely on read.
 */
final class SignalSnapshotMapper {

    private SignalSnapshotMapper() {
    }

    static SignalSnapshot toDomain(SignalSnapshotData data) {
        SignalSnapshot.Builder builder = SignalSnapshot.builder()
                .ticker(Ticker.of(data.getInstrument().getTicker()))
                .snapshotDate(data.getSnapshotDate());

        putIfPresent(builder, SignalSource.TECHNICAL, data.getTechnicalScore());
        putIfPresent(builder, SignalSource.NEWS, data.getNewsScore());
        putIfPresent(builder, SignalSource.ANALYST, data.getAnalystScore());
        putIfPresent(builder, SignalSource.INSIDER, data.getInsiderScore());
        putIfPresent(builder, SignalSource.SOCIAL, data.getSocialScore());
        putIfPresent(builder, SignalSource.WSB, data.getWsbScore());

        return builder.build();
    }

    private static void putIfPresent(SignalSnapshot.Builder builder, SignalSource source,
            java.math.BigDecimal score) {
        if (score != null) {
            builder.score(source, score);
        }
    }

    /**
     * @param existingId the id of the row already stored for this instrument and date, or
     *     {@code null} when none exists; carrying it through makes {@code JpaRepository.save}
     *     perform an update instead of an insert, so a second save for the same
     *     (instrument, snapshot_date) pair never hits the table's unique constraint.
     */
    static SignalSnapshotData toData(SignalSnapshot snapshot, InstrumentData instrument, Long existingId) {
        CombinedScore combined = CombinedScore.from(snapshot);
        return SignalSnapshotData.builder()
                .id(existingId)
                .instrument(instrument)
                .snapshotDate(snapshot.snapshotDate())
                .technicalScore(snapshot.scoreOf(SignalSource.TECHNICAL).orElse(null))
                .newsScore(snapshot.scoreOf(SignalSource.NEWS).orElse(null))
                .analystScore(snapshot.scoreOf(SignalSource.ANALYST).orElse(null))
                .insiderScore(snapshot.scoreOf(SignalSource.INSIDER).orElse(null))
                .socialScore(snapshot.scoreOf(SignalSource.SOCIAL).orElse(null))
                .wsbScore(snapshot.scoreOf(SignalSource.WSB).orElse(null))
                .combinedScore(combined.value())
                .signalLevel(combined.level())
                .rawPayload(null)
                .build();
    }
}
