package co.com.david.inversiones.model;

import co.com.david.inversiones.model.exception.InvalidTickerException;

import java.util.regex.Pattern;

/**
 * A market symbol, normalized to trimmed uppercase.
 *
 * <p>Accepts one to ten characters starting with a letter, allowing dots and hyphens for share
 * classes and foreign listings (for example {@code BRK.B}).
 */
public record Ticker(String symbol) {

    private static final Pattern VALID = Pattern.compile("^[A-Z][A-Z0-9.\\-]{0,9}$");

    /**
     * Normalizes and validates in the canonical constructor, so that no construction path — factory
     * or {@code new} — can produce an unvalidated ticker.
     */
    public Ticker {
        if (symbol == null) {
            throw new InvalidTickerException("null");
        }
        String normalized = symbol.trim().toUpperCase();
        if (!VALID.matcher(normalized).matches()) {
            throw new InvalidTickerException(symbol);
        }
        symbol = normalized;
    }

    /** @throws InvalidTickerException when {@code candidate} is not a valid market symbol */
    public static Ticker of(String candidate) {
        return new Ticker(candidate);
    }

    @Override
    public String toString() {
        return symbol;
    }
}
