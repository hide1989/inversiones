package co.com.david.inversiones.model;

/** Kind of tradable instrument, which drives the allocation limit that applies to it. */
public enum InstrumentType {
    EQUITY,
    ETF,
    ADR;

    /** Whether this instrument is a fund, which is allowed a larger single-holding weight. */
    public boolean isFund() {
        return this == ETF;
    }
}
