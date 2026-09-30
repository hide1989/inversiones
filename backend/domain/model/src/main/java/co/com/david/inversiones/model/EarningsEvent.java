package co.com.david.inversiones.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

/**
 * One upcoming earnings report for a held ticker.
 *
 * <p>{@code daysRemaining} is always computed by the use case from {@code reportDate} and {@link
 * co.com.david.inversiones.model.gateways.TimeProvider#today()} — never trusted from upstream —
 * because a value the system clock could compute must never be read from a payload a test cannot
 * control.
 */
public final class EarningsEvent {

    private final Ticker ticker;
    private final LocalDate reportDate;
    private final long daysRemaining;
    private final BigDecimal estimatedEps;
    private final BigDecimal historicalSurprise;

    private EarningsEvent(Builder builder) {
        this.ticker = Objects.requireNonNull(builder.ticker, "ticker must not be null");
        this.reportDate = Objects.requireNonNull(builder.reportDate, "reportDate must not be null");
        this.daysRemaining = builder.daysRemaining;
        this.estimatedEps = builder.estimatedEps;
        this.historicalSurprise = builder.historicalSurprise;
    }

    public static Builder builder() {
        return new Builder();
    }

    public Ticker ticker() {
        return ticker;
    }

    public LocalDate reportDate() {
        return reportDate;
    }

    public long daysRemaining() {
        return daysRemaining;
    }

    public BigDecimal estimatedEps() {
        return estimatedEps;
    }

    public BigDecimal historicalSurprise() {
        return historicalSurprise;
    }

    /** Fluent builder for {@link EarningsEvent}, following the convention of {@code SignalSnapshot.Builder}. */
    public static final class Builder {

        private Ticker ticker;
        private LocalDate reportDate;
        private long daysRemaining;
        private BigDecimal estimatedEps;
        private BigDecimal historicalSurprise;

        private Builder() {
        }

        public Builder ticker(Ticker ticker) {
            this.ticker = ticker;
            return this;
        }

        public Builder reportDate(LocalDate reportDate) {
            this.reportDate = reportDate;
            return this;
        }

        public Builder daysRemaining(long daysRemaining) {
            this.daysRemaining = daysRemaining;
            return this;
        }

        public Builder estimatedEps(BigDecimal estimatedEps) {
            this.estimatedEps = estimatedEps;
            return this;
        }

        public Builder historicalSurprise(BigDecimal historicalSurprise) {
            this.historicalSurprise = historicalSurprise;
            return this;
        }

        public EarningsEvent build() {
            return new EarningsEvent(this);
        }
    }
}
