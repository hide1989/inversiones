package co.com.david.inversiones.consumer.n8n.fundamentals;

import co.com.david.inversiones.consumer.n8n.dto.AnalizarEmpresaResponseDto;
import co.com.david.inversiones.consumer.n8n.dto.EtfFichaResponseDto;
import co.com.david.inversiones.model.Fundamentals;
import co.com.david.inversiones.model.Ticker;

import java.util.Optional;

/**
 * Maps {@code /analizar-empresa} and {@code /etf-ficha} responses into {@link Fundamentals}.
 *
 * <p>An equity ({@code es_etf == false}) is mapped from {@code /analizar-empresa} alone: it already
 * carries the full multiple set. An ETF is mapped from {@code /etf-ficha} when that second call
 * succeeded, since that workflow is the one with beta, dividend yield and the price multiples for a
 * fund; if the second call failed, this mapper still returns a {@code Fundamentals} with {@code
 * hasData() == true} (the ticker itself was recognized and confirmed to be an ETF) but every
 * multiple {@code null}, rather than discarding the fact that we know it exists.
 */
final class FundamentalsMapper {

    private FundamentalsMapper() {
    }

    static Fundamentals fromEquity(Ticker ticker, AnalizarEmpresaResponseDto dto) {
        AnalizarEmpresaResponseDto.Valoracion valoracion = dto.valoracion();
        AnalizarEmpresaResponseDto.Rentabilidad rentabilidad = dto.rentabilidad();
        AnalizarEmpresaResponseDto.Crecimiento crecimiento = dto.crecimiento();
        AnalizarEmpresaResponseDto.SaludFinanciera salud = dto.saludFinanciera();
        AnalizarEmpresaResponseDto.Dividendos dividendos = dto.dividendos();

        return Fundamentals.builder()
                .ticker(ticker)
                .isEtf(false)
                .hasData(true)
                .per(valoracion == null ? null : valoracion.per())
                .peg(valoracion == null ? null : valoracion.peg())
                .priceToBook(valoracion == null ? null : valoracion.pb())
                .priceToSales(valoracion == null ? null : valoracion.ps())
                .evToEbitda(valoracion == null ? null : valoracion.evEbitda())
                .roe(rentabilidad == null ? null : rentabilidad.roe())
                .netMargin(rentabilidad == null ? null : rentabilidad.margenNeto())
                .revenueCagr5y(crecimiento == null ? null : crecimiento.revenueCagr5y())
                .epsCagr5y(crecimiento == null ? null : crecimiento.epsCagr5y())
                .debtToEquity(salud == null ? null : salud.deudaEquity())
                .beta(salud == null ? null : salud.beta())
                .currentRatio(salud == null ? null : salud.currentRatio())
                .dividendYield(dividendos == null ? null : dividendos.yield())
                .build();
    }

    static Fundamentals fromEtf(Ticker ticker, Optional<EtfFichaResponseDto> dto) {
        EtfFichaResponseDto.Metricas metricas = dto.map(EtfFichaResponseDto::metricas).orElse(null);

        return Fundamentals.builder()
                .ticker(ticker)
                .isEtf(true)
                .hasData(true)
                .per(metricas == null ? null : metricas.per())
                .priceToBook(metricas == null ? null : metricas.pb())
                .beta(metricas == null ? null : metricas.beta())
                .dividendYield(metricas == null ? null : metricas.dividendYield())
                .build();
    }
}
