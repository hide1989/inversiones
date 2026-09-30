package co.com.david.inversiones.consumer.n8n.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.util.List;

/** Response shape of {@code /news-sentiment}: feeds {@code SignalSource.NEWS}. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record NewsSentimentResponseDto(List<Entry> tickers) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Entry(String ticker, BigDecimal scoreNoticias) {
    }
}
