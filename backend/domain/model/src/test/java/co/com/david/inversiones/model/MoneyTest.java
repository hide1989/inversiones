package co.com.david.inversiones.model;

import co.com.david.inversiones.model.exception.CurrencyMismatchException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MoneyTest {

    @Test
    void normalizesScaleSoThatEqualAmountsAreEqual() {
        assertThat(Money.usd("100")).isEqualTo(Money.usd("100.0000000000"));
    }

    @Test
    void addsAmountsOfTheSameCurrency() {
        assertThat(Money.usd("300.00").add(Money.usd("150.50")))
                .isEqualTo(Money.usd("450.50"));
    }

    @Test
    void rejectsArithmeticBetweenDifferentCurrencies() {
        assertThatThrownBy(() -> Money.usd("100").add(Money.cop("100")))
                .isInstanceOf(CurrencyMismatchException.class)
                .hasMessageContaining("USD")
                .hasMessageContaining("COP");
    }

    @Test
    void subtractsProducingNegativeAmounts() {
        Money result = Money.usd("50").subtract(Money.usd("120"));
        assertThat(result.amount()).isEqualByComparingTo("-70");
        assertThat(result.isNegative()).isTrue();
    }

    @Test
    void multipliesByAFractionalFactor() {
        assertThat(Money.usd("612.40").multiply(new BigDecimal("0.486372")).amount())
                .isEqualByComparingTo("297.8542128");
    }

    @Test
    void dividesRoundingHalfUpAtScaleTen() {
        assertThat(Money.usd("100").divide(new BigDecimal("3")).amount())
                .isEqualByComparingTo("33.3333333333");
    }

    @Test
    void rejectsDivisionByZero() {
        assertThatThrownBy(() -> Money.usd("100").divide(BigDecimal.ZERO))
                .isInstanceOf(ArithmeticException.class);
    }

    @Test
    void zeroIsZeroAndNotNegative() {
        assertThat(Money.zero(Currency.COP).isZero()).isTrue();
        assertThat(Money.zero(Currency.COP).isNegative()).isFalse();
    }

    @Test
    void comparesAmountsOfTheSameCurrency() {
        assertThat(Money.usd("10").compareTo(Money.usd("9.99"))).isPositive();
    }
}
