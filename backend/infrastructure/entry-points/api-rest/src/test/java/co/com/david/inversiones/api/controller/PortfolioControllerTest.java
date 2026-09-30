package co.com.david.inversiones.api.controller;

import co.com.david.inversiones.api.ApiTestContext;
import co.com.david.inversiones.model.*;
import co.com.david.inversiones.usecase.portfolio.CalculateTotalReturnUseCase;
import co.com.david.inversiones.usecase.portfolio.GetPortfolioUseCase;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies {@code GET /api/v1/portfolio} and {@code GET /api/v1/portfolio/returns}, in particular
 * that a portfolio holding both a USD and a COP position serializes with the base currency
 * declared and every amount as a string-carrying object — the shape of the Critical defect this
 * project already fixed.
 */
@SpringBootTest(classes = {ApiTestContext.class, PortfolioController.class}, webEnvironment = SpringBootTest.WebEnvironment.MOCK)
class PortfolioControllerTest {

    @Autowired
    private WebApplicationContext webApplicationContext;
    @Autowired
    private JwtEncoder jwtEncoder;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @MockitoBean
    private GetPortfolioUseCase getPortfolio;
    @MockitoBean
    private CalculateTotalReturnUseCase calculateTotalReturn;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).apply(springSecurity()).build();
    }

    private static Position position(Ticker ticker, String brokerName, Money unitCost) {
        return Position.builder()
                .ticker(ticker).brokerName(brokerName)
                .quantity(Quantity.of("10"))
                .totalCost(unitCost.multiply(BigDecimal.TEN))
                .averageCost(unitCost)
                .realizedPnl(Money.zero(unitCost.currency()))
                .build();
    }

    private static ValuedPosition valued(Position position, Money marketValue, Money baseValue) {
        return ValuedPosition.builder()
                .position(position)
                .marketValue(marketValue)
                .unrealizedPnl(marketValue.subtract(position.totalCost()))
                .unrealizedPnlPercent(Percentage.zero())
                .dayChangePercent(Percentage.zero())
                .priced(true)
                .costInBaseCurrency(baseValue)
                .marketValueInBaseCurrency(baseValue)
                .build();
    }

    @Test
    void portfolioWithoutATokenIsRejected() throws Exception {
        mockMvc.perform(get("/api/v1/portfolio")).andExpect(status().isUnauthorized());
    }

    @Test
    void portfolioWithUsdAndCopPositionsDeclaresBaseCurrencyAndSerializesAmountsAsStrings() throws Exception {
        Position usdPosition = position(Ticker.of("AAPL"), "Hapi", Money.usd("150.5000000000"));
        Position copPosition = position(Ticker.of("ECOPETROL"), "Trii", Money.cop("2500.0000000000"));

        ValuedPosition usdValued = valued(usdPosition, Money.usd("1600.0000000000"), Money.usd("1600.0000000000"));
        ValuedPosition copValued = valued(copPosition, Money.cop("26000.0000000000"), Money.usd("6.4000000000"));

        PortfolioValuation valuation = PortfolioValuation.builder()
                .positions(List.of(usdValued, copValued))
                .totalInvested(Money.usd("1511.4000000000"))
                .totalMarketValue(Money.usd("1606.4000000000"))
                .totalUnrealizedPnl(Money.usd("95.0000000000"))
                .unpricedTickers(List.of())
                .baseCurrency(Currency.USD)
                .build();
        when(getPortfolio.execute()).thenReturn(valuation);

        String body = mockMvc.perform(get("/api/v1/portfolio")
                        .header("Authorization", "Bearer " + ApiTestContext.issueToken(jwtEncoder, "david")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.baseCurrency").value("USD"))
                .andExpect(jsonPath("$.positions[0].ticker").value("AAPL"))
                .andExpect(jsonPath("$.positions[1].ticker").value("ECOPETROL"))
                .andReturn().getResponse().getContentAsString();

        JsonNode root = objectMapper.readTree(body);

        // The non-negotiable: amount must be a JSON string carrying full BigDecimal precision,
        // never a bare JSON number, on every Money-backed field in the response — a regression to
        // a numeric type is otherwise invisible until a client silently rounds a figure.
        assertThat(root.at("/totalInvested/amount").isTextual()).isTrue();
        assertThat(root.at("/totalInvested/amount").asText()).isEqualTo("1511.4000000000");
        assertThat(root.at("/totalInvested/currency").asText()).isEqualTo("USD");

        assertThat(root.at("/positions/0/totalCost/amount").isTextual()).isTrue();
        assertThat(root.at("/positions/0/totalCost/currency").asText()).isEqualTo("USD");
        assertThat(root.at("/positions/1/totalCost/amount").isTextual()).isTrue();
        assertThat(root.at("/positions/1/totalCost/currency").asText()).isEqualTo("COP");
        assertThat(root.at("/positions/1/totalCost/amount").asText()).isEqualTo("25000.0000000000");
    }

    @Test
    void returnsWithoutATokenIsRejected() throws Exception {
        mockMvc.perform(get("/api/v1/portfolio/returns")).andExpect(status().isUnauthorized());
    }

    @Test
    void returnsReportsTheAssetGainAndTheExchangeEffectSeparately() throws Exception {
        ExchangeRate baseRate = ExchangeRate.copPerUsd(new BigDecimal("4000"), LocalDate.of(2026, 1, 1));
        ExchangeRate currentRate = ExchangeRate.copPerUsd(new BigDecimal("4200"), LocalDate.of(2026, 9, 30));
        TotalReturn totalReturn = TotalReturn.builder()
                .segments(List.of())
                .baseRate(baseRate)
                .currentRate(currentRate)
                .exchangeEffect(Money.cop("1000000"))
                .assetGainInCop(Money.cop("500000"))
                .totalReturnInCop(Money.cop("1500000"))
                .build();
        when(calculateTotalReturn.execute()).thenReturn(totalReturn);

        mockMvc.perform(get("/api/v1/portfolio/returns")
                        .header("Authorization", "Bearer " + ApiTestContext.issueToken(jwtEncoder, "david")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.exchangeEffect.amount").value("1000000.0000000000"))
                .andExpect(jsonPath("$.exchangeEffect.currency").value("COP"))
                .andExpect(jsonPath("$.baseRate.rate").value("4000"))
                .andExpect(jsonPath("$.currentRate.rate").value("4200"));
    }
}
