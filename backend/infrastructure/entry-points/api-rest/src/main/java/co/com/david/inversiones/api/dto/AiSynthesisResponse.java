package co.com.david.inversiones.api.dto;

import co.com.david.inversiones.usecase.market.AiSynthesisReport;

import java.util.List;

/** Wire representation of an {@link AiSynthesisReport}. */
public record AiSynthesisResponse(List<AiSynthesisDto> withData, List<String> withoutData, String averageScore) {

    public static AiSynthesisResponse from(AiSynthesisReport report) {
        return new AiSynthesisResponse(
                report.withData().stream().map(AiSynthesisDto::from).toList(),
                report.withoutData().stream().map(t -> t.symbol()).toList(),
                DecimalStrings.of(report.averageScore()));
    }
}
