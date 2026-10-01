package co.com.david.inversiones.model.gateways;

import co.com.david.inversiones.model.ExchangeRate;

import java.util.Optional;

/** Source of the current COP/USD rate. */
public interface ExchangeRateProvider {

    /**
     * The Colombian TRM in force today, or {@link Optional#empty()} when it cannot be obtained.
     *
     * <p>"Absent means absent" applies here the same way it does to a missing quote or a NULL
     * signal score: when the real rate cannot be read, this must say so rather than substitute a
     * number that looks like a reading. A caller that needs a rate to compute something (a
     * currency-converted total, a currency effect) must treat an empty result as "unknown", never
     * as zero or as any other stand-in value.
     */
    Optional<ExchangeRate> currentCopPerUsd();
}
