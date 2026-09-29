package co.com.david.inversiones.model;

import java.math.BigDecimal;

/**
 * A source of trading signal, with the weight it carries in the combined score.
 *
 * <p>The weights sum to 1.00. When a source has no data its weight is redistributed across the
 * remaining sources rather than counted as zero — see {@link CombinedScore#from(SignalSnapshot)}.
 */
public enum SignalSource {

    TECHNICAL(new BigDecimal("0.30")),
    NEWS(new BigDecimal("0.20")),
    ANALYST(new BigDecimal("0.15")),
    INSIDER(new BigDecimal("0.15")),
    SOCIAL(new BigDecimal("0.10")),
    WSB(new BigDecimal("0.10"));

    private final BigDecimal weight;

    SignalSource(BigDecimal weight) {
        this.weight = weight;
    }

    public BigDecimal weight() {
        return weight;
    }
}
