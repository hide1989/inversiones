package co.com.david.inversiones.api.dto;

import co.com.david.inversiones.model.EarningsEvent;

/** Wire representation of an {@link EarningsEvent}. */
public record EarningsEventDto(
        String ticker, String reportDate, long daysRemaining, String estimatedEps, String historicalSurprise) {

    public static EarningsEventDto from(EarningsEvent event) {
        return new EarningsEventDto(
                event.ticker().symbol(),
                event.reportDate().toString(),
                event.daysRemaining(),
                DecimalStrings.of(event.estimatedEps()),
                DecimalStrings.of(event.historicalSurprise()));
    }
}
