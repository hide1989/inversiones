package co.com.david.inversiones.model;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * One row of the externally-sourced watchlist (WSB trending tickers, sector picks, and the like) —
 * independent of what the portfolio actually holds.
 *
 * <p>{@code price}, {@code technicalScore} and {@code wsbScore} are frequently {@code null} in the
 * upstream payload: many rows are WSB-trending symbols n8n has social data for but has not yet
 * priced or scored technically. {@code combinedScore} and {@code signal} are the only fields the
 * upstream workflow always fills in.
 */
public final class WatchlistItem {

    private final Ticker ticker;
    private final BigDecimal price;
    private final BigDecimal technicalScore;
    private final BigDecimal wsbScore;
    private final BigDecimal combinedScore;
    private final String signal;

    private WatchlistItem(Builder builder) {
        this.ticker = Objects.requireNonNull(builder.ticker, "ticker must not be null");
        this.price = builder.price;
        this.technicalScore = builder.technicalScore;
        this.wsbScore = builder.wsbScore;
        this.combinedScore = builder.combinedScore;
        this.signal = builder.signal;
    }

    public static Builder builder() {
        return new Builder();
    }

    public Ticker ticker() {
        return ticker;
    }

    public BigDecimal price() {
        return price;
    }

    public BigDecimal technicalScore() {
        return technicalScore;
    }

    public BigDecimal wsbScore() {
        return wsbScore;
    }

    public BigDecimal combinedScore() {
        return combinedScore;
    }

    public String signal() {
        return signal;
    }

    /** Fluent builder for {@link WatchlistItem}, following the convention of {@code SignalSnapshot.Builder}. */
    public static final class Builder {

        private Ticker ticker;
        private BigDecimal price;
        private BigDecimal technicalScore;
        private BigDecimal wsbScore;
        private BigDecimal combinedScore;
        private String signal;

        private Builder() {
        }

        public Builder ticker(Ticker ticker) {
            this.ticker = ticker;
            return this;
        }

        public Builder price(BigDecimal price) {
            this.price = price;
            return this;
        }

        public Builder technicalScore(BigDecimal technicalScore) {
            this.technicalScore = technicalScore;
            return this;
        }

        public Builder wsbScore(BigDecimal wsbScore) {
            this.wsbScore = wsbScore;
            return this;
        }

        public Builder combinedScore(BigDecimal combinedScore) {
            this.combinedScore = combinedScore;
            return this;
        }

        public Builder signal(String signal) {
            this.signal = signal;
            return this;
        }

        public WatchlistItem build() {
            return new WatchlistItem(this);
        }
    }
}
