package co.com.david.inversiones.model;

import co.com.david.inversiones.model.exception.CurrencyMismatchException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * A monetary amount in a specific currency.
 *
 * <p>Amounts are normalized to scale {@value #SCALE} with {@link RoundingMode#HALF_UP} so that two
 * amounts representing the same value are always equal, regardless of the scale they were created
 * with. Arithmetic between different currencies is rejected rather than silently coerced.
 */
public record Money(BigDecimal amount, Currency currency) implements Comparable<Money> {

    /** Scale every amount is normalized to. */
    public static final int SCALE = 10;

    public Money {
        Objects.requireNonNull(amount, "amount must not be null");
        Objects.requireNonNull(currency, "currency must not be null");
        amount = amount.setScale(SCALE, RoundingMode.HALF_UP);
    }

    public static Money of(BigDecimal amount, Currency currency) {
        return new Money(amount, currency);
    }

    public static Money usd(String amount) {
        return new Money(new BigDecimal(amount), Currency.USD);
    }

    public static Money cop(String amount) {
        return new Money(new BigDecimal(amount), Currency.COP);
    }

    public static Money zero(Currency currency) {
        return new Money(BigDecimal.ZERO, currency);
    }

    public Money add(Money other) {
        requireSameCurrency(other);
        return new Money(amount.add(other.amount), currency);
    }

    public Money subtract(Money other) {
        requireSameCurrency(other);
        return new Money(amount.subtract(other.amount), currency);
    }

    public Money multiply(BigDecimal factor) {
        return new Money(amount.multiply(factor), currency);
    }

    /** Divides this amount, rounding half up at scale {@value #SCALE}. */
    public Money divide(BigDecimal divisor) {
        return new Money(amount.divide(divisor, SCALE, RoundingMode.HALF_UP), currency);
    }

    public Money negate() {
        return new Money(amount.negate(), currency);
    }

    public boolean isNegative() {
        return amount.signum() < 0;
    }

    public boolean isZero() {
        return amount.signum() == 0;
    }

    @Override
    public int compareTo(Money other) {
        requireSameCurrency(other);
        return amount.compareTo(other.amount);
    }

    private void requireSameCurrency(Money other) {
        if (currency != other.currency) {
            throw new CurrencyMismatchException(currency, other.currency);
        }
    }
}
