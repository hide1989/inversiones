package co.com.david.inversiones.model;

import co.com.david.inversiones.model.exception.CurrencyMismatchException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ExchangeRateTest {

    private static final LocalDate DATE = LocalDate.of(2026, 9, 29);

    @Test
    void convertsUsdIntoCop() {
        ExchangeRate rate = ExchangeRate.copPerUsd(new BigDecimal("3912.40"), DATE);
        assertThat(rate.convert(Money.usd("100")).amount()).isEqualByComparingTo("391240");
        assertThat(rate.convert(Money.usd("100")).currency()).isEqualTo(Currency.COP);
    }

    @Test
    void rejectsConvertingAnAmountThatIsNotInTheBaseCurrency() {
        ExchangeRate rate = ExchangeRate.copPerUsd(new BigDecimal("3912.40"), DATE);
        assertThatThrownBy(() -> rate.convert(Money.cop("100")))
                .isInstanceOf(CurrencyMismatchException.class);
    }

    @Test
    void reportsTheDifferenceAgainstAReferenceRate() {
        ExchangeRate current = ExchangeRate.copPerUsd(new BigDecimal("3912.40"), DATE);
        ExchangeRate base = ExchangeRate.copPerUsd(new BigDecimal("3568.42"), DATE.minusYears(1));
        assertThat(current.difference(base)).isEqualByComparingTo("343.98");
    }
}
