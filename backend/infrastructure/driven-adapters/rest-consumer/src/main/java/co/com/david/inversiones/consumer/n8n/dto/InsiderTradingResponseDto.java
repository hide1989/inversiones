package co.com.david.inversiones.consumer.n8n.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.util.List;

/**
 * Response shape of {@code /insider-trading}: feeds {@code SignalSource.INSIDER}, under
 * {@code insiders} rather than {@code tickers}.
 *
 * <p>{@code insiderScore} is {@code null} when there is no recent insider activity for a ticker.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record InsiderTradingResponseDto(List<Entry> insiders) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Entry(String ticker, BigDecimal insiderScore) {
    }
}
