package co.com.david.inversiones.api.dto;

import java.math.BigDecimal;

/**
 * Converts a {@link BigDecimal} to its exact plain-string form for the wire.
 *
 * <p>Applied to every decimal DTO field carrying a percentage, a score, or a financial ratio, for
 * the same reason {@link MoneyDto} carries its amount as a string: a JSON number becomes a
 * JavaScript {@code double} in every client, which loses precision that a {@link BigDecimal} was
 * chosen specifically to keep.
 */
final class DecimalStrings {

    private DecimalStrings() {
    }

    static String of(BigDecimal value) {
        return value == null ? null : value.toPlainString();
    }
}
