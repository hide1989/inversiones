package co.com.david.inversiones.jpa.transaction;

import co.com.david.inversiones.jpa.broker.BrokerData;
import co.com.david.inversiones.jpa.broker.SpringDataBrokerRepository;
import co.com.david.inversiones.jpa.instrument.InstrumentData;
import co.com.david.inversiones.jpa.instrument.SpringDataInstrumentRepository;
import co.com.david.inversiones.model.Ticker;
import co.com.david.inversiones.model.Transaction;
import co.com.david.inversiones.model.exception.UnknownCatalogEntryException;
import co.com.david.inversiones.model.gateways.TransactionRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * JPA-backed implementation of {@link TransactionRepository}.
 *
 * <p>No {@link TransactionData} is ever returned from a public method here; every result passes
 * through {@link TransactionMapper} before it leaves this class, so nothing in {@code domain/}
 * needs to know a JPA entity exists.
 */
@Repository
public class TransactionRepositoryAdapter implements TransactionRepository {

    private final SpringDataTransactionRepository transactionRepository;
    private final SpringDataInstrumentRepository instrumentRepository;
    private final SpringDataBrokerRepository brokerRepository;

    public TransactionRepositoryAdapter(SpringDataTransactionRepository transactionRepository,
            SpringDataInstrumentRepository instrumentRepository,
            SpringDataBrokerRepository brokerRepository) {
        this.transactionRepository = transactionRepository;
        this.instrumentRepository = instrumentRepository;
        this.brokerRepository = brokerRepository;
    }

    @Override
    public List<Transaction> findAll() {
        return transactionRepository.findAll().stream().map(TransactionMapper::toDomain).toList();
    }

    @Override
    public List<Transaction> findByTickerAndBroker(Ticker ticker, String brokerName) {
        return transactionRepository.findByInstrumentTickerAndBrokerName(ticker.symbol(), brokerName)
                .stream()
                .map(TransactionMapper::toDomain)
                .toList();
    }

    @Override
    public Optional<Transaction> findById(Long id) {
        return transactionRepository.findById(id).map(TransactionMapper::toDomain);
    }

    /**
     * @throws UnknownCatalogEntryException when the transaction's ticker or broker name is not in the
     *     catalog, so that a missing reference surfaces as this domain exception rather than as a
     *     foreign-key constraint violation bubbling up as an unhandled 500.
     */
    @Override
    public Transaction save(Transaction transaction) {
        InstrumentData instrument = instrumentRepository.findByTicker(transaction.ticker().symbol())
                .orElseThrow(() -> UnknownCatalogEntryException.forInstrument(transaction.ticker()));
        BrokerData broker = brokerRepository.findByName(transaction.brokerName())
                .orElseThrow(() -> UnknownCatalogEntryException.forBroker(transaction.brokerName()));

        TransactionData saved = transactionRepository.save(
                TransactionMapper.toData(transaction, instrument, broker));
        return TransactionMapper.toDomain(saved);
    }

    @Override
    public void deleteById(Long id) {
        transactionRepository.deleteById(id);
    }
}
