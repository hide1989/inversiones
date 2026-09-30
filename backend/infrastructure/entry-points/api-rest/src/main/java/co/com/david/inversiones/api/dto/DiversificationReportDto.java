package co.com.david.inversiones.api.dto;

import co.com.david.inversiones.model.DiversificationReport;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Wire representation of a {@link DiversificationReport}. */
public record DiversificationReportDto(
        List<DiversificationViolationDto> violations,
        MoneyDto totalMarketValue,
        Map<String, String> weightByTicker,
        Map<String, String> weightBySector,
        boolean compliant) {

    public static DiversificationReportDto from(DiversificationReport report) {
        Map<String, String> byTicker = new LinkedHashMap<>();
        report.weightByTicker().forEach((ticker, weight) -> byTicker.put(ticker, DecimalStrings.of(weight.value())));
        Map<String, String> bySector = new LinkedHashMap<>();
        report.weightBySector().forEach((sector, weight) -> bySector.put(sector, DecimalStrings.of(weight.value())));

        return new DiversificationReportDto(
                report.violations().stream().map(DiversificationViolationDto::from).toList(),
                MoneyDto.from(report.totalMarketValue()),
                byTicker,
                bySector,
                report.compliant());
    }
}
