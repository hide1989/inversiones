package co.com.david.inversiones.model;

/** The allocation ceilings of the investor profile, as a share of total market value. */
public enum DiversificationLimit {

    /** At most 20% of the portfolio in any single stock. */
    SINGLE_EQUITY(Percentage.of("20")),
    /** At most 25% of the portfolio in any single fund. */
    SINGLE_ETF(Percentage.of("25")),
    /** At most 30% of the portfolio in any one sector. */
    SECTOR(Percentage.of("30"));

    private final Percentage allowedWeight;

    DiversificationLimit(Percentage allowedWeight) {
        this.allowedWeight = allowedWeight;
    }

    public Percentage allowedWeight() {
        return allowedWeight;
    }
}
