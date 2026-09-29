package co.com.david.inversiones.model;

import co.com.david.inversiones.model.exception.CurrencyMismatchException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

/**
 * A currency conversion rate valid on a given date.
 *
 * <p>The rate is expressed as units of {@code quote} per one unit of {@code base}. For the
 * Colombian market this is the TRM: COP per USD.
 */
public record ExchangeRate(Currency base, Currency quote, BigDecimal rate, LocalDate rateDate) {

    public ExchangeRate {
        Objects.requireNonNull(base, "base must not be null");
        Objects.requireNonNull(quote, "quote must not be null");
        Objects.requireNonNull(rate, "rate must not be null");
        Objects.requireNonNull(rateDate, "rateDate must not be null");
    }

    /** The Colombian TRM: Colombian pesos per one US dollar. */
    public static ExchangeRate copPerUsd(BigDecimal rate, LocalDate rateDate) {
        return new ExchangeRate(Currency.USD, Currency.COP, rate, rateDate);
    }

    /** @throws CurrencyMismatchException when {@code amount} is not in the base currency. */
    public Money convert(Money amount) {
        if (amount.currency() != base) {
            throw new CurrencyMismatchException(base, amount.currency());
        }
        return Money.of(amount.amount().multiply(rate), quote);
    }

    /** How many quote-currency units this rate sits above {@code reference}. */
    public BigDecimal difference(ExchangeRate reference) {
        return rate.subtract(reference.rate());
    }
}
