package co.com.david.inversiones.usecase.transaction;

import co.com.david.inversiones.model.Position;
import co.com.david.inversiones.model.Transaction;
import co.com.david.inversiones.model.TransactionType;
import co.com.david.inversiones.model.exception.InvalidTradeDateException;
import co.com.david.inversiones.model.exception.UnknownCatalogEntryException;
import co.com.david.inversiones.model.gateways.InstrumentRepository;
import co.com.david.inversiones.model.gateways.TimeProvider;
import co.com.david.inversiones.model.gateways.TransactionRepository;

import java.util.ArrayList;
import java.util.List;

/**
 * Records a trade after checking that it is consistent with the existing history.
 *
 * <p>A sale is validated by replaying the whole history for that ticker and broker with the candidate
 * included, which reuses the weighted average cost rules of {@link Position} instead of duplicating
 * them here.
 */
public class RegisterTransactionUseCase {

    private final TransactionRepository transactionRepository;
    private final InstrumentRepository instrumentRepository;
    private final TimeProvider timeProvider;

    public RegisterTransactionUseCase(TransactionRepository transactionRepository,
                                      InstrumentRepository instrumentRepository,
                                      TimeProvider timeProvider) {
        this.transactionRepository = transactionRepository;
        this.instrumentRepository = instrumentRepository;
        this.timeProvider = timeProvider;
    }

    public Transaction execute(Transaction candidate) {
        instrumentRepository.findByTicker(candidate.ticker())
                .orElseThrow(() -> UnknownCatalogEntryException.forInstrument(candidate.ticker()));

        if (candidate.tradedAt().isAfter(timeProvider.today())) {
            throw new InvalidTradeDateException(candidate.tradedAt(), timeProvider.today());
        }

        if (candidate.type() == TransactionType.SELL) {
            List<Transaction> history = new ArrayList<>(
                    transactionRepository.findByTickerAndBroker(candidate.ticker(), candidate.brokerName()));
            history.add(candidate);
            Position.fromTransactions(candidate.ticker(), candidate.brokerName(), history);
        }

        return transactionRepository.save(candidate);
    }
}
