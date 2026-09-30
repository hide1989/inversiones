package co.com.david.inversiones.jpa.transaction;

import co.com.david.inversiones.model.Currency;
import co.com.david.inversiones.model.Money;
import co.com.david.inversiones.model.Quantity;
import co.com.david.inversiones.model.Ticker;
import co.com.david.inversiones.model.Transaction;
import co.com.david.inversiones.model.TransactionType;
import co.com.david.inversiones.model.exception.UnknownCatalogEntryException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
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
 * Integration tests for {@link TransactionRepositoryAdapter} against a real PostgreSQL instance,
 * with Flyway applying the actual migrations (schema and the seeded portfolio) so the tests exercise
 * the same catalog the running application starts with.
 */
@Testcontainers
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({TransactionRepositoryAdapter.class,
        co.com.david.inversiones.jpa.instrument.InstrumentRepositoryAdapter.class})
// Enabled only so findAllIssuesAConstantNumberOfQueriesRegardlessOfRowCount can assert a hard
// SQL-statement-count bound below, proving @EntityGraph actually prevents an N+1 rather than just
// not throwing.
@TestPropertySource(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
class TransactionRepositoryAdapterTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired
    private TransactionRepositoryAdapter adapter;

    @Autowired
    private co.com.david.inversiones.jpa.instrument.InstrumentRepositoryAdapter instrumentRepositoryAdapter;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    void aSavedUsdTransactionRoundTripsWithScaleAndCurrencyPreserved() {
        Transaction toSave = Transaction.builder()
                .ticker(Ticker.of("QQQ"))
                .brokerName("Hapi")
                .type(TransactionType.BUY)
                .quantity(Quantity.of("3"))
                .unitPrice(Money.usd("101.5"))
                .fees(Money.usd("1.25"))
                .tradedAt(LocalDate.of(2026, 1, 15))
                .build();

        Transaction saved = adapter.save(toSave);
        entityManager.flush();
        entityManager.clear();

        assertThat(saved.id()).isNotNull();
        Transaction reloaded = adapter.findById(saved.id()).orElseThrow();

        assertThat(reloaded.ticker()).isEqualTo(Ticker.of("QQQ"));
        assertThat(reloaded.brokerName()).isEqualTo("Hapi");
        assertThat(reloaded.type()).isEqualTo(TransactionType.BUY);
        assertThat(reloaded.quantity().value()).isEqualByComparingTo(new BigDecimal("3"));
        assertThat(reloaded.quantity().value().scale()).isEqualTo(Quantity.SCALE);
        assertThat(reloaded.unitPrice()).isEqualTo(Money.usd("101.5"));
        assertThat(reloaded.unitPrice().currency()).isEqualTo(Currency.USD);
        assertThat(reloaded.unitPrice().amount().scale()).isEqualTo(Money.SCALE);
        assertThat(reloaded.fees()).isEqualTo(Money.usd("1.25"));
        assertThat(reloaded.tradedAt()).isEqualTo(LocalDate.of(2026, 1, 15));
    }

    @Test
    void aSavedCopTransactionRoundTripsAsCopNotSilentlyAsUsd() {
        Transaction toSave = Transaction.builder()
                .ticker(Ticker.of("TERPEL"))
                .brokerName("Trii")
                .type(TransactionType.BUY)
                .quantity(Quantity.of("10"))
                .unitPrice(Money.cop("16500.1234567891"))
                .tradedAt(LocalDate.of(2026, 2, 1))
                .build();

        Transaction saved = adapter.save(toSave);
        entityManager.flush();
        entityManager.clear();

        Transaction reloaded = adapter.findById(saved.id()).orElseThrow();

        assertThat(reloaded.unitPrice().currency()).isEqualTo(Currency.COP);
        assertThat(reloaded.unitPrice().amount()).isEqualByComparingTo("16500.1234567891");
        assertThat(reloaded.fees().currency()).isEqualTo(Currency.COP);
    }

    @Test
    void findByTickerAndBrokerReturnsOnlyTheMatchingPair() {
        // GLD is only seeded at Hapi, never at ARQ, so GLD@ARQ is a combination this test fully
        // controls. Fixture: GLD at both Hapi (from the seed) and ARQ (inserted here) -- same
        // ticker, two brokers -- plus IBIT at ARQ (different ticker, same broker, also inserted
        // here and also otherwise absent at ARQ). A query ignoring either half of the key would
        // wrongly include one of these.
        save("GLD", "ARQ", "1", Money.usd("100"));
        save("IBIT", "ARQ", "2", Money.usd("100"));
        entityManager.flush();
        entityManager.clear();

        List<Transaction> found = adapter.findByTickerAndBroker(Ticker.of("GLD"), "ARQ");

        assertThat(found).hasSize(1);
        assertThat(found.getFirst().ticker()).isEqualTo(Ticker.of("GLD"));
        assertThat(found.getFirst().brokerName()).isEqualTo("ARQ");
        assertThat(found.getFirst().quantity().value()).isEqualByComparingTo("1");
    }

    @Test
    void findByIdOnAnUnknownIdReturnsEmptyOptional() {
        Optional<Transaction> found = adapter.findById(999_999L);

        assertThat(found).isEmpty();
    }

    @Test
    void deleteByIdRemovesExactlyOneRowAndLeavesTheOthers() {
        Transaction first = adapter.save(build("QQQ", "Hapi", "1", Money.usd("100")));
        Transaction second = adapter.save(build("VOO", "Hapi", "2", Money.usd("100")));
        entityManager.flush();

        adapter.deleteById(first.id());
        entityManager.flush();
        entityManager.clear();

        assertThat(adapter.findById(first.id())).isEmpty();
        assertThat(adapter.findById(second.id())).isPresent();
    }

    @Test
    void findAllReturnsAtLeastTwentySixSeededRows() {
        List<Transaction> all = adapter.findAll();

        assertThat(all.size()).isGreaterThanOrEqualTo(26);
    }

    /**
     * Proves {@link SpringDataTransactionRepository#findAll()}'s {@code @EntityGraph} actually
     * prevents an N+1, rather than merely not throwing {@code LazyInitializationException}: on the
     * 26 seeded transactions across 20 instruments, a single statement fetches every row together
     * with its {@code instrument} and {@code broker} association. Before the entity graph was
     * added, {@link TransactionMapper#toDomain} reading each row's lazy proxies issued one extra
     * SELECT per row instead — a count that grows with the table, not a fixed one. A test that only
     * asserts "it did not throw" cannot tell the two apart; only counting statements can.
     */
    @Test
    void findAllIssuesAConstantNumberOfQueriesRegardlessOfRowCount() {
        entityManager.flush();
        entityManager.clear();
        Statistics statistics = entityManager.getEntityManager()
                .getEntityManagerFactory().unwrap(SessionFactory.class).getStatistics();
        statistics.setStatisticsEnabled(true);
        statistics.clear();

        List<Transaction> all = adapter.findAll();

        assertThat(all.size()).isGreaterThanOrEqualTo(26);
        assertThat(statistics.getPrepareStatementCount())
                .as("findAll() over %d rows should issue one JOIN FETCH statement, not one per row",
                        all.size())
                .isLessThanOrEqualTo(2);
    }

    @Test
    void savingATransactionForATickerAbsentFromTheCatalogRaisesUnknownCatalogEntryExceptionAndInsertsNoRow() {
        Transaction toSave = build("NOPE", "Hapi", "1", Money.usd("10"));

        assertThatThrownBy(() -> adapter.save(toSave))
                .isInstanceOf(UnknownCatalogEntryException.class);

        Long count = entityManager.getEntityManager()
                .createQuery("select count(t) from TransactionData t where t.instrument.ticker = 'NOPE'", Long.class)
                .getSingleResult();
        assertThat(count).isZero();
    }

    @Test
    void savingATransactionForABrokerAbsentFromTheCatalogRaisesUnknownCatalogEntryException() {
        Transaction toSave = build("QQQ", "NoSuchBroker", "1", Money.usd("10"));

        assertThatThrownBy(() -> adapter.save(toSave))
                .isInstanceOf(UnknownCatalogEntryException.class);
    }

    @Test
    void aTerpelTransactionFromTheSeedHasCopCurrencyAndItsInstrumentIsListedOnTheBvc() {
        List<Transaction> terpelTransactions = adapter.findByTickerAndBroker(Ticker.of("TERPEL"), "Trii");

        assertThat(terpelTransactions).isNotEmpty();
        assertThat(terpelTransactions.getFirst().unitPrice().currency()).isEqualTo(Currency.COP);

        var terpelInstrument = instrumentRepositoryAdapter.findByTicker(Ticker.of("TERPEL")).orElseThrow();
        assertThat(terpelInstrument.exchange()).isEqualTo("BVC");
    }

    private Transaction save(String ticker, String broker, String quantity, Money price) {
        return adapter.save(build(ticker, broker, quantity, price));
    }

    private Transaction build(String ticker, String broker, String quantity, Money price) {
        return Transaction.builder()
                .ticker(Ticker.of(ticker))
                .brokerName(broker)
                .type(TransactionType.BUY)
                .quantity(Quantity.of(quantity))
                .unitPrice(price)
                .tradedAt(LocalDate.of(2026, 3, 1))
                .build();
    }
}
