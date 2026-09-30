package co.com.david.inversiones.consumer.n8n.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;

/**
 * Response shape of {@code POST /etf-ficha} (WF-08): feeds {@code FundamentalsProvider} for the
 * tickers {@code /analizar-empresa} reported as {@code es_etf}. Only carries the price-based
 * multiples and beta/dividend yield — no revenue, margin or growth figures, which do not apply to
 * a fund the way they do to a company.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record EtfFichaResponseDto(String ticker, Metricas metricas) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Metricas(BigDecimal beta, BigDecimal dividendYield, BigDecimal per, BigDecimal pb) {
    }
}
