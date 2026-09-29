package co.com.david.inversiones.model;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/** The result of checking the portfolio against the allocation ceilings of the investor profile. */
public final class DiversificationReport {

    private final List<DiversificationViolation> violations;
    private final Money totalMarketValue;
    private final Map<String, Percentage> weightByTicker;
    private final Map<String, Percentage> weightBySector;

    private DiversificationReport(Builder builder) {
        this.violations = List.copyOf(Objects.requireNonNull(builder.violations, "violations must not be null"));
        this.totalMarketValue = Objects.requireNonNull(builder.totalMarketValue,
                "totalMarketValue must not be null");
        this.weightByTicker = Map.copyOf(Objects.requireNonNull(builder.weightByTicker,
                "weightByTicker must not be null"));
        this.weightBySector = Map.copyOf(Objects.requireNonNull(builder.weightBySector,
                "weightBySector must not be null"));
    }

    public static Builder builder() {
        return new Builder();
    }

    public List<DiversificationViolation> violations() {
        return violations;
    }

    public Money totalMarketValue() {
        return totalMarketValue;
    }

    public Map<String, Percentage> weightByTicker() {
        return weightByTicker;
    }

    public Map<String, Percentage> weightBySector() {
        return weightBySector;
    }

    /** Whether every allocation ceiling is respected. */
    public boolean compliant() {
        return violations.isEmpty();
    }

    /** Fluent builder for {@link DiversificationReport}. */
    public static final class Builder {

        private List<DiversificationViolation> violations;
        private Money totalMarketValue;
        private Map<String, Percentage> weightByTicker;
        private Map<String, Percentage> weightBySector;

        private Builder() {
        }

        public Builder violations(List<DiversificationViolation> violations) {
            this.violations = violations;
            return this;
        }

        public Builder totalMarketValue(Money totalMarketValue) {
            this.totalMarketValue = totalMarketValue;
            return this;
        }

        public Builder weightByTicker(Map<String, Percentage> weightByTicker) {
            this.weightByTicker = weightByTicker;
            return this;
        }

        public Builder weightBySector(Map<String, Percentage> weightBySector) {
            this.weightBySector = weightBySector;
            return this;
        }

        public DiversificationReport build() {
            return new DiversificationReport(this);
        }
    }
}
