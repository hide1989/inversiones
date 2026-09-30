package co.com.david.inversiones.config;

import co.com.david.inversiones.model.gateways.TimeProvider;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves the mechanism every time-dependent test in this project relies on: substituting a fixed
 * {@link Clock} into the production {@link TimeProviderConfig#timeProvider(Clock)} factory method
 * makes {@link TimeProvider#today()} and {@link TimeProvider#now()} deterministic, regardless of
 * the real wall clock.
 */
class TimeProviderConfigTest {

    private final TimeProviderConfig config = new TimeProviderConfig();

    @Test
    void aFixedClockMakesTodayAndNowRepeatable() {
        Instant fixedInstant = Instant.parse("2026-09-30T10:15:30.00Z");
        Clock fixedClock = Clock.fixed(fixedInstant, ZoneOffset.UTC);

        TimeProvider timeProvider = config.timeProvider(fixedClock);

        assertThat(timeProvider.today()).isEqualTo(LocalDate.of(2026, 9, 30));
        assertThat(timeProvider.today()).isEqualTo(timeProvider.today());
        assertThat(timeProvider.now()).isEqualTo(fixedInstant);
        assertThat(timeProvider.now()).isEqualTo(timeProvider.now());
    }

    @Test
    void differentFixedClocksProduceDifferentDeterministicDates() {
        TimeProvider yesterday = config.timeProvider(
                Clock.fixed(Instant.parse("2026-09-29T23:59:59Z"), ZoneOffset.UTC));
        TimeProvider today = config.timeProvider(
                Clock.fixed(Instant.parse("2026-09-30T00:00:01Z"), ZoneOffset.UTC));

        assertThat(yesterday.today()).isEqualTo(LocalDate.of(2026, 9, 29));
        assertThat(today.today()).isEqualTo(LocalDate.of(2026, 9, 30));
    }
}
