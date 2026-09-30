package co.com.david.inversiones.consumer.n8n.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Response shape of {@code /precios-hoy}: NYSE/NASDAQ prices in US dollars, keyed by ticker symbol
 * under {@code datos}.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PreciosHoyResponseDto(Map<String, Entry> datos) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Entry(BigDecimal precioActual, BigDecimal cambioPct) {
    }
}
