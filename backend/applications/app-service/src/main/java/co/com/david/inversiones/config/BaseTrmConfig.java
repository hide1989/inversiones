package co.com.david.inversiones.config;

import co.com.david.inversiones.usecase.portfolio.CalculateTotalReturnUseCase;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.math.BigDecimal;

/**
 * The {@link BigDecimal} constructor argument {@link CalculateTotalReturnUseCase} needs: the TRM
 * the investor actually paid when converting pesos to dollars, read from
 * {@code inversiones.base-trm}.
 *
 * <p>Task 9 is the first caller to wire {@code CalculateTotalReturnUseCase} into a controller;
 * until then nothing forced this bean to exist, so the gap was invisible. Exposed here as its own
 * named bean, rather than inlined with {@code @Value} on the use case itself, because the use case
 * has zero framework dependencies by design — it takes a plain {@code BigDecimal} constructor
 * argument, and only the application layer is allowed to know that value comes from configuration.
 */
@Configuration
public class BaseTrmConfig {

    @Bean
    public BigDecimal baseTrm(@Value("${inversiones.base-trm}") BigDecimal baseTrm) {
        return baseTrm;
    }
}
