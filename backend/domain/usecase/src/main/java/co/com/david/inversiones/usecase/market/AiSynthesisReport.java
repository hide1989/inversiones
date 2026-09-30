package co.com.david.inversiones.usecase.market;

import co.com.david.inversiones.model.AiSynthesis;
import co.com.david.inversiones.model.Ticker;

import java.math.BigDecimal;
import java.util.List;

/**
 * The result of {@link GetAiSynthesisUseCase}: syntheses that actually carry AI data, the average
 * IA score computed only from those, and the tickers n8n marked {@code sin_datos}.
 */
public record AiSynthesisReport(List<AiSynthesis> withData, List<Ticker> withoutData, BigDecimal averageScore) {
}
