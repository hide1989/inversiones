package co.com.david.inversiones.model;

import co.com.david.inversiones.model.exception.InvalidTickerException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TickerTest {

    @Test
    void normalizesToTrimmedUppercase() {
        assertThat(Ticker.of("  tsla ").symbol()).isEqualTo("TSLA");
    }

    @Test
    void equalTickersAreEqualRegardlessOfInputCasing() {
        assertThat(Ticker.of("googl")).isEqualTo(Ticker.of("GOOGL"));
    }

    @Test
    void acceptsSymbolsWithDotsAndHyphens() {
        assertThat(Ticker.of("BRK.B").symbol()).isEqualTo("BRK.B");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "1TSLA", "TOOLONGSYMBOL", "TS LA", "TS$"})
    void rejectsMalformedSymbols(String candidate) {
        assertThatThrownBy(() -> Ticker.of(candidate))
                .isInstanceOf(InvalidTickerException.class);
    }

    @Test
    void rejectsNull() {
        assertThatThrownBy(() -> Ticker.of(null)).isInstanceOf(InvalidTickerException.class);
    }

    @Test
    void theCanonicalConstructorNormalizesAndValidatesToo() {
        assertThat(new Ticker(" tsla ")).isEqualTo(Ticker.of("TSLA"));
        assertThatThrownBy(() -> new Ticker("TS LA")).isInstanceOf(InvalidTickerException.class);
    }
}
