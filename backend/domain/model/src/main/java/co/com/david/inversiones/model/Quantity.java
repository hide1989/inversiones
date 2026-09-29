package co.com.david.inversiones.model;

import co.com.david.inversiones.model.exception.InvalidQuantityException;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * A non-negative number of shares, supporting fractional holdings.
 *
 * <p>Normalized to scale {@value #SCALE} so that equal quantities are equal.
 */
public record Quantity(BigDecimal value) implements Comparable<Quantity> {

    public static final int SCALE = 10;

    public Quantity {
        if (value == null) {
            throw new InvalidQuantityException("Quantity must not be null");
        }
        if (value.signum() < 0) {
            throw new InvalidQuantityException("Quantity must not be negative: " + value);
        }
        value = value.setScale(SCALE, RoundingMode.HALF_UP);
    }

    public static Quantity of(BigDecimal value) {
        return new Quantity(value);
    }

    public static Quantity of(String value) {
        return new Quantity(new BigDecimal(value));
    }

    public static Quantity zero() {
        return new Quantity(BigDecimal.ZERO);
    }

    public Quantity add(Quantity other) {
        return new Quantity(value.add(other.value));
    }

    /** @throws InvalidQuantityException when the result would be negative. */
    public Quantity subtract(Quantity other) {
        BigDecimal result = value.subtract(other.value);
        if (result.signum() < 0) {
            throw new InvalidQuantityException(
                    "Cannot subtract %s from %s: result would be negative".formatted(other.value, value));
        }
        return new Quantity(result);
    }

    public boolean isZero() {
        return value.signum() == 0;
    }

    public boolean isGreaterThan(Quantity other) {
        return value.compareTo(other.value) > 0;
    }

    @Override
    public int compareTo(Quantity other) {
        return value.compareTo(other.value);
    }
}
