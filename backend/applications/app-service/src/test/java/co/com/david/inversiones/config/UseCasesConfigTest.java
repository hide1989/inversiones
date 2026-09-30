package co.com.david.inversiones.config;

import co.com.david.inversiones.usecase.auth.AuthenticateUserUseCase;
import co.com.david.inversiones.usecase.market.GetAiSynthesisUseCase;
import co.com.david.inversiones.usecase.market.GetEarningsUseCase;
import co.com.david.inversiones.usecase.market.GetFundamentalsUseCase;
import co.com.david.inversiones.usecase.market.GetWatchlistUseCase;
import co.com.david.inversiones.usecase.portfolio.CalculateTotalReturnUseCase;
import co.com.david.inversiones.usecase.portfolio.EvaluateDiversificationUseCase;
import co.com.david.inversiones.usecase.portfolio.GetPortfolioUseCase;
import co.com.david.inversiones.usecase.signal.GetSignalHistoryUseCase;
import co.com.david.inversiones.usecase.signal.GetSignalsDashboardUseCase;
import co.com.david.inversiones.usecase.signal.RefreshSignalSnapshotUseCase;
import co.com.david.inversiones.usecase.transaction.DeleteTransactionUseCase;
import co.com.david.inversiones.usecase.transaction.ListTransactionsUseCase;
import co.com.david.inversiones.usecase.transaction.RegisterTransactionUseCase;
import co.com.david.inversiones.usecase.transaction.UpdateTransactionUseCase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves the full application context starts eagerly and wires every use case as a named bean.
 *
 * <p>Until Task 10, {@link UseCasesConfig} scanned its use cases lazily, and this class's only test
 * caught {@code UnsatisfiedDependencyException} and asserted {@code true} regardless — it could not
 * fail. Now that every gateway port has an adapter, {@code lazyInit} is gone and this test starts
 * the real {@code @SpringBootTest} context (against Testcontainers PostgreSQL, the same setup
 * {@code MigrationTest} uses) and asserts, one by one and by bean name, that every use case the
 * domain layer exposes is present. A context that fails to start fails this test; a context that
 * starts but is missing a use case bean also fails this test — either way, this is no longer
 * tautological.
 */
@Testcontainers
@SpringBootTest
class UseCasesConfigTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private ApplicationContext context;

    @Test
    void registersAuthenticateUserUseCase() {
        assertThat(context.getBean("authenticateUserUseCase", AuthenticateUserUseCase.class)).isNotNull();
    }

    @Test
    void registersGetAiSynthesisUseCase() {
        assertThat(context.getBean("getAiSynthesisUseCase", GetAiSynthesisUseCase.class)).isNotNull();
    }

    @Test
    void registersGetEarningsUseCase() {
        assertThat(context.getBean("getEarningsUseCase", GetEarningsUseCase.class)).isNotNull();
    }

    @Test
    void registersGetFundamentalsUseCase() {
        assertThat(context.getBean("getFundamentalsUseCase", GetFundamentalsUseCase.class)).isNotNull();
    }

    @Test
    void registersGetWatchlistUseCase() {
        assertThat(context.getBean("getWatchlistUseCase", GetWatchlistUseCase.class)).isNotNull();
    }

    @Test
    void registersCalculateTotalReturnUseCase() {
        assertThat(context.getBean("calculateTotalReturnUseCase", CalculateTotalReturnUseCase.class)).isNotNull();
    }

    @Test
    void registersEvaluateDiversificationUseCase() {
        assertThat(context.getBean("evaluateDiversificationUseCase", EvaluateDiversificationUseCase.class))
                .isNotNull();
    }

    @Test
    void registersGetPortfolioUseCase() {
        assertThat(context.getBean("getPortfolioUseCase", GetPortfolioUseCase.class)).isNotNull();
    }

    @Test
    void registersGetSignalHistoryUseCase() {
        assertThat(context.getBean("getSignalHistoryUseCase", GetSignalHistoryUseCase.class)).isNotNull();
    }

    @Test
    void registersGetSignalsDashboardUseCase() {
        assertThat(context.getBean("getSignalsDashboardUseCase", GetSignalsDashboardUseCase.class)).isNotNull();
    }

    @Test
    void registersRefreshSignalSnapshotUseCase() {
        assertThat(context.getBean("refreshSignalSnapshotUseCase", RefreshSignalSnapshotUseCase.class)).isNotNull();
    }

    @Test
    void registersDeleteTransactionUseCase() {
        assertThat(context.getBean("deleteTransactionUseCase", DeleteTransactionUseCase.class)).isNotNull();
    }

    @Test
    void registersListTransactionsUseCase() {
        assertThat(context.getBean("listTransactionsUseCase", ListTransactionsUseCase.class)).isNotNull();
    }

    @Test
    void registersRegisterTransactionUseCase() {
        assertThat(context.getBean("registerTransactionUseCase", RegisterTransactionUseCase.class)).isNotNull();
    }

    @Test
    void registersUpdateTransactionUseCase() {
        assertThat(context.getBean("updateTransactionUseCase", UpdateTransactionUseCase.class)).isNotNull();
    }
}
