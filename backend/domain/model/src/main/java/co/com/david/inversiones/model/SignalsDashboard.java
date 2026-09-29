package co.com.david.inversiones.model;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * The full signals dashboard: one row per held ticker, the portfolio-level average score, how many
 * tickers fall into each recommendation level, and which tickers had no signal data at all.
 *
 * <p>Tickers listed in {@link #tickersWithoutData()} are excluded from {@link #averageScore()} and
 * {@link #levelCounts()}: a snapshot with no sources reduces to a zero score, which would otherwise
 * read as a strong sell that nobody actually signaled.
 */
public final class SignalsDashboard {

    private final List<TickerSignal> signals;
    private final BigDecimal averageScore;
    private final Map<SignalLevel, Integer> levelCounts;
    private final List<Ticker> tickersWithoutData;

    private SignalsDashboard(Builder builder) {
        this.signals = List.copyOf(Objects.requireNonNull(builder.signals, "signals must not be null"));
        this.averageScore = Objects.requireNonNull(builder.averageScore, "averageScore must not be null");
        this.levelCounts = Map.copyOf(Objects.requireNonNull(builder.levelCounts, "levelCounts must not be null"));
        this.tickersWithoutData =
                List.copyOf(Objects.requireNonNull(builder.tickersWithoutData, "tickersWithoutData must not be null"));
    }

    public static Builder builder() {
        return new Builder();
    }

    public List<TickerSignal> signals() {
        return signals;
    }

    public BigDecimal averageScore() {
        return averageScore;
    }

    public Map<SignalLevel, Integer> levelCounts() {
        return levelCounts;
    }

    public List<Ticker> tickersWithoutData() {
        return tickersWithoutData;
    }

    /** Fluent builder for {@link SignalsDashboard}, following the convention of {@code Transaction.Builder}. */
    public static final class Builder {

        private List<TickerSignal> signals;
        private BigDecimal averageScore;
        private Map<SignalLevel, Integer> levelCounts;
        private List<Ticker> tickersWithoutData;

        private Builder() {
        }

        public Builder signals(List<TickerSignal> signals) {
            this.signals = signals;
            return this;
        }

        public Builder averageScore(BigDecimal averageScore) {
            this.averageScore = averageScore;
            return this;
        }

        public Builder levelCounts(Map<SignalLevel, Integer> levelCounts) {
            this.levelCounts = levelCounts;
            return this;
        }

        public Builder tickersWithoutData(List<Ticker> tickersWithoutData) {
            this.tickersWithoutData = tickersWithoutData;
            return this;
        }

        public SignalsDashboard build() {
            return new SignalsDashboard(this);
        }
    }
}
