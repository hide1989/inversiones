package co.com.david.inversiones.jpa.transaction;

import co.com.david.inversiones.jpa.broker.BrokerData;
import co.com.david.inversiones.jpa.instrument.InstrumentData;
import co.com.david.inversiones.model.Money;
import co.com.david.inversiones.model.Quantity;
import co.com.david.inversiones.model.Ticker;
import co.com.david.inversiones.model.Transaction;

/**
 * Hand-written mapping between {@link TransactionData} and {@link Transaction}.
 *
 * <p>This cannot be a reflection-based, field-name-to-field-name mapper. The domain's
 * {@link Money} is {@code (amount, currency)}, but the table stores those as two independent
 * columns ({@code unit_price}/{@code fees} and {@code currency}); a generic mapper has no notion
 * that they must travel together, and the failure mode of getting that wrong is silent — an amount
 * that quietly loses its currency, which is exactly the Critical defect this project already paid
 * for once in the domain layer. Writing the conversion by hand means the currency is attached
 * explicitly on every read and write, in code that is visible and reviewable rather than inferred.
 */
final class TransactionMapper {

    private TransactionMapper() {
    }

    static Transaction toDomain(TransactionData data) {
        Money unitPrice = Money.of(data.getUnitPrice(), data.getCurrency());
        Money fees = Money.of(data.getFees(), data.getCurrency());
        return Transaction.builder()
                .id(data.getId())
                .ticker(Ticker.of(data.getInstrument().getTicker()))
                .brokerName(data.getBroker().getName())
                .type(data.getType())
                .quantity(Quantity.of(data.getQuantity()))
                .unitPrice(unitPrice)
                .fees(fees)
                .tradedAt(data.getTradedAt())
                .build();
    }

    static TransactionData toData(Transaction transaction, InstrumentData instrument, BrokerData broker) {
        return TransactionData.builder()
                .id(transaction.id())
                .instrument(instrument)
                .broker(broker)
                .type(transaction.type())
                .quantity(transaction.quantity().value())
                .unitPrice(transaction.unitPrice().amount())
                .fees(transaction.fees().amount())
                .currency(transaction.unitPrice().currency())
                .tradedAt(transaction.tradedAt())
                .build();
    }
}
