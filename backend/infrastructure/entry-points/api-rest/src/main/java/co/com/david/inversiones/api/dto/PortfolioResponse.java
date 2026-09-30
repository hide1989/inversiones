package co.com.david.inversiones.api.dto;

import co.com.david.inversiones.model.PortfolioValuation;

import java.util.List;

/**
 * Wire representation of a {@link PortfolioValuation}: every open position, valued, plus the
 * portfolio-wide totals.
 *
 * <p>{@code baseCurrency} is declared explicitly so a client never has to guess which currency the
 * totals are expressed in.
 */
public record PortfolioResponse(
        List<PositionDto> positions,
        MoneyDto totalInvested,
        MoneyDto totalMarketValue,
        MoneyDto totalUnrealizedPnl,
        List<String> unpricedTickers,
        String baseCurrency) {

    public static PortfolioResponse from(PortfolioValuation valuation) {
        return new PortfolioResponse(
                valuation.positions().stream().map(PositionDto::from).toList(),
                MoneyDto.from(valuation.totalInvested()),
                MoneyDto.from(valuation.totalMarketValue()),
                MoneyDto.from(valuation.totalUnrealizedPnl()),
                valuation.unpricedTickers().stream().map(t -> t.symbol()).toList(),
                valuation.baseCurrency().name());
    }
}
