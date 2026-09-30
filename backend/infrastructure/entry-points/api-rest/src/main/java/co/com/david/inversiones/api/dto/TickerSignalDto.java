package co.com.david.inversiones.api.dto;

import co.com.david.inversiones.model.TickerSignal;

/** Wire representation of a {@link TickerSignal}: one row of the signals dashboard. */
public record TickerSignalDto(String ticker, SignalSnapshotDto snapshot, CombinedScoreDto score) {

    public static TickerSignalDto from(TickerSignal signal) {
        return new TickerSignalDto(
                signal.ticker().symbol(), SignalSnapshotDto.from(signal.snapshot()), CombinedScoreDto.from(signal.score()));
    }
}
