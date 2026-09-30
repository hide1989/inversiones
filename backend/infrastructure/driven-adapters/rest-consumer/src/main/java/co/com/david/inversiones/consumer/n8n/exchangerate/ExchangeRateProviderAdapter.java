package co.com.david.inversiones.consumer.n8n.exchangerate;

import co.com.david.inversiones.model.ExchangeRate;
import co.com.david.inversiones.model.gateways.ExchangeRateProvider;
import org.springframework.stereotype.Component;

/** {@link ExchangeRateProvider} backed by n8n's {@code /trm-hoy} workflow. */
@Component
public class ExchangeRateProviderAdapter implements ExchangeRateProvider {

    private final ExchangeRateCacheSource cacheSource;

    public ExchangeRateProviderAdapter(ExchangeRateCacheSource cacheSource) {
        this.cacheSource = cacheSource;
    }

    @Override
    public ExchangeRate currentCopPerUsd() {
        return cacheSource.currentRate();
    }
}
