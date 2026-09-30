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
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * JPA-backed implementation of {@link TransactionRepository}.
 *
 * <p>No {@link TransactionData} is ever returned from a public method here; every result passes
 * through {@link TransactionMapper} before it leaves this class, so nothing in {@code domain/}
 * needs to know a JPA entity exists.
 *
 * <p>{@code instrument} and {@code broker} on {@link TransactionData} are {@code FetchType.LAZY}
 * (see its Javadoc). With {@code spring.jpa.open-in-view: false}, a derived Spring Data repository
 * method such as {@code findById} runs in its own short-lived transaction that closes the moment
 * the method returns — before control ever reaches {@link TransactionMapper#toDomain}, which reads
 * {@code data.getInstrument().getTicker()} through that lazy proxy. Calling it from outside any
 * transaction therefore throws {@code LazyInitializationException}, invisible to a unit test or a
 * {@code @DataJpaTest} that wraps its whole test method in one transaction, and only surfaced by an
 * acceptance test driving a real HTTP request end to end.
 *
 * <p>{@code @Transactional(readOnly = true)} on every read method, and a plain
 * {@code @Transactional} on {@link #save}, keeps one session open across both the repository call
 * and the mapping that follows it — but a transaction only keeps a lazy proxy alive long enough to
 * be loaded; it does not stop the load from happening once per row. {@link #findAll} and
 * {@link #findByTickerAndBroker}, which can return many rows, fetch {@code instrument} and
 * {@code broker} eagerly via {@code @EntityGraph} on {@link SpringDataTransactionRepository} instead
 * — one SQL statement per call regardless of row count. {@link #findById} returns at most one row,
 * so the extra per-row query an N+1 describes cannot occur there; the transaction alone is
 * sufficient and no entity graph was added.
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
    @Transactional(readOnly = true)
    public List<Transaction> findAll() {
        return transactionRepository.findAll().stream().map(TransactionMapper::toDomain).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<Transaction> findByTickerAndBroker(Ticker ticker, String brokerName) {
        return transactionRepository.findByInstrumentTickerAndBrokerName(ticker.symbol(), brokerName)
                .stream()
                .map(TransactionMapper::toDomain)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Transaction> findById(Long id) {
        return transactionRepository.findById(id).map(TransactionMapper::toDomain);
    }

    /**
     * @throws UnknownCatalogEntryException when the transaction's ticker or broker name is not in the
     *     catalog, so that a missing reference surfaces as this domain exception rather than as a
     *     foreign-key constraint violation bubbling up as an unhandled 500.
     */
    @Override
    @Transactional
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
