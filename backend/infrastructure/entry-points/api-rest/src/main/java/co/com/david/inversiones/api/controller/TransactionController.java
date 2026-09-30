package co.com.david.inversiones.api.controller;

import co.com.david.inversiones.api.dto.TransactionRequest;
import co.com.david.inversiones.api.dto.TransactionResponse;
import co.com.david.inversiones.model.Ticker;
import co.com.david.inversiones.usecase.transaction.DeleteTransactionUseCase;
import co.com.david.inversiones.usecase.transaction.ListTransactionsUseCase;
import co.com.david.inversiones.usecase.transaction.RegisterTransactionUseCase;
import co.com.david.inversiones.usecase.transaction.TransactionFilter;
import co.com.david.inversiones.usecase.transaction.UpdateTransactionUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/** CRUD over recorded trades, with optional filtering on listing. */
@RestController
@RequestMapping(value = "/api/v1/transactions", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Transactions")
@SecurityRequirement(name = "bearerAuth")
public class TransactionController {

    private final ListTransactionsUseCase listTransactions;
    private final RegisterTransactionUseCase registerTransaction;
    private final UpdateTransactionUseCase updateTransaction;
    private final DeleteTransactionUseCase deleteTransaction;

    public TransactionController(ListTransactionsUseCase listTransactions,
                                 RegisterTransactionUseCase registerTransaction,
                                 UpdateTransactionUseCase updateTransaction,
                                 DeleteTransactionUseCase deleteTransaction) {
        this.listTransactions = listTransactions;
        this.registerTransaction = registerTransaction;
        this.updateTransaction = updateTransaction;
        this.deleteTransaction = deleteTransaction;
    }

    @Operation(summary = "List trades, most recent first, with optional filters")
    @GetMapping
    public List<TransactionResponse> list(
            @RequestParam(name = "ticker", required = false) String ticker,
            @RequestParam(name = "brokerName", required = false) String brokerName,
            @RequestParam(name = "from", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(name = "to", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        TransactionFilter filter = new TransactionFilter(
                ticker == null ? null : Ticker.of(ticker), brokerName, from, to);
        return listTransactions.execute(filter).stream().map(TransactionResponse::from).toList();
    }

    @Operation(summary = "Register a buy or sell")
    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping
    public TransactionResponse register(@Valid @RequestBody TransactionRequest request) {
        return TransactionResponse.from(registerTransaction.execute(request.toDomain()));
    }

    @Operation(summary = "Edit an existing trade; moving it to a different ticker or broker is rejected")
    @PutMapping("/{id}")
    public TransactionResponse update(@PathVariable(name = "id") Long id, @Valid @RequestBody TransactionRequest request) {
        return TransactionResponse.from(updateTransaction.execute(id, request.toDomain(id)));
    }

    @Operation(summary = "Delete a trade, refusing it when the remaining history would go negative")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @DeleteMapping("/{id}")
    public void delete(@PathVariable(name = "id") Long id) {
        deleteTransaction.execute(id);
    }
}
