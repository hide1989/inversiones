package co.com.david.inversiones.consumer.n8n.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.util.List;

/**
 * Response shape of {@code /ratings-analistas}: feeds {@code SignalSource.ANALYST}.
 *
 * <p>{@code ratingScore} is {@code null} for a ticker analysts have not covered ({@code consenso}
 * {@code "SIN DATOS"}), which the mapper treats as an absent source rather than a zero score.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record RatingsAnalistasResponseDto(List<Entry> tickers) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Entry(String ticker, BigDecimal ratingScore) {
    }
}
