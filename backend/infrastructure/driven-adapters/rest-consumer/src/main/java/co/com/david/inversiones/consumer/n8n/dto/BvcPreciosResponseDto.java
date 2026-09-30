package co.com.david.inversiones.consumer.n8n.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Response shape of {@code /bvc-precios}: Colombian Stock Exchange prices in pesos, keyed by
 * ticker symbol under {@code bvc}.
 *
 * <p>The {@code moneda} field is captured for a defensive assertion, but the currency this adapter
 * assigns to a BVC quote is {@link co.com.david.inversiones.model.Currency#COP} because of which
 * endpoint the price came from, not because this field said so — see {@code QuoteMapper}.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record BvcPreciosResponseDto(Map<String, Entry> bvc) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Entry(BigDecimal precioActual, BigDecimal cambioDiaPct, String moneda) {
    }
}
