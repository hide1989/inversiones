package co.com.david.inversiones.api.dto;

import co.com.david.inversiones.model.ExchangeRate;

/** Wire representation of an {@link ExchangeRate}. */
public record ExchangeRateDto(String base, String quote, String rate, String rateDate) {

    public static ExchangeRateDto from(ExchangeRate rate) {
        return new ExchangeRateDto(
                rate.base().name(), rate.quote().name(), DecimalStrings.of(rate.rate()), rate.rateDate().toString());
    }
}
