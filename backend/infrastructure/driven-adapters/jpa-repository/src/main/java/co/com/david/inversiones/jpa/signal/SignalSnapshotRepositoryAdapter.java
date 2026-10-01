package co.com.david.inversiones.jpa.signal;

import co.com.david.inversiones.jpa.instrument.InstrumentData;
import co.com.david.inversiones.jpa.instrument.SpringDataInstrumentRepository;
import co.com.david.inversiones.model.SignalSnapshot;
import co.com.david.inversiones.model.Ticker;
import co.com.david.inversiones.model.exception.UnknownCatalogEntryException;
import co.com.david.inversiones.model.gateways.SignalSnapshotRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * JPA-backed implementation of {@link SignalSnapshotRepository}.
 *
 * <p>No {@link SignalSnapshotData} is ever returned from a public method here; every result passes
 * through {@link SignalSnapshotMapper} before it leaves this class.
 *
 * <p>{@code instrument} on {@link SignalSnapshotData} is {@code FetchType.LAZY}. As in
 * {@code TransactionRepositoryAdapter}, with {@code spring.jpa.open-in-view: false} a derived
 * Spring Data method's own transaction closes before {@link SignalSnapshotMapper#toDomain} reads
 * the lazy instrument proxy, so every read method here needs its own
 * {@code @Transactional(readOnly = true)} to keep one session open across both calls — but a
 * transaction only keeps the proxy alive long enough to be loaded; it does not stop the load from
 * happening once per row. {@link #findBetween} can return many rows (a whole history range), so it
 * fetches {@code instrument} eagerly via {@code @EntityGraph} on
 * {@link SpringDataSignalSnapshotRepository} instead — one SQL statement regardless of row count.
 * {@link #findLatest} also now carries {@code @EntityGraph} (see
 * {@link SpringDataSignalSnapshotRepository#findFirstByInstrumentTickerOrderBySnapshotDateDesc}):
 * "at most one row per call" turned out to be the wrong criterion, because
 * {@code GetSignalsDashboardUseCase} calls it once per held ticker — rows-per-call was never the
 * axis the N+1 ran along, calls-per-request was. {@link #findLatestForAll} is the real fix for that
 * caller: one statement for every held ticker instead of one call per ticker.
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
    @Transactional
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
    @Transactional
    public List<SignalSnapshot> saveAll(List<SignalSnapshot> snapshots) {
        List<SignalSnapshotData> toSave = snapshots.stream()
                .map(snapshot -> toData(snapshot, resolveInstrument(snapshot.ticker())))
                .toList();
        return snapshotRepository.saveAll(toSave).stream().map(SignalSnapshotMapper::toDomain).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<SignalSnapshot> findLatest(Ticker ticker) {
        return snapshotRepository.findFirstByInstrumentTickerOrderBySnapshotDateDesc(ticker.symbol())
                .map(SignalSnapshotMapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<Ticker, SignalSnapshot> findLatestForAll(Set<Ticker> tickers) {
        if (tickers.isEmpty()) {
            return Map.of();
        }
        List<String> symbols = tickers.stream().map(Ticker::symbol).toList();
        return snapshotRepository.findLatestForTickers(symbols).stream()
                .collect(Collectors.toMap(
                        data -> Ticker.of(data.getInstrument().getTicker()),
                        SignalSnapshotMapper::toDomain));
    }

    @Override
    @Transactional(readOnly = true)
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
