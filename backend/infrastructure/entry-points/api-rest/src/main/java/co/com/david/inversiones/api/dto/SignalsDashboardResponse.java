package co.com.david.inversiones.api.dto;

import co.com.david.inversiones.model.SignalsDashboard;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Wire representation of a {@link SignalsDashboard}. */
public record SignalsDashboardResponse(
        List<TickerSignalDto> signals,
        String averageScore,
        Map<String, Integer> levelCounts,
        List<String> tickersWithoutData) {

    public static SignalsDashboardResponse from(SignalsDashboard dashboard) {
        Map<String, Integer> levelCounts = new LinkedHashMap<>();
        dashboard.levelCounts().forEach((level, count) -> levelCounts.put(level.name(), count));
        return new SignalsDashboardResponse(
                dashboard.signals().stream().map(TickerSignalDto::from).toList(),
                DecimalStrings.of(dashboard.averageScore()),
                levelCounts,
                dashboard.tickersWithoutData().stream().map(t -> t.symbol()).toList());
    }
}
