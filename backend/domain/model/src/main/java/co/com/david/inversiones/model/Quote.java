package co.com.david.inversiones.model;

/** The latest known price of an instrument and how far it has moved today. */
public record Quote(Ticker ticker, Money price, Percentage dayChangePercent) {
}
