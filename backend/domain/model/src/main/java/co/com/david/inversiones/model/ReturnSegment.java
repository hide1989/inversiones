package co.com.david.inversiones.model;

import java.util.Objects;

/** The invested amount, market value and asset gain of one {@link MarketSegment}. */
public final class ReturnSegment {

    private final MarketSegment segment;
    private final Money invested;
    private final Money marketValue;
    private final Money assetGain;

    private ReturnSegment(Builder builder) {
        this.segment = Objects.requireNonNull(builder.segment, "segment must not be null");
        this.invested = Objects.requireNonNull(builder.invested, "invested must not be null");
        this.marketValue = Objects.requireNonNull(builder.marketValue, "marketValue must not be null");
        this.assetGain = Objects.requireNonNull(builder.assetGain, "assetGain must not be null");
    }

    public static Builder builder() {
        return new Builder();
    }

    public MarketSegment segment() {
        return segment;
    }

    public Money invested() {
        return invested;
    }

    public Money marketValue() {
        return marketValue;
    }

    public Money assetGain() {
        return assetGain;
    }

    /** Fluent builder for {@link ReturnSegment}. */
    public static final class Builder {

        private MarketSegment segment;
        private Money invested;
        private Money marketValue;
        private Money assetGain;

        private Builder() {
        }

        public Builder segment(MarketSegment segment) {
            this.segment = segment;
            return this;
        }

        public Builder invested(Money invested) {
            this.invested = invested;
            return this;
        }

        public Builder marketValue(Money marketValue) {
            this.marketValue = marketValue;
            return this;
        }

        public Builder assetGain(Money assetGain) {
            this.assetGain = assetGain;
            return this;
        }

        public ReturnSegment build() {
            return new ReturnSegment(this);
        }
    }
}
