package co.com.david.inversiones.model;

import java.util.List;
import java.util.Objects;

/**
 * The full portfolio, valued against current market quotes, with totals across all positions.
 *
 * <p>The three totals are expressed in {@link #baseCurrency()}, converted at the rate in force
 * when the valuation was produced.
 */
public final class PortfolioValuation {

    private final List<ValuedPosition> positions;
    private final Money totalInvested;
    private final Money totalMarketValue;
    private final Money totalUnrealizedPnl;
    private final List<Ticker> unpricedTickers;
    private final Currency baseCurrency;

    private PortfolioValuation(Builder builder) {
        this.positions = List.copyOf(Objects.requireNonNull(builder.positions, "positions must not be null"));
        this.totalInvested = Objects.requireNonNull(builder.totalInvested, "totalInvested must not be null");
        this.totalMarketValue = Objects.requireNonNull(builder.totalMarketValue,
                "totalMarketValue must not be null");
        this.totalUnrealizedPnl = Objects.requireNonNull(builder.totalUnrealizedPnl,
                "totalUnrealizedPnl must not be null");
        this.unpricedTickers = List.copyOf(Objects.requireNonNull(builder.unpricedTickers,
                "unpricedTickers must not be null"));
        this.baseCurrency = Objects.requireNonNull(builder.baseCurrency, "baseCurrency must not be null");
    }

    public static Builder builder() {
        return new Builder();
    }

    public List<ValuedPosition> positions() {
        return positions;
    }

    public Money totalInvested() {
        return totalInvested;
    }

    public Money totalMarketValue() {
        return totalMarketValue;
    }

    public Money totalUnrealizedPnl() {
        return totalUnrealizedPnl;
    }

    public List<Ticker> unpricedTickers() {
        return unpricedTickers;
    }

    /** Currency every total in this valuation is expressed in. */
    public Currency baseCurrency() {
        return baseCurrency;
    }

    /** Fluent builder for {@link PortfolioValuation}. */
    public static final class Builder {

        private List<ValuedPosition> positions;
        private Money totalInvested;
        private Money totalMarketValue;
        private Money totalUnrealizedPnl;
        private List<Ticker> unpricedTickers;
        private Currency baseCurrency;

        private Builder() {
        }

        public Builder positions(List<ValuedPosition> positions) {
            this.positions = positions;
            return this;
        }

        public Builder totalInvested(Money totalInvested) {
            this.totalInvested = totalInvested;
            return this;
        }

        public Builder totalMarketValue(Money totalMarketValue) {
            this.totalMarketValue = totalMarketValue;
            return this;
        }

        public Builder totalUnrealizedPnl(Money totalUnrealizedPnl) {
            this.totalUnrealizedPnl = totalUnrealizedPnl;
            return this;
        }

        public Builder unpricedTickers(List<Ticker> unpricedTickers) {
            this.unpricedTickers = unpricedTickers;
            return this;
        }

        public Builder baseCurrency(Currency baseCurrency) {
            this.baseCurrency = baseCurrency;
            return this;
        }

        public PortfolioValuation build() {
            return new PortfolioValuation(this);
        }
    }
}
