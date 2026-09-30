package co.com.david.inversiones.api.controller;

import co.com.david.inversiones.api.ApiTestContext;
import co.com.david.inversiones.model.EarningsEvent;
import co.com.david.inversiones.model.Fundamentals;
import co.com.david.inversiones.model.Ticker;
import co.com.david.inversiones.model.WatchlistItem;
import co.com.david.inversiones.usecase.market.AiSynthesisReport;
import co.com.david.inversiones.usecase.market.GetAiSynthesisUseCase;
import co.com.david.inversiones.usecase.market.GetEarningsUseCase;
import co.com.david.inversiones.usecase.market.GetFundamentalsUseCase;
import co.com.david.inversiones.usecase.market.GetWatchlistUseCase;
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
import java.util.Optional;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = {ApiTestContext.class, MarketController.class}, webEnvironment = SpringBootTest.WebEnvironment.MOCK)
class MarketControllerTest {

    @Autowired
    private WebApplicationContext webApplicationContext;
    @Autowired
    private JwtEncoder jwtEncoder;

    @MockitoBean
    private GetAiSynthesisUseCase getAiSynthesis;
    @MockitoBean
    private GetEarningsUseCase getEarnings;
    @MockitoBean
    private GetWatchlistUseCase getWatchlist;
    @MockitoBean
    private GetFundamentalsUseCase getFundamentals;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).apply(springSecurity()).build();
    }

    private String token() {
        return "Bearer " + ApiTestContext.issueToken(jwtEncoder, "david");
    }

    @Test
    void aiSynthesisWithoutATokenIsRejected() throws Exception {
        mockMvc.perform(get("/api/v1/ai-synthesis")).andExpect(status().isUnauthorized());
    }

    @Test
    void aiSynthesisReturnsTheReport() throws Exception {
        when(getAiSynthesis.execute()).thenReturn(new AiSynthesisReport(List.of(), List.of(Ticker.of("AAPL")),
                new BigDecimal("0.00")));

        mockMvc.perform(get("/api/v1/ai-synthesis").header("Authorization", token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.withoutData[0]").value("AAPL"))
                .andExpect(jsonPath("$.averageScore").value("0.00"));
    }

    @Test
    void earningsWithoutATokenIsRejected() throws Exception {
        mockMvc.perform(get("/api/v1/earnings")).andExpect(status().isUnauthorized());
    }

    @Test
    void earningsReturnsTheCalendar() throws Exception {
        EarningsEvent event = EarningsEvent.builder()
                .ticker(Ticker.of("AAPL")).reportDate(LocalDate.of(2026, 10, 15)).daysRemaining(15)
                .estimatedEps(new BigDecimal("1.50")).historicalSurprise(new BigDecimal("0.02")).build();
        when(getEarnings.execute()).thenReturn(List.of(event));

        mockMvc.perform(get("/api/v1/earnings").header("Authorization", token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].ticker").value("AAPL"))
                .andExpect(jsonPath("$[0].daysRemaining").value(15));
    }

    @Test
    void watchlistWithoutATokenIsRejected() throws Exception {
        mockMvc.perform(get("/api/v1/watchlist")).andExpect(status().isUnauthorized());
    }

    @Test
    void watchlistReturnsTheItems() throws Exception {
        WatchlistItem item = WatchlistItem.builder().ticker(Ticker.of("GME")).combinedScore(new BigDecimal("5.0"))
                .signal("HOLD").build();
        when(getWatchlist.execute()).thenReturn(List.of(item));

        mockMvc.perform(get("/api/v1/watchlist").header("Authorization", token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].ticker").value("GME"))
                .andExpect(jsonPath("$[0].price").doesNotExist());
    }

    @Test
    void fundamentalsWithoutATokenIsRejected() throws Exception {
        mockMvc.perform(get("/api/v1/fundamentals/AAPL")).andExpect(status().isUnauthorized());
    }

    @Test
    void fundamentalsReturnsTheMultiples() throws Exception {
        Fundamentals fundamentals = Fundamentals.builder()
                .ticker(Ticker.of("AAPL")).isEtf(false).hasData(true).per(new BigDecimal("28.50")).build();
        when(getFundamentals.execute(Ticker.of("AAPL"))).thenReturn(Optional.of(fundamentals));

        mockMvc.perform(get("/api/v1/fundamentals/AAPL").header("Authorization", token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.per").value("28.50"));
    }

    @Test
    void fundamentalsOfAnUnresolvedTickerReturnsAProblemDetailNotAnEmptyBody() throws Exception {
        when(getFundamentals.execute(Ticker.of("ZZZZ"))).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/fundamentals/ZZZZ").header("Authorization", token()))
                .andExpect(status().isNotFound())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                        .contentType(org.springframework.http.MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.detail").value("Could not resolve ticker: ZZZZ"));
    }
}
