package co.com.david.inversiones.api.dto;

import co.com.david.inversiones.model.TotalReturn;

import java.util.List;

/** Wire representation of a {@link TotalReturn}: the asset gain and the exchange effect, kept separate. */
public record TotalReturnResponse(
        List<ReturnSegmentDto> segments,
        ExchangeRateDto baseRate,
        ExchangeRateDto currentRate,
        MoneyDto exchangeEffect,
        MoneyDto assetGainInCop,
        MoneyDto totalReturnInCop) {

    public static TotalReturnResponse from(TotalReturn totalReturn) {
        return new TotalReturnResponse(
                totalReturn.segments().stream().map(ReturnSegmentDto::from).toList(),
                ExchangeRateDto.from(totalReturn.baseRate()),
                ExchangeRateDto.from(totalReturn.currentRate()),
                MoneyDto.from(totalReturn.exchangeEffect()),
                MoneyDto.from(totalReturn.assetGainInCop()),
                MoneyDto.from(totalReturn.totalReturnInCop()));
    }
}
