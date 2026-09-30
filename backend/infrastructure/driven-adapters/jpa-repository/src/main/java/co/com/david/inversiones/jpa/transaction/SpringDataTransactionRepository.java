package co.com.david.inversiones.jpa.transaction;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/** Spring Data repository for {@link TransactionData}. */
public interface SpringDataTransactionRepository extends JpaRepository<TransactionData, Long> {

    /**
     * Derived query joining both foreign keys directly in SQL (backed by the
     * {@code transactions_instrument_broker_traded_idx} index), rather than loading every
     * transaction and filtering in Java. This is the hottest read path in the application, so it
     * must not scale with the table's full size.
     *
     * <p>{@code @EntityGraph} fetches {@code instrument} and {@code broker} — both
     * {@code FetchType.LAZY} on {@link TransactionData} — in the same SQL statement via a JOIN,
     * rather than leaving them as lazy proxies: {@link TransactionMapper#toDomain} reads both on
     * every row, so without this a transaction-wrapped call still issues one extra query per row
     * (an N+1) even though it no longer throws {@code LazyInitializationException}. A transaction
     * keeps the session open long enough for a lazy load to succeed; it does not stop one from
     * happening.
     */
    @EntityGraph(attributePaths = {"instrument", "broker"})
    List<TransactionData> findByInstrumentTickerAndBrokerName(String ticker, String brokerName);

    /**
     * Overrides {@link JpaRepository#findAll()} purely to attach the same {@code @EntityGraph}: the
     * inherited method has no hook for one, and {@link TransactionMapper#toDomain} needs
     * {@code instrument} and {@code broker} loaded for every row this returns.
     */
    @Override
    @EntityGraph(attributePaths = {"instrument", "broker"})
    List<TransactionData> findAll();
}
