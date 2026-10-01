package co.com.david.inversiones.api.dto;

import co.com.david.inversiones.model.TotalReturn;

import java.util.List;

/**
 * Wire representation of a {@link TotalReturn}: the asset gain and the exchange effect, kept
 * separate.
 *
 * <p>{@code currentRate}, {@code exchangeEffect}, {@code assetGainInCop} and
 * {@code totalReturnInCop} are all nullable: they are {@code null} exactly when the TRM source was
 * unreachable, and {@code currencyEffectAvailable} makes that unambiguous rather than leaving a
 * client to guess whether a missing field means "unavailable" or a bug. A client must never treat
 * a missing {@code exchangeEffect} as zero.
 */
public record TotalReturnResponse(
        List<ReturnSegmentDto> segments,
        ExchangeRateDto baseRate,
        ExchangeRateDto currentRate,
        boolean currencyEffectAvailable,
        MoneyDto exchangeEffect,
        MoneyDto assetGainInCop,
        MoneyDto totalReturnInCop) {

    public static TotalReturnResponse from(TotalReturn totalReturn) {
        return new TotalReturnResponse(
                totalReturn.segments().stream().map(ReturnSegmentDto::from).toList(),
                ExchangeRateDto.from(totalReturn.baseRate()),
                totalReturn.currentRate().map(ExchangeRateDto::from).orElse(null),
                totalReturn.currentRate().isPresent(),
                totalReturn.exchangeEffect().map(MoneyDto::from).orElse(null),
                totalReturn.assetGainInCop().map(MoneyDto::from).orElse(null),
                totalReturn.totalReturnInCop().map(MoneyDto::from).orElse(null));
    }
}
