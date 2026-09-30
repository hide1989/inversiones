package co.com.david.inversiones.api.controller;

import co.com.david.inversiones.api.ApiTestContext;
import co.com.david.inversiones.model.*;
import co.com.david.inversiones.usecase.signal.GetSignalHistoryUseCase;
import co.com.david.inversiones.usecase.signal.GetSignalsDashboardUseCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = {ApiTestContext.class, SignalController.class}, webEnvironment = SpringBootTest.WebEnvironment.MOCK)
class SignalControllerTest {

    @Autowired
    private WebApplicationContext webApplicationContext;
    @Autowired
    private JwtEncoder jwtEncoder;

    @MockitoBean
    private GetSignalsDashboardUseCase getSignalsDashboard;
    @MockitoBean
    private GetSignalHistoryUseCase getSignalHistory;

    private MockMvc mockMvc;

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
}
