package co.com.david.inversiones.model.gateways;

import co.com.david.inversiones.model.Transaction;
import co.com.david.inversiones.model.Ticker;

import java.util.List;
import java.util.Optional;

/** Persistence port for {@link Transaction} records. */
public interface TransactionRepository {

    /** Returns every stored transaction, in no guaranteed order. */
    List<Transaction> findAll();

    /** Returns every transaction for that ticker and broker pair, in no guaranteed order. */
    List<Transaction> findByTickerAndBroker(Ticker ticker, String brokerName);

    /** Returns the transaction with this id, if one exists. */
    Optional<Transaction> findById(Long id);

    /** Persists the given transaction, returning the stored state. */
    Transaction save(Transaction transaction);

    /** Removes the transaction with this id. */
    void deleteById(Long id);
}
