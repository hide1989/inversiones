package co.com.david.inversiones.model.gateways;

import co.com.david.inversiones.model.ExchangeRate;

/** Source of the current COP/USD rate. */
public interface ExchangeRateProvider {

    /** The Colombian TRM in force today. */
    ExchangeRate currentCopPerUsd();
}
