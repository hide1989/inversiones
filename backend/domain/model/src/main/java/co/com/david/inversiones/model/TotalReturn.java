package co.com.david.inversiones.model;

import java.util.List;
import java.util.Objects;

/**
 * The portfolio return in Colombian pesos, with the asset gain and the currency exchange effect
 * reported as separate amounts.
 */
public final class TotalReturn {

    private final List<ReturnSegment> segments;
    private final ExchangeRate baseRate;
    private final ExchangeRate currentRate;
    private final Money exchangeEffect;
    private final Money assetGainInCop;
    private final Money totalReturnInCop;

    private TotalReturn(Builder builder) {
        this.segments = List.copyOf(Objects.requireNonNull(builder.segments, "segments must not be null"));
        this.baseRate = Objects.requireNonNull(builder.baseRate, "baseRate must not be null");
        this.currentRate = Objects.requireNonNull(builder.currentRate, "currentRate must not be null");
        this.exchangeEffect = Objects.requireNonNull(builder.exchangeEffect, "exchangeEffect must not be null");
        this.assetGainInCop = Objects.requireNonNull(builder.assetGainInCop, "assetGainInCop must not be null");
        this.totalReturnInCop = Objects.requireNonNull(builder.totalReturnInCop,
                "totalReturnInCop must not be null");
    }

    public static Builder builder() {
        return new Builder();
    }

    public List<ReturnSegment> segments() {
        return segments;
    }

    public ExchangeRate baseRate() {
        return baseRate;
    }

    public ExchangeRate currentRate() {
        return currentRate;
    }

    public Money exchangeEffect() {
        return exchangeEffect;
    }

    public Money assetGainInCop() {
        return assetGainInCop;
    }

    public Money totalReturnInCop() {
        return totalReturnInCop;
    }

    /** Fluent builder for {@link TotalReturn}. */
    public static final class Builder {

        private List<ReturnSegment> segments;
        private ExchangeRate baseRate;
        private ExchangeRate currentRate;
        private Money exchangeEffect;
        private Money assetGainInCop;
        private Money totalReturnInCop;

        private Builder() {
        }

        public Builder segments(List<ReturnSegment> segments) {
            this.segments = segments;
            return this;
        }

        public Builder baseRate(ExchangeRate baseRate) {
            this.baseRate = baseRate;
            return this;
        }

        public Builder currentRate(ExchangeRate currentRate) {
            this.currentRate = currentRate;
            return this;
        }

        public Builder exchangeEffect(Money exchangeEffect) {
            this.exchangeEffect = exchangeEffect;
            return this;
        }

        public Builder assetGainInCop(Money assetGainInCop) {
            this.assetGainInCop = assetGainInCop;
            return this;
        }

        public Builder totalReturnInCop(Money totalReturnInCop) {
            this.totalReturnInCop = totalReturnInCop;
            return this;
        }

        public TotalReturn build() {
            return new TotalReturn(this);
        }
    }
}
