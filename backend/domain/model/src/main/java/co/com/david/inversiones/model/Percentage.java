package co.com.david.inversiones.model;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * A percentage expressed in percent units: a value of {@code 13.33} means 13.33%.
 *
 * <p>Normalized to scale {@value #SCALE}, which is enough precision for display and comparison
 * without accumulating rounding noise.
 */
public record Percentage(BigDecimal value) {

    public static final int SCALE = 4;

    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");

    public Percentage {
        value = value == null ? BigDecimal.ZERO : value.setScale(SCALE, RoundingMode.HALF_UP);
    }

    public static Percentage of(BigDecimal value) {
        return new Percentage(value);
    }

    public static Percentage of(String value) {
        return new Percentage(new BigDecimal(value));
    }

    public static Percentage zero() {
        return new Percentage(BigDecimal.ZERO);
    }

    /**
     * The share {@code part} represents of {@code whole}, in percent units.
     *
     * <p>Returns zero when {@code whole} is zero rather than dividing by zero, which is the correct
     * reading for an empty portfolio or a position with no cost basis.
     */
    public static Percentage between(Money part, Money whole) {
        if (whole.isZero()) {
            return zero();
        }
        return new Percentage(part.amount()
                .multiply(ONE_HUNDRED)
                .divide(whole.amount(), SCALE, RoundingMode.HALF_UP));
    }
}
