package co.com.david.inversiones.model;

import java.math.BigDecimal;

/** The recommendation a combined score maps to. */
public enum SignalLevel {

    STRONG_BUY,
    BUY,
    HOLD,
    SELL,
    STRONG_SELL;

    private static final BigDecimal STRONG_BUY_FLOOR = new BigDecimal("8.0");
    private static final BigDecimal BUY_FLOOR = new BigDecimal("6.5");
    private static final BigDecimal HOLD_FLOOR = new BigDecimal("4.5");
    private static final BigDecimal SELL_FLOOR = new BigDecimal("3.0");

    /** Maps a score on the one to ten scale to its recommendation. */
    public static SignalLevel fromScore(BigDecimal score) {
        if (score.compareTo(STRONG_BUY_FLOOR) >= 0) {
            return STRONG_BUY;
        }
        if (score.compareTo(BUY_FLOOR) >= 0) {
            return BUY;
        }
        if (score.compareTo(HOLD_FLOOR) >= 0) {
            return HOLD;
        }
        if (score.compareTo(SELL_FLOOR) >= 0) {
            return SELL;
        }
        return STRONG_SELL;
    }
}
