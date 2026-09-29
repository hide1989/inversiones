package co.com.david.inversiones.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

/**
 * The per-source signal scores recorded for one instrument on one day.
 *
 * <p>A source with no data is simply absent. Null values handed to the builder are dropped, so an
 * absent source and a source the provider explicitly reported as null are treated identically.
 */
public final class SignalSnapshot {

    private final Ticker ticker;
    private final LocalDate snapshotDate;
    private final Map<SignalSource, BigDecimal> scores;

    private SignalSnapshot(Builder builder) {
        this.ticker = Objects.requireNonNull(builder.ticker, "ticker must not be null");
        this.snapshotDate = Objects.requireNonNull(builder.snapshotDate, "snapshotDate must not be null");
        Map<SignalSource, BigDecimal> copy = new EnumMap<>(SignalSource.class);
        if (builder.scores != null) {
            builder.scores.forEach((source, score) -> {
                if (source != null && score != null) {
                    copy.put(source, score);
                }
            });
        }
        this.scores = Collections.unmodifiableMap(copy);
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

    public Map<SignalSource, BigDecimal> scores() {
        return scores;
    }

    public Optional<BigDecimal> scoreOf(SignalSource source) {
        return Optional.ofNullable(scores.get(source));
    }

    public Set<SignalSource> availableSources() {
        return scores.keySet();
    }

    /** Fluent builder for {@link SignalSnapshot}, following the convention of {@code Transaction.Builder}. */
    public static final class Builder {

        private Ticker ticker;
        private LocalDate snapshotDate;
        private Map<SignalSource, BigDecimal> scores;

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

        /**
         * Replaces the accumulated scores.
         *
         * <p>Copies the argument: the builder never holds a reference to a caller's collection, so
         * a later {@link #score(SignalSource, BigDecimal)} cannot mutate it. A {@link LinkedHashMap}
         * is used rather than an {@link EnumMap} because the copy must tolerate a null key, which
         * the constructor drops rather than rejecting.
         */
        public Builder scores(Map<SignalSource, BigDecimal> scores) {
            this.scores = scores == null ? null : new LinkedHashMap<>(scores);
            return this;
        }

        /** Adds or replaces one source's score. */
        public Builder score(SignalSource source, BigDecimal score) {
            if (this.scores == null) {
                this.scores = new LinkedHashMap<>();
            }
            this.scores.put(source, score);
            return this;
        }

        public SignalSnapshot build() {
            return new SignalSnapshot(this);
        }
    }
}
