package co.com.david.inversiones.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SignalSnapshotTest {

    @Test
    void dropsNullScoresSoAnAbsentSourceAndANullSourceAreTheSame() {
        Map<SignalSource, BigDecimal> scores = new HashMap<>();
        scores.put(SignalSource.TECHNICAL, new BigDecimal("8"));
        scores.put(SignalSource.WSB, null);

        SignalSnapshot snapshot = SignalSnapshot.builder()
                .ticker(Ticker.of("NU")).snapshotDate(LocalDate.of(2026, 9, 29))
                .scores(scores).build();

        assertThat(snapshot.availableSources()).containsExactly(SignalSource.TECHNICAL);
        assertThat(snapshot.scoreOf(SignalSource.WSB)).isEmpty();
        assertThat(snapshot.scoreOf(SignalSource.TECHNICAL)).contains(new BigDecimal("8"));
    }

    @Test
    void isImmutableAgainstLaterChangesToTheSourceMap() {
        Map<SignalSource, BigDecimal> scores = new HashMap<>();
        scores.put(SignalSource.TECHNICAL, new BigDecimal("8"));
        SignalSnapshot snapshot = SignalSnapshot.builder()
                .ticker(Ticker.of("NU")).snapshotDate(LocalDate.of(2026, 9, 29))
                .scores(scores).build();

        scores.put(SignalSource.NEWS, new BigDecimal("2"));

        assertThat(snapshot.availableSources()).containsExactly(SignalSource.TECHNICAL);
        assertThatThrownBy(() -> snapshot.scores().put(SignalSource.NEWS, BigDecimal.ONE))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
