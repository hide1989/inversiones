package co.com.david.inversiones.usecase.transaction;

import co.com.david.inversiones.model.Position;
import co.com.david.inversiones.model.Transaction;
import co.com.david.inversiones.model.exception.TransactionNotFoundException;
import co.com.david.inversiones.model.gateways.TransactionRepository;

import java.util.List;
import java.util.Objects;

/**
 * Removes a trade, refusing the deletion when the remaining history would no longer be valid.
 *
 * <p>Deleting a purchase that a later sale depends on would leave a negative position, so the
 * resulting history is replayed before anything is removed.
 */
public class DeleteTransactionUseCase {

    private final TransactionRepository transactionRepository;

    public DeleteTransactionUseCase(TransactionRepository transactionRepository) {
        this.transactionRepository = transactionRepository;
    }

    public void execute(Long id) {
        Transaction target = transactionRepository.findById(id)
                .orElseThrow(() -> new TransactionNotFoundException(id));

        List<Transaction> remaining = transactionRepository
                .findByTickerAndBroker(target.ticker(), target.brokerName())
                .stream()
                .filter(transaction -> !Objects.equals(transaction.id(), id))
                .toList();

        Position.fromTransactions(target.ticker(), target.brokerName(), remaining);

        transactionRepository.deleteById(id);
    }
}
