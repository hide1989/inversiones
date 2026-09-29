package co.com.david.inversiones.usecase.transaction;

import co.com.david.inversiones.model.Ticker;

import java.time.LocalDate;

/**
 * Optional filters for listing transactions. A null field means the field is not filtered on.
 *
 * @param ticker     only transactions for this instrument
 * @param brokerName only transactions at this broker
 * @param from       only transactions traded on or after this date
 * @param to         only transactions traded on or before this date
 */
public record TransactionFilter(Ticker ticker, String brokerName, LocalDate from, LocalDate to) {

    public static TransactionFilter none() {
        return new TransactionFilter(null, null, null, null);
    }

    boolean matches(co.com.david.inversiones.model.Transaction transaction) {
        if (ticker != null && !ticker.equals(transaction.ticker())) {
            return false;
        }
        if (brokerName != null && !brokerName.equals(transaction.brokerName())) {
            return false;
        }
        if (from != null && transaction.tradedAt().isBefore(from)) {
            return false;
        }
        return to == null || !transaction.tradedAt().isAfter(to);
    }
}
