package co.com.david.inversiones.config;

import co.com.david.inversiones.model.gateways.TimeProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;

/**
 * The single {@link TimeProvider} implementation for the running application, over
 * {@link Clock#systemDefaultZone()}.
 *
 * <p>Use cases and adapters must never call {@link LocalDate#now()} or {@link Instant#now()}
 * directly — see the {@code TimeProvider} port's Javadoc — so exactly one production
 * implementation exists, wired here, and every other class asks for the current date or instant
 * through it. This also means a test can substitute a fixed clock without touching the system
 * clock.
 */
@Configuration
public class TimeProviderConfig {

    @Bean
    public Clock systemClock() {
        return Clock.systemDefaultZone();
    }

    @Bean
    public TimeProvider timeProvider(Clock clock) {
        return new TimeProvider() {
            @Override
            public LocalDate today() {
                return LocalDate.now(clock);
            }

            @Override
            public Instant now() {
                return clock.instant();
            }
        };
    }
}
