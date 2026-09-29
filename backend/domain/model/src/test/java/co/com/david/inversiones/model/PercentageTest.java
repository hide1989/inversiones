package co.com.david.inversiones.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PercentageTest {

    @Test
    void computesTheShareOfAWholeInPercentUnits() {
        Percentage result = Percentage.between(Money.usd("328.42"), Money.usd("2463.00"));
        assertThat(result.value()).isEqualByComparingTo("13.3341");
    }

    @Test
    void returnsZeroWhenTheWholeIsZeroInsteadOfDividingByZero() {
        assertThat(Percentage.between(Money.usd("10"), Money.zero(Currency.USD)))
                .isEqualTo(Percentage.zero());
    }

    @Test
    void supportsNegativePercentages() {
        assertThat(Percentage.between(Money.usd("-12.40"), Money.usd("50.00")).value())
                .isEqualByComparingTo("-24.8000");
    }
}
