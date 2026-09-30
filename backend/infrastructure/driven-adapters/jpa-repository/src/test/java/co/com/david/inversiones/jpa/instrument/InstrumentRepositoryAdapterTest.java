package co.com.david.inversiones.jpa.instrument;

import co.com.david.inversiones.model.Currency;
import co.com.david.inversiones.model.Instrument;
import co.com.david.inversiones.model.Ticker;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link InstrumentRepositoryAdapter} against a real PostgreSQL instance with
 * Flyway applying the real migrations, so the tests read the actual seeded catalog.
 */
@Testcontainers
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(InstrumentRepositoryAdapter.class)
class InstrumentRepositoryAdapterTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired
    private InstrumentRepositoryAdapter adapter;

    @Test
    void findByTickerFindsTheSeededQqqUsingANormalizedSymbol() {
        Optional<Instrument> found = adapter.findByTicker(Ticker.of("qqq"));

        assertThat(found).isPresent();
        assertThat(found.orElseThrow().ticker()).isEqualTo(Ticker.of("QQQ"));
    }

    @Test
    void findAllReturnsAtLeastTwentySeededInstruments() {
        List<Instrument> all = adapter.findAll();

        assertThat(all.size()).isGreaterThanOrEqualTo(20);
    }

    @Test
    void theTerpelInstrumentIsListedOnTheBvcInCop() {
        Instrument terpel = adapter.findByTicker(Ticker.of("TERPEL")).orElseThrow();

        assertThat(terpel.exchange()).isEqualTo("BVC");
        assertThat(terpel.currency()).isEqualTo(Currency.COP);
    }
}
