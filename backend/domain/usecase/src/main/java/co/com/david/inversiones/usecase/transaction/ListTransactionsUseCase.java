package co.com.david.inversiones.usecase.transaction;

import co.com.david.inversiones.model.Transaction;
import co.com.david.inversiones.model.gateways.TransactionRepository;

import java.util.Comparator;
import java.util.List;

/** Lists trades matching a filter, most recent first. */
public class ListTransactionsUseCase {

    private final TransactionRepository transactionRepository;

    public ListTransactionsUseCase(TransactionRepository transactionRepository) {
        this.transactionRepository = transactionRepository;
    }

    public List<Transaction> execute(TransactionFilter filter) {
        return transactionRepository.findAll().stream()
                .filter(filter::matches)
                .sorted(Comparator.comparing(Transaction::tradedAt).reversed())
                .toList();
    }
}
