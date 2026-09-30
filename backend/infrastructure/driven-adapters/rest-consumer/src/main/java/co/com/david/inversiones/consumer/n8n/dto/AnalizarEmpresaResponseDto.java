package co.com.david.inversiones.consumer.n8n.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;

/**
 * Response shape of {@code POST /analizar-empresa} (WF-06): feeds {@code FundamentalsProvider} for
 * equities. {@code esEtf} tells the adapter whether {@code /etf-ficha} (WF-08) must also be called:
 * an ETF ticker still comes back from this endpoint, but its multiples are mostly {@code null}
 * because Finnhub does not carry them for funds — {@code /etf-ficha} is the workflow that actually
 * has beta, dividend yield and the price-based multiples for an ETF.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AnalizarEmpresaResponseDto(
        String ticker,
        Boolean esEtf,
        Valoracion valoracion,
        Rentabilidad rentabilidad,
        Crecimiento crecimiento,
        SaludFinanciera saludFinanciera,
        Dividendos dividendos) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Valoracion(BigDecimal per, BigDecimal peg, BigDecimal pb, BigDecimal ps, BigDecimal evEbitda) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Rentabilidad(BigDecimal roe, BigDecimal margenNeto) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Crecimiento(BigDecimal revenueCagr5y, BigDecimal epsCagr5y) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SaludFinanciera(BigDecimal deudaEquity, BigDecimal currentRatio, BigDecimal beta) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Dividendos(BigDecimal yield) {
    }
}
