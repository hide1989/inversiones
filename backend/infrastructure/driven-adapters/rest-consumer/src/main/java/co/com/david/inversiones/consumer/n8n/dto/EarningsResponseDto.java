package co.com.david.inversiones.consumer.n8n.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Response shape of {@code /earnings} (WF-13): feeds {@code EarningsProvider}.
 *
 * <p>{@code diasRestantes} is deliberately not modeled here: it is computed by the upstream
 * workflow from its own server clock at fetch time, and {@code GetEarningsUseCase} recomputes it
 * itself from an injected {@code TimeProvider} instead (see that class's Javadoc for why).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record EarningsResponseDto(List<Entry> tickers) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Entry(
            String ticker,
            LocalDate proximoReporte,
            BigDecimal epsEstimado,
            BigDecimal sorpresaPromedioPct) {
    }
}
