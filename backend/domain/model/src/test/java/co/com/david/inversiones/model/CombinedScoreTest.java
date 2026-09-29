package co.com.david.inversiones.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class CombinedScoreTest {

    private static final LocalDate DATE = LocalDate.of(2026, 9, 29);

    private static SignalSnapshot snapshot(Map<SignalSource, BigDecimal> scores) {
        return SignalSnapshot.builder()
                .ticker(Ticker.of("TSLA"))
                .snapshotDate(DATE)
                .scores(scores)
                .build();
    }

    @Test
    void withEverySourcePresentTheScoreIsTheWeightedAverage() {
        Map<SignalSource, BigDecimal> scores = new LinkedHashMap<>();
        scores.put(SignalSource.TECHNICAL, new BigDecimal("8"));
        scores.put(SignalSource.NEWS, new BigDecimal("7"));
        scores.put(SignalSource.ANALYST, new BigDecimal("6"));
        scores.put(SignalSource.INSIDER, new BigDecimal("5"));
        scores.put(SignalSource.SOCIAL, new BigDecimal("4"));
        scores.put(SignalSource.WSB, new BigDecimal("3"));

        CombinedScore result = CombinedScore.from(snapshot(scores));

        // 8(.30) + 7(.20) + 6(.15) + 5(.15) + 4(.10) + 3(.10) = 6.15
        assertThat(result.value()).isEqualByComparingTo("6.15");
        assertThat(result.level()).isEqualTo(SignalLevel.HOLD);
        assertThat(result.usedSources()).hasSize(6);
    }

    @Test
    void redistributesWeightProportionallyWhenSourcesAreMissing() {
        Map<SignalSource, BigDecimal> scores = new LinkedHashMap<>();
        scores.put(SignalSource.TECHNICAL, new BigDecimal("8"));
        scores.put(SignalSource.NEWS, new BigDecimal("7"));
        scores.put(SignalSource.ANALYST, new BigDecimal("6"));

        CombinedScore result = CombinedScore.from(snapshot(scores));

        // (8x.30 + 7x.20 + 6x.15) / (.30 + .20 + .15) = 4.70 / 0.65
        assertThat(result.value()).isEqualByComparingTo("7.23");
        assertThat(result.level()).isEqualTo(SignalLevel.BUY);
        assertThat(result.usedSources()).containsExactlyInAnyOrder(
                SignalSource.TECHNICAL, SignalSource.NEWS, SignalSource.ANALYST);
    }

    @Test
    void aMissingSourceDoesNotDragTheScoreDownTheWayAZeroWould() {
        Map<SignalSource, BigDecimal> onlyTechnical = new LinkedHashMap<>();
        onlyTechnical.put(SignalSource.TECHNICAL, new BigDecimal("9"));

        CombinedScore result = CombinedScore.from(snapshot(onlyTechnical));

        assertThat(result.value()).isEqualByComparingTo("9.00");
        assertThat(result.level()).isEqualTo(SignalLevel.STRONG_BUY);
    }

    @Test
    void withNoSourcesAtAllTheScoreIsNeutralAndNoSourceIsClaimed() {
        CombinedScore result = CombinedScore.from(snapshot(Map.of()));

        assertThat(result.value()).isEqualByComparingTo("0.00");
        assertThat(result.level()).isEqualTo(SignalLevel.STRONG_SELL);
        assertThat(result.usedSources()).isEmpty();
    }

    @Test
    void ignoresSourcesReportedExplicitlyAsNull() {
        Map<SignalSource, BigDecimal> withNulls = new LinkedHashMap<>();
        withNulls.put(SignalSource.TECHNICAL, new BigDecimal("9"));
        withNulls.put(SignalSource.INSIDER, null);

        CombinedScore result = CombinedScore.from(snapshot(withNulls));

        assertThat(result.usedSources()).containsExactly(SignalSource.TECHNICAL);
        assertThat(result.value()).isEqualByComparingTo("9.00");
    }
}
