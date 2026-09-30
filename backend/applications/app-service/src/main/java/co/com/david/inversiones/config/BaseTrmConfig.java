package co.com.david.inversiones.config;

import co.com.david.inversiones.config.properties.InversionesProperties;
import co.com.david.inversiones.usecase.portfolio.CalculateTotalReturnUseCase;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.math.BigDecimal;

/**
 * The {@link BigDecimal} constructor argument {@link CalculateTotalReturnUseCase} needs: the TRM
 * the investor actually paid when converting pesos to dollars.
 *
 * <p>Task 9 is the first caller to wire {@code CalculateTotalReturnUseCase} into a controller;
 * until then nothing forced this bean to exist, so the gap was invisible. Exposed here as its own
 * named bean, rather than inlined with {@code @Value} on the use case itself, because the use case
 * has zero framework dependencies by design — it takes a plain {@code BigDecimal} constructor
 * argument, and only the application layer is allowed to know that value comes from configuration.
 *
 * <p>Task 10 folds the former direct {@code @Value("${inversiones.base-trm}")} read into
 * {@link InversionesProperties}: the value is now bound and validated (positive, non-null) once, at
 * context refresh, alongside the rest of the {@code inversiones.*} tree, instead of resolved ad hoc
 * here.
 */
@Configuration
public class BaseTrmConfig {

    @Bean
    public BigDecimal baseTrm(InversionesProperties properties) {
        return properties.baseTrm();
    }
}
