package co.com.david.inversiones.consumer.n8n.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.util.List;

/** Response shape of {@code /stocktwits}: feeds {@code SignalSource.SOCIAL}. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record StocktwitsResponseDto(List<Entry> tickers) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Entry(String ticker, BigDecimal scoreSocial) {
    }
}
