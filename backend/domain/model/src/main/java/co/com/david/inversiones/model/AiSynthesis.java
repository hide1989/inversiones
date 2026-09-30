package co.com.david.inversiones.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * The AI-generated read on one ticker for one day: a score, a recommendation, and the reasoning
 * behind it.
 *
 * <p>{@code hasData} is {@code false} when the upstream workflow marked this ticker {@code
 * sin_datos} after its LLM call kept failing on retry. A no-data synthesis still carries a
 * placeholder score and signal (upstream's own "hold, neutral" default) — those fields must never
 * be trusted or averaged when {@code hasData} is {@code false}; callers exist precisely to enforce
 * that (see {@code GetAiSynthesisUseCase}).
 */
public final class AiSynthesis {

    private final Ticker ticker;
    private final LocalDate snapshotDate;
    private final BigDecimal iaScore;
    private final String signal;
    private final String conviction;
    private final String horizon;
    private final String summary;
    private final List<String> catalysts;
    private final List<String> risks;
    private final boolean hasData;

    private AiSynthesis(Builder builder) {
        this.ticker = Objects.requireNonNull(builder.ticker, "ticker must not be null");
        this.snapshotDate = Objects.requireNonNull(builder.snapshotDate, "snapshotDate must not be null");
        this.iaScore = builder.iaScore;
        this.signal = builder.signal;
        this.conviction = builder.conviction;
        this.horizon = builder.horizon;
        this.summary = builder.summary;
        this.catalysts = builder.catalysts == null
                ? List.of() : List.copyOf(builder.catalysts);
        this.risks = builder.risks == null
                ? List.of() : List.copyOf(builder.risks);
        this.hasData = builder.hasData;
    }

    public static Builder builder() {
        return new Builder();
    }

    public Ticker ticker() {
        return ticker;
    }

    public LocalDate snapshotDate() {
        return snapshotDate;
    }

    public BigDecimal iaScore() {
        return iaScore;
    }

    public String signal() {
        return signal;
    }

    public String conviction() {
        return conviction;
    }

    public String horizon() {
        return horizon;
    }

    public String summary() {
        return summary;
    }

    public List<String> catalysts() {
        return catalysts;
    }

    public List<String> risks() {
        return risks;
    }

    public boolean hasData() {
        return hasData;
    }

    /** Fluent builder for {@link AiSynthesis}, following the convention of {@code SignalSnapshot.Builder}. */
    public static final class Builder {

        private Ticker ticker;
        private LocalDate snapshotDate;
        private BigDecimal iaScore;
        private String signal;
        private String conviction;
        private String horizon;
        private String summary;
        private List<String> catalysts = Collections.emptyList();
        private List<String> risks = Collections.emptyList();
        private boolean hasData;

        private Builder() {
        }

        public Builder ticker(Ticker ticker) {
            this.ticker = ticker;
            return this;
        }

        public Builder snapshotDate(LocalDate snapshotDate) {
            this.snapshotDate = snapshotDate;
            return this;
        }

        public Builder iaScore(BigDecimal iaScore) {
            this.iaScore = iaScore;
            return this;
        }

        public Builder signal(String signal) {
            this.signal = signal;
            return this;
        }

        public Builder conviction(String conviction) {
            this.conviction = conviction;
            return this;
        }

        public Builder horizon(String horizon) {
            this.horizon = horizon;
            return this;
        }

        public Builder summary(String summary) {
            this.summary = summary;
            return this;
        }

        public Builder catalysts(List<String> catalysts) {
            this.catalysts = catalysts;
            return this;
        }

        public Builder risks(List<String> risks) {
            this.risks = risks;
            return this;
        }

        public Builder hasData(boolean hasData) {
            this.hasData = hasData;
            return this;
        }

        public AiSynthesis build() {
            return new AiSynthesis(this);
        }
    }
}
