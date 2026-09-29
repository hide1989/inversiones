package co.com.david.inversiones.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class SignalLevelTest {

    @ParameterizedTest
    @CsvSource({
            "10.0, STRONG_BUY",
            "8.0,  STRONG_BUY",
            "7.99, BUY",
            "6.5,  BUY",
            "6.49, HOLD",
            "4.5,  HOLD",
            "4.49, SELL",
            "3.0,  SELL",
            "2.99, STRONG_SELL",
            "1.0,  STRONG_SELL"
    })
    void mapsScoresToLevelsAtTheDocumentedBoundaries(String score, SignalLevel expected) {
        assertThat(SignalLevel.fromScore(new BigDecimal(score))).isEqualTo(expected);
    }

    @Test
    void weightsOfAllSourcesSumToOne() {
        BigDecimal total = java.util.Arrays.stream(SignalSource.values())
                .map(SignalSource::weight)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(total).isEqualByComparingTo("1.00");
    }
}
