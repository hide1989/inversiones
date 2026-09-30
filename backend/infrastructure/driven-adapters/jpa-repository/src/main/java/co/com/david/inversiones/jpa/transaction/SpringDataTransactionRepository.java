package co.com.david.inversiones.jpa.transaction;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/** Spring Data repository for {@link TransactionData}. */
public interface SpringDataTransactionRepository extends JpaRepository<TransactionData, Long> {

    /**
     * Derived query joining both foreign keys directly in SQL (backed by the
     * {@code transactions_instrument_broker_traded_idx} index), rather than loading every
     * transaction and filtering in Java. This is the hottest read path in the application, so it
     * must not scale with the table's full size.
     */
    List<TransactionData> findByInstrumentTickerAndBrokerName(String ticker, String brokerName);
}
