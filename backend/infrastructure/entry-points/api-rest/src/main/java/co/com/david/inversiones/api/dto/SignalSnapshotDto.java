package co.com.david.inversiones.api.dto;

import co.com.david.inversiones.model.SignalSnapshot;

import java.util.LinkedHashMap;
import java.util.Map;

/** Wire representation of a {@link SignalSnapshot}: one day's per-source scores for a ticker. */
public record SignalSnapshotDto(String ticker, String snapshotDate, Map<String, String> scores) {

    public static SignalSnapshotDto from(SignalSnapshot snapshot) {
        Map<String, String> scores = new LinkedHashMap<>();
        snapshot.scores().forEach((source, score) -> scores.put(source.name(), DecimalStrings.of(score)));
        return new SignalSnapshotDto(snapshot.ticker().symbol(), snapshot.snapshotDate().toString(), scores);
    }
}
