package co.com.david.inversiones.api.dto;

import co.com.david.inversiones.model.Transaction;

/** Wire representation of a stored {@link Transaction}. */
public record TransactionResponse(
        Long id,
        String ticker,
        String brokerName,
        String type,
        String quantity,
        MoneyDto unitPrice,
        MoneyDto fees,
        String tradedAt,
        MoneyDto grossAmount,
        MoneyDto netAmount) {

    public static TransactionResponse from(Transaction transaction) {
        return new TransactionResponse(
                transaction.id(),
                transaction.ticker().symbol(),
                transaction.brokerName(),
                transaction.type().name(),
                transaction.quantity().value().toPlainString(),
                MoneyDto.from(transaction.unitPrice()),
                MoneyDto.from(transaction.fees()),
                transaction.tradedAt().toString(),
                MoneyDto.from(transaction.grossAmount()),
                MoneyDto.from(transaction.netAmount()));
    }
}
