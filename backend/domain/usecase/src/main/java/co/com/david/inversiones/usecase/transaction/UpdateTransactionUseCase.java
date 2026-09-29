package co.com.david.inversiones.usecase.transaction;

import co.com.david.inversiones.model.Position;
import co.com.david.inversiones.model.Transaction;
import co.com.david.inversiones.model.exception.InvalidTransactionUpdateException;
import co.com.david.inversiones.model.exception.TransactionNotFoundException;
import co.com.david.inversiones.model.gateways.TransactionRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Replaces a trade, validating the resulting history the same way a deletion does.
 *
 * <p>The instrument and broker of a trade are immutable. {@link Position#fromTransactions} replays
 * whatever list it is handed without filtering by ticker or broker, so validating a replacement that
 * moved to a different pair against the old pair's remaining history would replay a meaningless
 * mixture and prove nothing about either position. Moving a trade to another instrument is a
 * deletion plus a new trade, not an edit, so it is rejected.
 */
public class UpdateTransactionUseCase {

    private final TransactionRepository transactionRepository;

    public UpdateTransactionUseCase(TransactionRepository transactionRepository) {
        this.transactionRepository = transactionRepository;
    }

    public Transaction execute(Long id, Transaction replacement) {
        Transaction existing = transactionRepository.findById(id)
                .orElseThrow(() -> new TransactionNotFoundException(id));

        if (!existing.ticker().equals(replacement.ticker())
                || !existing.brokerName().equals(replacement.brokerName())) {
            throw new InvalidTransactionUpdateException(existing, replacement);
        }

        List<Transaction> history = new ArrayList<>(transactionRepository
                .findByTickerAndBroker(existing.ticker(), existing.brokerName())
                .stream()
                .filter(transaction -> !Objects.equals(transaction.id(), id))
                .toList());
        history.add(replacement);

        Position.fromTransactions(existing.ticker(), existing.brokerName(), history);

        return transactionRepository.save(replacement);
    }
}
