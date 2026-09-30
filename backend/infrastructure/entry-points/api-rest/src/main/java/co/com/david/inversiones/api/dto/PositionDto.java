package co.com.david.inversiones.api.dto;

import co.com.david.inversiones.model.ValuedPosition;

/** Wire representation of a {@link ValuedPosition}: a holding valued against a current quote. */
public record PositionDto(
        String ticker,
        String brokerName,
        String quantity,
        MoneyDto totalCost,
        MoneyDto averageCost,
        MoneyDto realizedPnl,
        MoneyDto marketValue,
        MoneyDto unrealizedPnl,
        String unrealizedPnlPercent,
        String dayChangePercent,
        boolean priced,
        MoneyDto costInBaseCurrency,
        MoneyDto marketValueInBaseCurrency) {

    public static PositionDto from(ValuedPosition valued) {
        var position = valued.position();
        return new PositionDto(
                position.ticker().symbol(),
                position.brokerName(),
                position.quantity().value().toPlainString(),
                MoneyDto.from(position.totalCost()),
                MoneyDto.from(position.averageCost()),
                MoneyDto.from(position.realizedPnl()),
                MoneyDto.from(valued.marketValue()),
                MoneyDto.from(valued.unrealizedPnl()),
                DecimalStrings.of(valued.unrealizedPnlPercent().value()),
                DecimalStrings.of(valued.dayChangePercent().value()),
                valued.priced(),
                MoneyDto.from(valued.costInBaseCurrency()),
                MoneyDto.from(valued.marketValueInBaseCurrency()));
    }
}
