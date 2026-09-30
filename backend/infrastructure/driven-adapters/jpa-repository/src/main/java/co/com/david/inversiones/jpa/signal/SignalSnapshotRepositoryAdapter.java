package co.com.david.inversiones.jpa.signal;

import co.com.david.inversiones.jpa.instrument.InstrumentData;
import co.com.david.inversiones.jpa.instrument.SpringDataInstrumentRepository;
import co.com.david.inversiones.model.SignalSnapshot;
import co.com.david.inversiones.model.Ticker;
import co.com.david.inversiones.model.exception.UnknownCatalogEntryException;
import co.com.david.inversiones.model.gateways.SignalSnapshotRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * JPA-backed implementation of {@link SignalSnapshotRepository}.
 *
 * <p>No {@link SignalSnapshotData} is ever returned from a public method here; every result passes
 * through {@link SignalSnapshotMapper} before it leaves this class.
 */
@Repository
public class SignalSnapshotRepositoryAdapter implements SignalSnapshotRepository {

    private final SpringDataSignalSnapshotRepository snapshotRepository;
    private final SpringDataInstrumentRepository instrumentRepository;

    public SignalSnapshotRepositoryAdapter(SpringDataSignalSnapshotRepository snapshotRepository,
            SpringDataInstrumentRepository instrumentRepository) {
        this.snapshotRepository = snapshotRepository;
        this.instrumentRepository = instrumentRepository;
    }

    /**
     * @throws UnknownCatalogEntryException when the snapshot's ticker is not in the catalog, so a
     *     missing reference surfaces as this domain exception rather than as a foreign-key
     *     constraint violation bubbling up as an unhandled 500.
     */
    @Override
    public SignalSnapshot save(SignalSnapshot snapshot) {
        InstrumentData instrument = resolveInstrument(snapshot.ticker());
        SignalSnapshotData toSave = toData(snapshot, instrument);
        SignalSnapshotData saved = snapshotRepository.save(toSave);
        return SignalSnapshotMapper.toDomain(saved);
    }

    /**
     * Resolves every snapshot's instrument and existing row first, then issues a single
     * {@code JpaRepository.saveAll} call for the whole batch, rather than looping {@code save}
     * once per snapshot — the latter would issue one round trip per row for what the scheduled
     * job that calls this treats as one unit of work.
     */
    @Override
    public List<SignalSnapshot> saveAll(List<SignalSnapshot> snapshots) {
        List<SignalSnapshotData> toSave = snapshots.stream()
                .map(snapshot -> toData(snapshot, resolveInstrument(snapshot.ticker())))
                .toList();
        return snapshotRepository.saveAll(toSave).stream().map(SignalSnapshotMapper::toDomain).toList();
    }

    @Override
    public Optional<SignalSnapshot> findLatest(Ticker ticker) {
        return snapshotRepository.findFirstByInstrumentTickerOrderBySnapshotDateDesc(ticker.symbol())
                .map(SignalSnapshotMapper::toDomain);
    }

    @Override
    public List<SignalSnapshot> findBetween(Ticker ticker, LocalDate from, LocalDate to) {
        return snapshotRepository
                .findByInstrumentTickerAndSnapshotDateBetweenOrderBySnapshotDateAsc(ticker.symbol(), from, to)
                .stream()
                .map(SignalSnapshotMapper::toDomain)
                .toList();
    }

    private InstrumentData resolveInstrument(Ticker ticker) {
        return instrumentRepository.findByTicker(ticker.symbol())
                .orElseThrow(() -> UnknownCatalogEntryException.forInstrument(ticker));
    }

    private SignalSnapshotData toData(SignalSnapshot snapshot, InstrumentData instrument) {
        Long existingId = snapshotRepository
                .findByInstrumentIdAndSnapshotDate(instrument.getId(), snapshot.snapshotDate())
                .map(SignalSnapshotData::getId)
                .orElse(null);
        return SignalSnapshotMapper.toData(snapshot, instrument, existingId);
    }
}
