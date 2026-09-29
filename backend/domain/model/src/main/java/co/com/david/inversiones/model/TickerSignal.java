package co.com.david.inversiones.model;

/** One row of the signals dashboard: a ticker, its raw per-source scores, and the score they reduce to. */
public record TickerSignal(Ticker ticker, SignalSnapshot snapshot, CombinedScore score) {
}
