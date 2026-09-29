package co.com.david.inversiones.model;

import java.util.Objects;

/**
 * A position combined with its current market price. A position with no available quote is
 * carried at cost with {@code priced() == false}.
 */
public final class ValuedPosition {

    private final Position position;
    private final Money marketValue;
    private final Money unrealizedPnl;
    private final Percentage unrealizedPnlPercent;
    private final Percentage dayChangePercent;
    private final boolean priced;

    private ValuedPosition(Builder builder) {
        this.position = Objects.requireNonNull(builder.position, "position must not be null");
        this.marketValue = Objects.requireNonNull(builder.marketValue, "marketValue must not be null");
        this.unrealizedPnl = Objects.requireNonNull(builder.unrealizedPnl, "unrealizedPnl must not be null");
        this.unrealizedPnlPercent = Objects.requireNonNull(builder.unrealizedPnlPercent,
                "unrealizedPnlPercent must not be null");
        this.dayChangePercent = Objects.requireNonNull(builder.dayChangePercent,
                "dayChangePercent must not be null");
        this.priced = builder.priced;
    }

    public static Builder builder() {
        return new Builder();
    }

    public Position position() {
        return position;
    }

    public Money marketValue() {
        return marketValue;
    }

    public Money unrealizedPnl() {
        return unrealizedPnl;
    }

    public Percentage unrealizedPnlPercent() {
        return unrealizedPnlPercent;
    }

    public Percentage dayChangePercent() {
        return dayChangePercent;
    }

    public boolean priced() {
        return priced;
    }

    /** Fluent builder for {@link ValuedPosition}. */
    public static final class Builder {

        private Position position;
        private Money marketValue;
        private Money unrealizedPnl;
        private Percentage unrealizedPnlPercent;
        private Percentage dayChangePercent;
        private boolean priced;

        private Builder() {
        }

        public Builder position(Position position) {
            this.position = position;
            return this;
        }

        public Builder marketValue(Money marketValue) {
            this.marketValue = marketValue;
            return this;
        }

        public Builder unrealizedPnl(Money unrealizedPnl) {
            this.unrealizedPnl = unrealizedPnl;
            return this;
        }

        public Builder unrealizedPnlPercent(Percentage unrealizedPnlPercent) {
            this.unrealizedPnlPercent = unrealizedPnlPercent;
            return this;
        }

        public Builder dayChangePercent(Percentage dayChangePercent) {
            this.dayChangePercent = dayChangePercent;
            return this;
        }

        public Builder priced(boolean priced) {
            this.priced = priced;
            return this;
        }

        public ValuedPosition build() {
            return new ValuedPosition(this);
        }
    }
}
