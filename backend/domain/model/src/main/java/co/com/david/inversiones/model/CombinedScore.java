package co.com.david.inversiones.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Set;

/**
 * The single score a snapshot reduces to, on the one to ten scale.
 *
 * @param value       the weighted score, at scale two
 * @param level       the recommendation the value maps to
 * @param usedSources which sources actually contributed; a value built from two sources is a weaker
 *                    claim than the same value built from six, and callers should say so
 */
public record CombinedScore(BigDecimal value, SignalLevel level, Set<SignalSource> usedSources) {

    private static final int SCALE = 2;

    /**
     * Reduces a snapshot to one score, redistributing the weight of missing sources across the
     * available ones.
     *
     * <p>Treating a missing source as zero would turn "no data" into a sell signal, so the available
     * weights are renormalized to sum to one instead:
     * {@code sum(score x weight) / sum(weight)} over the available sources only.
     *
     * <p>With no sources at all the value is zero and {@code usedSources} is empty. Callers must
     * check for that before presenting the level, which would otherwise read as a strong sell.
     */
    public static CombinedScore from(SignalSnapshot snapshot) {
        BigDecimal weightedSum = BigDecimal.ZERO;
        BigDecimal weightTotal = BigDecimal.ZERO;

        for (SignalSource source : snapshot.availableSources()) {
            BigDecimal score = snapshot.scores().get(source);
            weightedSum = weightedSum.add(score.multiply(source.weight()));
            weightTotal = weightTotal.add(source.weight());
        }

        BigDecimal value = weightTotal.signum() == 0
                ? BigDecimal.ZERO.setScale(SCALE, RoundingMode.HALF_UP)
                : weightedSum.divide(weightTotal, SCALE, RoundingMode.HALF_UP);

        return new CombinedScore(value, SignalLevel.fromScore(value), Set.copyOf(snapshot.availableSources()));
    }
}
