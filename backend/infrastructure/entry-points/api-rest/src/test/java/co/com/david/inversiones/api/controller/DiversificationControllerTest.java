package co.com.david.inversiones.api.controller;

import co.com.david.inversiones.api.ApiTestContext;
import co.com.david.inversiones.model.DiversificationReport;
import co.com.david.inversiones.model.Money;
import co.com.david.inversiones.usecase.portfolio.EvaluateDiversificationUseCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = {ApiTestContext.class, DiversificationController.class}, webEnvironment = SpringBootTest.WebEnvironment.MOCK)
class DiversificationControllerTest {

    @Autowired
    private WebApplicationContext webApplicationContext;
    @Autowired
    private JwtEncoder jwtEncoder;

    @MockitoBean
    private EvaluateDiversificationUseCase evaluateDiversification;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).apply(springSecurity()).build();
    }

    @Test
    void diversificationWithoutATokenIsRejected() throws Exception {
        mockMvc.perform(get("/api/v1/diversification")).andExpect(status().isUnauthorized());
    }

    @Test
    void diversificationReturnsWeightsAndCompliance() throws Exception {
        DiversificationReport report = DiversificationReport.builder()
                .violations(List.of())
                .totalMarketValue(Money.usd("10000.00"))
                .weightByTicker(Map.of())
                .weightBySector(Map.of())
                .build();
        when(evaluateDiversification.execute()).thenReturn(report);

        mockMvc.perform(get("/api/v1/diversification")
                        .header("Authorization", "Bearer " + ApiTestContext.issueToken(jwtEncoder, "david")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.compliant").value(true))
                .andExpect(jsonPath("$.totalMarketValue.amount").value("10000.0000000000"));
    }
}
