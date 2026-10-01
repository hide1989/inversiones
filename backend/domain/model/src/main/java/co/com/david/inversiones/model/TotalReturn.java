package co.com.david.inversiones.model;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * The portfolio return in Colombian pesos, with the asset gain and the currency exchange effect
 * reported as separate amounts.
 *
 * <p>{@code currentRate}, {@code exchangeEffect}, {@code assetGainInCop} and
 * {@code totalReturnInCop} are all {@link Optional} for the same reason: every one of them needs
 * today's COP/USD rate to be computed (converting a dollar-denominated gain into pesos, or
 * comparing today's rate against {@link #baseRate()}), and when that rate cannot be obtained this
 * type must be able to say so instead of forcing a caller to pick between throwing away the whole
 * report and quietly substituting a value. Only {@link #segments()} and {@link #baseRate()} never
 * need today's rate — the segment breakdown is computed in each segment's own native currency, and
 * the base rate is a configured constant — so they alone stay non-optional.
 */
public final class TotalReturn {

    private final List<ReturnSegment> segments;
    private final ExchangeRate baseRate;
    private final Optional<ExchangeRate> currentRate;
    private final Optional<Money> exchangeEffect;
    private final Optional<Money> assetGainInCop;
    private final Optional<Money> totalReturnInCop;

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

    /** Today's COP/USD rate, or {@link Optional#empty()} when the TRM source was unavailable. */
    public Optional<ExchangeRate> currentRate() {
        return currentRate;
    }

    /**
     * How much of the total return is attributable to the peso moving against the dollar, or
     * {@link Optional#empty()} when {@link #currentRate()} is absent. Never zero as a stand-in for
     * "unknown": an unknown rate must never render as "no currency effect".
     */
    public Optional<Money> exchangeEffect() {
        return exchangeEffect;
    }

    public Optional<Money> assetGainInCop() {
        return assetGainInCop;
    }

    public Optional<Money> totalReturnInCop() {
        return totalReturnInCop;
    }

    /** Fluent builder for {@link TotalReturn}. */
    public static final class Builder {

        private List<ReturnSegment> segments;
        private ExchangeRate baseRate;
        private Optional<ExchangeRate> currentRate = Optional.empty();
        private Optional<Money> exchangeEffect = Optional.empty();
        private Optional<Money> assetGainInCop = Optional.empty();
        private Optional<Money> totalReturnInCop = Optional.empty();

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

        public Builder currentRate(Optional<ExchangeRate> currentRate) {
            this.currentRate = currentRate;
            return this;
        }

        public Builder exchangeEffect(Optional<Money> exchangeEffect) {
            this.exchangeEffect = exchangeEffect;
            return this;
        }

        public Builder assetGainInCop(Optional<Money> assetGainInCop) {
            this.assetGainInCop = assetGainInCop;
            return this;
        }

        public Builder totalReturnInCop(Optional<Money> totalReturnInCop) {
            this.totalReturnInCop = totalReturnInCop;
            return this;
        }

        public TotalReturn build() {
            return new TotalReturn(this);
        }
    }
}
