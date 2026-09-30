package co.com.david.inversiones;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest
class MigrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void createsEveryTableTheAdaptersMapOnto() {
        List<String> tables = jdbc.queryForList(
                "SELECT table_name FROM information_schema.tables WHERE table_schema = 'public'",
                String.class);

        assertThat(tables).contains("users", "brokers", "instruments", "transactions",
                "exchange_rates", "signal_snapshots", "ai_syntheses");
    }

    @Test
    void storesMonetaryAmountsAtScaleTenSoTheDomainScaleSurvivesARoundTrip() {
        List<String> types = jdbc.queryForList(
                """
                SELECT data_type || '(' || numeric_precision || ',' || numeric_scale || ')'
                FROM information_schema.columns
                WHERE table_name = 'transactions' AND column_name IN ('unit_price', 'fees')
                """,
                String.class);

        assertThat(types).isNotEmpty().allMatch(type -> type.equals("numeric(38,10)"));
    }

    @Test
    void seedsTheCurrentPortfolioSoTheDashboardHasSomethingToShow() {
        Integer transactions = jdbc.queryForObject("SELECT count(*) FROM transactions", Integer.class);
        Integer instruments = jdbc.queryForObject("SELECT count(*) FROM instruments", Integer.class);

        assertThat(instruments).isGreaterThanOrEqualTo(18);
        assertThat(transactions).isGreaterThanOrEqualTo(18);
    }

    @Test
    void seedsExactlyOneUserWithAHashedPassword() {
        List<String> hashes = jdbc.queryForList("SELECT password_hash FROM users", String.class);

        assertThat(hashes).hasSize(1);
        assertThat(hashes.getFirst()).startsWith("$2");
    }

    @Test
    void rejectsTwoInstrumentsWithTheSameTicker() {
        List<String> indexes = jdbc.queryForList(
                """
                SELECT indexdef FROM pg_indexes
                WHERE tablename = 'instruments' AND indexdef LIKE '%UNIQUE%'
                """,
                String.class);

        assertThat(indexes).anyMatch(definition -> definition.contains("ticker"));
    }
}
