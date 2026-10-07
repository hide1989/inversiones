package co.com.david.inversiones.api.controller;

import co.com.david.inversiones.api.ApiTestContext;
import co.com.david.inversiones.model.*;
import co.com.david.inversiones.model.gateways.SignalProvider;
import co.com.david.inversiones.model.gateways.SignalSnapshotRepository;
import co.com.david.inversiones.model.gateways.TimeProvider;
import co.com.david.inversiones.model.gateways.TransactionRepository;
import co.com.david.inversiones.usecase.signal.GetSignalHistoryUseCase;
import co.com.david.inversiones.usecase.signal.GetSignalsDashboardUseCase;
import co.com.david.inversiones.usecase.signal.RefreshSignalSnapshotUseCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(
        classes = {ApiTestContext.class, SignalController.class, SignalControllerTest.RefreshUseCaseTestConfig.class},
        webEnvironment = SpringBootTest.WebEnvironment.MOCK)
class SignalControllerTest {

    @Autowired
    private WebApplicationContext webApplicationContext;
    @Autowired
    private JwtEncoder jwtEncoder;

    @MockitoBean
    private GetSignalsDashboardUseCase getSignalsDashboard;
    @MockitoBean
    private GetSignalHistoryUseCase getSignalHistory;

    /**
     * {@link RefreshSignalSnapshotUseCase} is wired as a real bean (see
     * {@link RefreshUseCaseTestConfig}), not mocked — the overlap guard this task adds lives
     * inside that class, so mocking it away would make the concurrency test below assert nothing
     * about the actual guard. Only its gateway dependencies are mocked.
     */
    @MockitoBean
    private TransactionRepository transactionRepository;
    @MockitoBean
    private SignalProvider signalProvider;
    @MockitoBean
    private SignalSnapshotRepository snapshotRepository;
    @MockitoBean
    private TimeProvider timeProvider;

    private MockMvc mockMvc;

    /** Supplies the real {@link RefreshSignalSnapshotUseCase}, built from the mocked gateways above. */
    @Configuration
    static class RefreshUseCaseTestConfig {
        @Bean
        RefreshSignalSnapshotUseCase refreshSignalSnapshotUseCase(
                TransactionRepository transactionRepository, SignalProvider signalProvider,
                SignalSnapshotRepository snapshotRepository, TimeProvider timeProvider) {
            return new RefreshSignalSnapshotUseCase(transactionRepository, signalProvider, snapshotRepository, timeProvider);
        }
    }

    private static Transaction holding(Ticker ticker) {
        return Transaction.builder()
                .ticker(ticker).brokerName("Hapi").type(TransactionType.BUY)
                .quantity(Quantity.of("1")).unitPrice(Money.usd("100.00"))
                .tradedAt(LocalDate.of(2026, 1, 2)).build();
    }

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).apply(springSecurity()).build();
    }

    private String token() {
        return "Bearer " + ApiTestContext.issueToken(jwtEncoder, "david");
    }

    @Test
    void dashboardWithoutATokenIsRejected() throws Exception {
        mockMvc.perform(get("/api/v1/signals")).andExpect(status().isUnauthorized());
    }

    @Test
    void dashboardReturnsTheCombinedScoreTable() throws Exception {
        Ticker ticker = Ticker.of("AAPL");
        SignalSnapshot snapshot = SignalSnapshot.builder()
                .ticker(ticker).snapshotDate(LocalDate.of(2026, 9, 29))
                .scores(Map.of(SignalSource.TECHNICAL, new BigDecimal("8.0"))).build();
        CombinedScore score = CombinedScore.from(snapshot);
        SignalsDashboard dashboard = SignalsDashboard.builder()
                .signals(List.of(new TickerSignal(ticker, snapshot, score)))
                .averageScore(score.value())
                .levelCounts(Map.of(score.level(), 1))
                .tickersWithoutData(List.of())
                .build();
        when(getSignalsDashboard.execute()).thenReturn(dashboard);

        mockMvc.perform(get("/api/v1/signals").header("Authorization", token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.signals[0].ticker").value("AAPL"))
                .andExpect(jsonPath("$.signals[0].score.value").isString());
    }

    @Test
    void historyWithoutATokenIsRejected() throws Exception {
        mockMvc.perform(get("/api/v1/signals/history?ticker=AAPL&from=2026-01-01&to=2026-09-29"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void historyReturnsSnapshotsBetweenTheGivenDates() throws Exception {
        Ticker ticker = Ticker.of("AAPL");
        SignalSnapshot snapshot = SignalSnapshot.builder()
                .ticker(ticker).snapshotDate(LocalDate.of(2026, 5, 1)).scores(Map.of()).build();
        when(getSignalHistory.execute(ticker, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 9, 29)))
                .thenReturn(List.of(snapshot));

        mockMvc.perform(get("/api/v1/signals/history?ticker=AAPL&from=2026-01-01&to=2026-09-29")
                        .header("Authorization", token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].ticker").value("AAPL"))
                .andExpect(jsonPath("$[0].snapshotDate").value("2026-05-01"));
    }

    @Test
    void refreshWithoutATokenIsRejected() throws Exception {
        mockMvc.perform(post("/api/v1/signals/refresh")).andExpect(status().isUnauthorized());
    }

    @Test
    void refreshRunsAndReturnsTheStoredCount() throws Exception {
        Ticker ticker = Ticker.of("AAPL");
        SignalSnapshot fetched = SignalSnapshot.builder()
                .ticker(ticker).snapshotDate(LocalDate.of(2026, 9, 29))
                .scores(Map.of(SignalSource.TECHNICAL, new BigDecimal("8"))).build();
        when(transactionRepository.findAll()).thenReturn(List.of(holding(ticker)));
        when(timeProvider.today()).thenReturn(LocalDate.of(2026, 9, 29));
        when(signalProvider.fetchSnapshots(Set.of(ticker), LocalDate.of(2026, 9, 29))).thenReturn(List.of(fetched));
        when(snapshotRepository.saveAll(List.of(fetched))).thenReturn(List.of(fetched));

        mockMvc.perform(post("/api/v1/signals/refresh").header("Authorization", token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.snapshotsStored").value(1));
    }

    /**
     * Proves the guard genuinely excludes a second, concurrent request rather than merely
     * asserting a call count. The first request is blocked inside the stubbed
     * {@link SignalProvider} until the second request has actually been issued and observed the
     * guard held — only then is the first released to finish.
     */
    @Test
    void aSecondRefreshWhileOneIsInFlightIsRefusedWithConflict() throws Exception {
        Ticker ticker = Ticker.of("AAPL");
        CountDownLatch firstRequestEnteredProvider = new CountDownLatch(1);
        CountDownLatch releaseFirstRequest = new CountDownLatch(1);
        when(transactionRepository.findAll()).thenReturn(List.of(holding(ticker)));
        when(timeProvider.today()).thenReturn(LocalDate.of(2026, 9, 29));
        when(signalProvider.fetchSnapshots(Set.of(ticker), LocalDate.of(2026, 9, 29))).thenAnswer(invocation -> {
            firstRequestEnteredProvider.countDown();
            releaseFirstRequest.await(5, TimeUnit.SECONDS);
            return List.of();
        });

        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            Future<MvcResult> firstRequest = pool.submit(() ->
                    mockMvc.perform(post("/api/v1/signals/refresh").header("Authorization", token())).andReturn());
            assertThat(firstRequestEnteredProvider.await(5, TimeUnit.SECONDS)).isTrue();

            mockMvc.perform(post("/api/v1/signals/refresh").header("Authorization", token()))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.status").value(409));

            releaseFirstRequest.countDown();
            assertThat(firstRequest.get(5, TimeUnit.SECONDS).getResponse().getStatus()).isEqualTo(200);
        } finally {
            pool.shutdown();
        }
    }
}
