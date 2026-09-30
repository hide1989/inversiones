package co.com.david.inversiones.usecase.transaction;

import co.com.david.inversiones.model.Position;
import co.com.david.inversiones.model.Transaction;
import co.com.david.inversiones.model.exception.InvalidTradeDateException;
import co.com.david.inversiones.model.exception.InvalidTransactionUpdateException;
import co.com.david.inversiones.model.exception.TransactionNotFoundException;
import co.com.david.inversiones.model.gateways.TimeProvider;
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
 *
 * <p>An edit is also rejected when it would move {@code tradedAt} into the future, for the same
 * reason and with the same exception as {@link RegisterTransactionUseCase}: an edit is an ordinary
 * way to reach the exact state the create path already refuses, so leaving this path unchecked
 * would just let a future-dated trade in through a different door.
 */
public class UpdateTransactionUseCase {

    private final TransactionRepository transactionRepository;
    private final TimeProvider timeProvider;

    public UpdateTransactionUseCase(TransactionRepository transactionRepository, TimeProvider timeProvider) {
        this.transactionRepository = transactionRepository;
        this.timeProvider = timeProvider;
    }

    public Transaction execute(Long id, Transaction replacement) {
        Transaction existing = transactionRepository.findById(id)
                .orElseThrow(() -> new TransactionNotFoundException(id));

        if (!existing.ticker().equals(replacement.ticker())
                || !existing.brokerName().equals(replacement.brokerName())) {
            throw new InvalidTransactionUpdateException(existing, replacement);
        }

        if (replacement.tradedAt().isAfter(timeProvider.today())) {
            throw new InvalidTradeDateException(replacement.tradedAt(), timeProvider.today());
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
