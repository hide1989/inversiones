package co.com.david.inversiones.consumer.n8n.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.util.List;

/** Response shape of {@code /reddit} (ApeWisdom WSB mentions): feeds {@code SignalSource.WSB}. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record RedditResponseDto(List<Entry> tickers) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Entry(String ticker, BigDecimal scoreReddit) {
    }
}
