package co.com.david.inversiones.jpa.signal;

import co.com.david.inversiones.model.SignalSnapshot;
import co.com.david.inversiones.model.SignalSource;
import co.com.david.inversiones.model.Ticker;
import co.com.david.inversiones.model.exception.UnknownCatalogEntryException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration tests for {@link SignalSnapshotRepositoryAdapter} against a real PostgreSQL instance,
 * with Flyway applying the actual migrations so the tests exercise the same catalog the running
 * application starts with.
 */
@Testcontainers
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({SignalSnapshotRepositoryAdapter.class,
        co.com.david.inversiones.jpa.instrument.InstrumentRepositoryAdapter.class})
class SignalSnapshotRepositoryAdapterTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired
    private SignalSnapshotRepositoryAdapter adapter;

    @Autowired
    private SpringDataSignalSnapshotRepository springDataRepository;

    @Autowired
    private co.com.david.inversiones.jpa.instrument.SpringDataInstrumentRepository instrumentRepository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    void aSnapshotWithThreeOfSixSourcesRoundTripsWithExactlyThoseThreePresent() {
        SignalSnapshot toSave = SignalSnapshot.builder()
                .ticker(Ticker.of("QQQ"))
                .snapshotDate(LocalDate.of(2026, 4, 1))
                .score(SignalSource.TECHNICAL, new BigDecimal("7.50"))
                .score(SignalSource.NEWS, new BigDecimal("6.00"))
                .score(SignalSource.ANALYST, new BigDecimal("8.25"))
                .build();

        SignalSnapshot saved = adapter.save(toSave);
        entityManager.flush();
        entityManager.clear();

        SignalSnapshot reloaded = adapter.findLatest(Ticker.of("QQQ")).orElseThrow();

        assertThat(reloaded.availableSources())
                .containsExactlyInAnyOrder(SignalSource.TECHNICAL, SignalSource.NEWS, SignalSource.ANALYST);
        assertThat(reloaded.scoreOf(SignalSource.TECHNICAL)).contains(new BigDecimal("7.50"));
        assertThat(reloaded.scoreOf(SignalSource.NEWS)).contains(new BigDecimal("6.00"));
        assertThat(reloaded.scoreOf(SignalSource.ANALYST)).contains(new BigDecimal("8.25"));
        assertThat(reloaded.scoreOf(SignalSource.INSIDER)).isEmpty();
        assertThat(reloaded.scoreOf(SignalSource.SOCIAL)).isEmpty();
        assertThat(reloaded.scoreOf(SignalSource.WSB)).isEmpty();
        assertThat(saved.ticker()).isEqualTo(Ticker.of("QQQ"));
    }

    @Test
    void aSnapshotWithANullInsiderScoreColumnComesBackWithInsiderAbsentNotZero() {
        SignalSnapshot toSave = SignalSnapshot.builder()
                .ticker(Ticker.of("VOO"))
                .snapshotDate(LocalDate.of(2026, 4, 2))
                .score(SignalSource.TECHNICAL, new BigDecimal("5.00"))
                .score(SignalSource.NEWS, new BigDecimal("5.00"))
                .score(SignalSource.ANALYST, new BigDecimal("5.00"))
                .score(SignalSource.SOCIAL, new BigDecimal("5.00"))
                .score(SignalSource.WSB, new BigDecimal("5.00"))
                // INSIDER deliberately omitted: this is the column that must stay NULL in the
                // database and come back as an absent map entry, never as zero.
                .build();

        adapter.save(toSave);
        entityManager.flush();
        entityManager.clear();

        SignalSnapshot reloaded = adapter.findLatest(Ticker.of("VOO")).orElseThrow();

        assertThat(reloaded.scoreOf(SignalSource.INSIDER)).isEmpty();
        assertThat(reloaded.availableSources()).doesNotContain(SignalSource.INSIDER);
    }

    @Test
    void writingASnapshotThatOmitsASourceStoresSqlNullNotZero() {
        SignalSnapshot toSave = SignalSnapshot.builder()
                .ticker(Ticker.of("SMH"))
                .snapshotDate(LocalDate.of(2026, 4, 3))
                .score(SignalSource.TECHNICAL, new BigDecimal("6.00"))
                .build();

        adapter.save(toSave);
        entityManager.flush();
        entityManager.clear();

        Long instrumentId = instrumentRepository.findByTicker("SMH").orElseThrow().getId();
        Object rawInsiderScore = entityManager.getEntityManager()
                .createNativeQuery("select insider_score from signal_snapshots "
                        + "where instrument_id = ?1 and snapshot_date = ?2")
                .setParameter(1, instrumentId)
                .setParameter(2, LocalDate.of(2026, 4, 3))
                .getSingleResult();

        assertThat(rawInsiderScore).isNull();
    }

    @Test
    void findLatestReturnsTheMostRecentByDateAmongThreeSnapshots() {
        save("GLD", LocalDate.of(2026, 4, 1), "4.0");
        save("GLD", LocalDate.of(2026, 4, 3), "6.0");
        save("GLD", LocalDate.of(2026, 4, 2), "5.0");
        entityManager.flush();
        entityManager.clear();

        SignalSnapshot latest = adapter.findLatest(Ticker.of("GLD")).orElseThrow();

        assertThat(latest.snapshotDate()).isEqualTo(LocalDate.of(2026, 4, 3));
        assertThat(latest.scoreOf(SignalSource.TECHNICAL).orElseThrow()).isEqualByComparingTo("6.0");
    }

    @Test
    void findLatestOnATickerNeverStoredReturnsEmptyOptional() {
        Optional<SignalSnapshot> found = adapter.findLatest(Ticker.of("MSFT"));

        assertThat(found).isEmpty();
    }

    @Test
    void findBetweenIncludesBothBoundaryDatesAndExcludesOneDayOutsideEachEnd() {
        save("TSLA", LocalDate.of(2026, 5, 9), "3.0");
        save("TSLA", LocalDate.of(2026, 5, 10), "4.0");
        save("TSLA", LocalDate.of(2026, 5, 15), "5.0");
        save("TSLA", LocalDate.of(2026, 5, 20), "6.0");
        save("TSLA", LocalDate.of(2026, 5, 21), "7.0");
        entityManager.flush();
        entityManager.clear();

        List<SignalSnapshot> found = adapter.findBetween(
                Ticker.of("TSLA"), LocalDate.of(2026, 5, 10), LocalDate.of(2026, 5, 20));

        assertThat(found).extracting(SignalSnapshot::snapshotDate).containsExactly(
                LocalDate.of(2026, 5, 10), LocalDate.of(2026, 5, 15), LocalDate.of(2026, 5, 20));
    }

    @Test
    void saveAllOfThreeSnapshotsReturnsThreeAndAllAreReadableAfterwards() {
        List<SignalSnapshot> toSave = List.of(
                snapshot("QQQ", LocalDate.of(2026, 6, 1), "5.0"),
                snapshot("VOO", LocalDate.of(2026, 6, 1), "5.5"),
                snapshot("GLD", LocalDate.of(2026, 6, 1), "6.5"));

        List<SignalSnapshot> saved = adapter.saveAll(toSave);
        entityManager.flush();
        entityManager.clear();

        assertThat(saved).hasSize(3);
        assertThat(adapter.findLatest(Ticker.of("QQQ"))).isPresent();
        assertThat(adapter.findLatest(Ticker.of("VOO"))).isPresent();
        assertThat(adapter.findLatest(Ticker.of("GLD"))).isPresent();
    }

    @Test
    void savingTwiceForTheSameInstrumentAndDateUpdatesRatherThanThrowingAndLeavesExactlyOneRow() {
        LocalDate date = LocalDate.of(2026, 7, 1);
        save("JPM", date, "4.0");
        entityManager.flush();
        entityManager.clear();

        save("JPM", date, "9.0");
        entityManager.flush();
        entityManager.clear();

        SignalSnapshot latest = adapter.findLatest(Ticker.of("JPM")).orElseThrow();
        assertThat(latest.scoreOf(SignalSource.TECHNICAL).orElseThrow()).isEqualByComparingTo("9.0");

        Long instrumentId = instrumentRepository.findByTicker("JPM").orElseThrow().getId();
        Long rowCount = ((Number) entityManager.getEntityManager()
                .createNativeQuery("select count(*) from signal_snapshots "
                        + "where instrument_id = ?1 and snapshot_date = ?2")
                .setParameter(1, instrumentId)
                .setParameter(2, date)
                .getSingleResult())
                .longValue();
        assertThat(rowCount).isEqualTo(1L);
    }

    @Test
    void savingASnapshotForATickerAbsentFromTheCatalogRaisesUnknownCatalogEntryExceptionAndInsertsNoRow() {
        SignalSnapshot toSave = snapshot("NOPE", LocalDate.of(2026, 8, 1), "5.0");

        assertThatThrownBy(() -> adapter.save(toSave))
                .isInstanceOf(UnknownCatalogEntryException.class);

        Long count = entityManager.getEntityManager()
                .createQuery("select count(s) from SignalSnapshotData s where s.instrument.ticker = 'NOPE'",
                        Long.class)
                .getSingleResult();
        assertThat(count).isZero();
    }

    private SignalSnapshot save(String ticker, LocalDate date, String technicalScore) {
        return adapter.save(snapshot(ticker, date, technicalScore));
    }

    private SignalSnapshot snapshot(String ticker, LocalDate date, String technicalScore) {
        return SignalSnapshot.builder()
                .ticker(Ticker.of(ticker))
                .snapshotDate(date)
                .score(SignalSource.TECHNICAL, new BigDecimal(technicalScore))
                .build();
    }
}
