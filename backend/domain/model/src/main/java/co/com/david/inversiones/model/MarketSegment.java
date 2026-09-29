package co.com.david.inversiones.model;

/** Where a holding trades, which determines whether it is exposed to the COP/USD rate. */
public enum MarketSegment {

    /** NYSE and NASDAQ holdings at Hapi and ARQ, denominated in dollars. */
    USD_ASSETS(Currency.USD),
    /** NYSE ADRs held through Trii, quoted in pesos but bought in dollars. */
    TRII_ADR(Currency.USD),
    /** Colombian stock exchange holdings, bought and quoted in pesos. */
    BVC_COLOMBIA(Currency.COP);

    private final Currency currency;

    MarketSegment(Currency currency) {
        this.currency = currency;
    }

    public Currency currency() {
        return currency;
    }
}
