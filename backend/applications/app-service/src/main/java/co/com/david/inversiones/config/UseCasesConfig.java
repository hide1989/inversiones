package co.com.david.inversiones.config;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;

/**
 * Registers every use case as a bean.
 *
 * <p>Scanned lazily on purpose, and only here. Use cases depend on gateway ports whose adapters are
 * still being implemented, so eager wiring would fail startup on a port that has no adapter yet.
 * Deferring only this scan keeps eager validation intact for everything else — the datasource, Flyway,
 * the REST client and the security filters all still fail fast at startup, which is where a wiring
 * mistake should surface.
 *
 * <p>TODO: remove {@code lazyInit} once every gateway has an adapter; at that point eager wiring is
 * the stronger guarantee and this scan should fail startup like the rest of the context.
 */
@Configuration
@ComponentScan(basePackages = "co.com.david.inversiones.usecase",
        includeFilters = {
                @ComponentScan.Filter(type = FilterType.REGEX, pattern = "^.+UseCase$")
        },
        useDefaultFilters = false,
        lazyInit = true)
public class UseCasesConfig {
}
