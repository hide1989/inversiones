package co.com.david.inversiones.model;

import java.time.LocalDate;
import java.util.Objects;

/**
 * A single executed trade: one lot bought or sold at one broker on one date.
 *
 * <p>Positions are always derived from transactions, never stored directly, so that the weighted
 * average cost and the realized profit can be recomputed from the record of what actually happened.
 */
public final class Transaction {

    private final Long id;
    private final Ticker ticker;
    private final String brokerName;
    private final TransactionType type;
    private final Quantity quantity;
    private final Money unitPrice;
    private final Money fees;
    private final LocalDate tradedAt;

    private Transaction(Builder builder) {
        this.id = builder.id;
        this.ticker = Objects.requireNonNull(builder.ticker, "ticker must not be null");
        this.type = Objects.requireNonNull(builder.type, "type must not be null");
        this.brokerName = Objects.requireNonNull(builder.brokerName, "brokerName must not be null");
        this.quantity = Objects.requireNonNull(builder.quantity, "quantity must not be null");
        this.unitPrice = Objects.requireNonNull(builder.unitPrice, "unitPrice must not be null");
        this.tradedAt = Objects.requireNonNull(builder.tradedAt, "tradedAt must not be null");
        this.fees = builder.fees == null ? Money.zero(this.unitPrice.currency()) : builder.fees;
        // Raises CurrencyMismatchException when fees are not in the trade currency.
        this.unitPrice.add(this.fees);
    }

    public static Builder builder() {
        return new Builder();
    }

    /** Quantity times unit price, before fees. */
    public Money grossAmount() {
        return unitPrice.multiply(quantity.value());
    }

    /** What the trade actually moved: gross plus fees on a purchase, gross minus fees on a sale. */
    public Money netAmount() {
        return type == TransactionType.BUY
                ? grossAmount().add(fees)
                : grossAmount().subtract(fees);
    }

    public Long id() {
        return id;
    }

    public Ticker ticker() {
        return ticker;
    }

    public String brokerName() {
        return brokerName;
    }

    public TransactionType type() {
        return type;
    }

    public Quantity quantity() {
        return quantity;
    }

    public Money unitPrice() {
        return unitPrice;
    }

    public Money fees() {
        return fees;
    }

    public LocalDate tradedAt() {
        return tradedAt;
    }

    /** Fluent builder for {@link Transaction}. */
    public static final class Builder {

        private Long id;
        private Ticker ticker;
        private String brokerName;
        private TransactionType type;
        private Quantity quantity;
        private Money unitPrice;
        private Money fees;
        private LocalDate tradedAt;

        private Builder() {
        }

        public Builder id(Long id) {
            this.id = id;
            return this;
        }

        public Builder ticker(Ticker ticker) {
            this.ticker = ticker;
            return this;
        }

        public Builder brokerName(String brokerName) {
            this.brokerName = brokerName;
            return this;
        }

        public Builder type(TransactionType type) {
            this.type = type;
            return this;
        }

        public Builder quantity(Quantity quantity) {
            this.quantity = quantity;
            return this;
        }

        public Builder unitPrice(Money unitPrice) {
            this.unitPrice = unitPrice;
            return this;
        }

        public Builder fees(Money fees) {
            this.fees = fees;
            return this;
        }

        public Builder tradedAt(LocalDate tradedAt) {
            this.tradedAt = tradedAt;
            return this;
        }

        public Transaction build() {
            return new Transaction(this);
        }
    }
}
