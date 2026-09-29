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

    public static Ticker of(String candidate) {
        if (candidate == null) {
            throw new InvalidTickerException("null");
        }
        String normalized = candidate.trim().toUpperCase();
        if (!VALID.matcher(normalized).matches()) {
            throw new InvalidTickerException(candidate);
        }
        return new Ticker(normalized);
    }

    @Override
    public String toString() {
        return symbol;
    }
}
