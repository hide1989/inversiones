package co.com.david.inversiones.api.dto;

import co.com.david.inversiones.model.Money;

/**
 * Wire representation of a {@link Money}.
 *
 * <p>{@code amount} is a JSON string, never a bare number. Every JavaScript client parses a JSON
 * number into a {@code double}, and {@link Money}'s scale of 10 does not survive that round trip:
 * this project already shipped and fixed a Critical defect where an amount lost its currency
 * across the wire, and a numeric {@code amount} here would reintroduce the same class of bug by
 * silently rounding client-side. Carrying the amount as a string keeps every digit and forces
 * callers to parse it deliberately (e.g. into a {@code BigDecimal} or a decimal library) instead
 * of accidentally through {@code JSON.parse}.
 *
 * @param amount   the exact decimal amount, serialized as a string
 * @param currency the ISO-style currency code (USD or COP)
 */
public record MoneyDto(String amount, String currency) {

    public static MoneyDto from(Money money) {
        return new MoneyDto(money.amount().toPlainString(), money.currency().name());
    }
}
