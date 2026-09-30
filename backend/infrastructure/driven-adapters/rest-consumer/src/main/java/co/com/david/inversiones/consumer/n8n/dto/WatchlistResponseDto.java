package co.com.david.inversiones.consumer.n8n.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.util.List;

/**
 * Response shape of {@code /watchlist} (WF-18): feeds {@code WatchlistProvider}.
 *
 * <p>{@code precio}, {@code scoreTecnico} and {@code scoreSocial} are frequently {@code null}: many
 * rows are WSB-trending symbols n8n has social data for but has not priced or scored technically
 * yet. {@code señal} is read via an explicit {@link JsonProperty} for the same reason as in
 * {@link SintesisIaResponseDto}.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record WatchlistResponseDto(List<Entry> watchlist) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Entry(
            String ticker,
            BigDecimal precio,
            BigDecimal scoreTecnico,
            BigDecimal scoreSocial,
            BigDecimal scoreTotal,
            @JsonProperty("señal") String senal) {
    }
}
