package co.com.david.inversiones.api.dto;

import co.com.david.inversiones.model.AiSynthesis;

import java.util.List;

/** Wire representation of an {@link AiSynthesis} that carries actual AI data. */
public record AiSynthesisDto(
        String ticker,
        String snapshotDate,
        String iaScore,
        String signal,
        String conviction,
        String horizon,
        String summary,
        List<String> catalysts,
        List<String> risks) {

    public static AiSynthesisDto from(AiSynthesis synthesis) {
        return new AiSynthesisDto(
                synthesis.ticker().symbol(),
                synthesis.snapshotDate().toString(),
                DecimalStrings.of(synthesis.iaScore()),
                synthesis.signal(),
                synthesis.conviction(),
                synthesis.horizon(),
                synthesis.summary(),
                synthesis.catalysts(),
                synthesis.risks());
    }
}
