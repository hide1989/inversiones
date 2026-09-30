package co.com.david.inversiones.config;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;

/**
 * Registers every use case as a bean.
 *
 * <p>Eagerly, like the rest of the context. Every gateway port the use cases depend on now has an
 * adapter — the last one, {@code TimeProvider}, was wired in {@link TimeProviderConfig} — so there
 * is no longer a reason to defer instantiation past startup. Lazy scanning used to mean a missing
 * or miswired bean surfaced on the first HTTP request instead of at boot, which lets a deployment
 * that will fail every request still pass a health check; eager wiring here restores the same
 * fail-fast guarantee the datasource, Flyway, the REST client and the security filters already had.
 */
@Configuration
@ComponentScan(basePackages = "co.com.david.inversiones.usecase",
        includeFilters = {
                @ComponentScan.Filter(type = FilterType.REGEX, pattern = "^.+UseCase$")
        },
        useDefaultFilters = false)
public class UseCasesConfig {
}
