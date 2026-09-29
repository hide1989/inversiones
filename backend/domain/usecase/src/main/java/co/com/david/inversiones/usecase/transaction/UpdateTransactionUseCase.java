package co.com.david.inversiones.usecase.transaction;

import co.com.david.inversiones.model.Position;
import co.com.david.inversiones.model.Transaction;
import co.com.david.inversiones.model.exception.TransactionNotFoundException;
import co.com.david.inversiones.model.gateways.TransactionRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Replaces a trade, validating the resulting history the same way a deletion does. */
public class UpdateTransactionUseCase {

    private final TransactionRepository transactionRepository;

    public UpdateTransactionUseCase(TransactionRepository transactionRepository) {
        this.transactionRepository = transactionRepository;
    }

    public Transaction execute(Long id, Transaction replacement) {
        Transaction existing = transactionRepository.findById(id)
                .orElseThrow(() -> new TransactionNotFoundException(id));

        List<Transaction> history = new ArrayList<>(transactionRepository
                .findByTickerAndBroker(existing.ticker(), existing.brokerName())
                .stream()
                .filter(transaction -> !Objects.equals(transaction.id(), id))
                .toList());
        history.add(replacement);

        Position.fromTransactions(replacement.ticker(), replacement.brokerName(), history);

        return transactionRepository.save(replacement);
    }
}
