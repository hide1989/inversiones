package co.com.david.inversiones.model;

import co.com.david.inversiones.model.exception.InsufficientPositionException;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * A holding of one instrument at one broker, derived from its transactions.
 *
 * <p>Uses weighted average cost accounting: a purchase raises the average cost proportionally to the
 * quantity added, while a sale removes shares at the prevailing average cost and turns the
 * difference against the sale proceeds into realized profit, leaving the average cost untouched.
 */
public final class Position {

    private final Ticker ticker;
    private final String brokerName;
    private final Quantity quantity;
    private final Money totalCost;
    private final Money averageCost;
    private final Money realizedPnl;

    private Position(Builder builder) {
        this.ticker = Objects.requireNonNull(builder.ticker, "ticker must not be null");
        this.brokerName = Objects.requireNonNull(builder.brokerName, "brokerName must not be null");
        this.quantity = Objects.requireNonNull(builder.quantity, "quantity must not be null");
        this.totalCost = Objects.requireNonNull(builder.totalCost, "totalCost must not be null");
        this.averageCost = Objects.requireNonNull(builder.averageCost, "averageCost must not be null");
        this.realizedPnl = Objects.requireNonNull(builder.realizedPnl, "realizedPnl must not be null");
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Replays the transactions in trade-date order to derive the current holding.
     *
     * @throws InsufficientPositionException when a sale exceeds the quantity held at that point
     */
    public static Position fromTransactions(Ticker ticker, String brokerName, List<Transaction> transactions) {
        Currency currency = transactions.isEmpty()
                ? Currency.USD
                : transactions.getFirst().unitPrice().currency();

        Quantity quantity = Quantity.zero();
        Money totalCost = Money.zero(currency);
        Money realizedPnl = Money.zero(currency);

        List<Transaction> ordered = transactions.stream()
                .sorted(Comparator.comparing(Transaction::tradedAt))
                .toList();

        for (Transaction transaction : ordered) {
            if (transaction.type() == TransactionType.BUY) {
                quantity = quantity.add(transaction.quantity());
                totalCost = totalCost.add(transaction.netAmount());
                continue;
            }
            if (transaction.quantity().isGreaterThan(quantity)) {
                throw new InsufficientPositionException(ticker, brokerName,
                        quantity.value().toPlainString(), transaction.quantity().value().toPlainString());
            }
            Money averageCost = averageCostOf(totalCost, quantity, currency);
            Money costRemoved = averageCost.multiply(transaction.quantity().value());
            realizedPnl = realizedPnl.add(transaction.netAmount().subtract(costRemoved));
            quantity = quantity.subtract(transaction.quantity());
            totalCost = quantity.isZero() ? Money.zero(currency) : totalCost.subtract(costRemoved);
        }

        return builder()
                .ticker(ticker)
                .brokerName(brokerName)
                .quantity(quantity)
                .totalCost(totalCost)
                .averageCost(averageCostOf(totalCost, quantity, currency))
                .realizedPnl(realizedPnl)
                .build();
    }

    /**
     * Every open holding implied by a transaction history.
     *
     * <p>A holding is identified by ticker <em>and</em> broker: the same instrument bought at two
     * brokers has two independent cost bases. This method is the single place that knows that rule,
     * so callers never group transactions themselves. Fully sold holdings are omitted.
     */
    public static List<Position> deriveAll(List<Transaction> transactions) {
        Map<PositionKey, List<Transaction>> grouped = new LinkedHashMap<>();
        for (Transaction transaction : transactions) {
            grouped.computeIfAbsent(
                            new PositionKey(transaction.ticker(), transaction.brokerName()),
                            key -> new java.util.ArrayList<>())
                    .add(transaction);
        }
        return grouped.entrySet().stream()
                .map(entry -> fromTransactions(
                        entry.getKey().ticker(), entry.getKey().brokerName(), entry.getValue()))
                .filter(Position::isOpen)
                .toList();
    }

    private static Money averageCostOf(Money totalCost, Quantity quantity, Currency currency) {
        return quantity.isZero() ? Money.zero(currency) : totalCost.divide(quantity.value());
    }

    /** Identity of a holding: ticker plus broker, since each broker carries its own cost basis. */
    private record PositionKey(Ticker ticker, String brokerName) {
    }

    /** Whether any shares are still held. */
    public boolean isOpen() {
        return !quantity.isZero();
    }

    public Ticker ticker() {
        return ticker;
    }

    public String brokerName() {
        return brokerName;
    }

    public Quantity quantity() {
        return quantity;
    }

    public Money totalCost() {
        return totalCost;
    }

    public Money averageCost() {
        return averageCost;
    }

    public Money realizedPnl() {
        return realizedPnl;
    }

    /** Fluent builder for {@link Position}, following the convention of {@code Transaction.Builder}. */
    public static final class Builder {

        private Ticker ticker;
        private String brokerName;
        private Quantity quantity;
        private Money totalCost;
        private Money averageCost;
        private Money realizedPnl;

        private Builder() {
        }

        public Builder ticker(Ticker ticker) {
            this.ticker = ticker;
            return this;
        }

        public Builder brokerName(String brokerName) {
            this.brokerName = brokerName;
            return this;
        }

        public Builder quantity(Quantity quantity) {
            this.quantity = quantity;
            return this;
        }

        public Builder totalCost(Money totalCost) {
            this.totalCost = totalCost;
            return this;
        }

        public Builder averageCost(Money averageCost) {
            this.averageCost = averageCost;
            return this;
        }

        public Builder realizedPnl(Money realizedPnl) {
            this.realizedPnl = realizedPnl;
            return this;
        }

        public Position build() {
            return new Position(this);
        }
    }
}
