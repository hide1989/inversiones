package co.com.david.inversiones.api.error;

import co.com.david.inversiones.api.ApiTestContext;
import co.com.david.inversiones.api.controller.DiversificationController;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies {@link ApiExceptionHandler}'s catch-all: an exception that is not a {@link
 * co.com.david.inversiones.model.exception.DomainException} is a bug, not a rejected request, so
 * it must come back as a 500 with a correlation id and never a stack trace in the body.
 */
@SpringBootTest(classes = {ApiTestContext.class, DiversificationController.class}, webEnvironment = SpringBootTest.WebEnvironment.MOCK)
class ApiExceptionHandlerTest {

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
    void anUnexpectedExceptionReturnsServerErrorWithACorrelationIdAndNoStackTrace() throws Exception {
        when(evaluateDiversification.execute()).thenThrow(new IllegalStateException("boom: unexpected failure"));

        String body = mockMvc.perform(get("/api/v1/diversification")
                        .header("Authorization", "Bearer " + ApiTestContext.issueToken(jwtEncoder, "david")))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.correlationId").exists())
                .andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContain("IllegalStateException");
        assertThat(body).doesNotContain("boom: unexpected failure");
        assertThat(body).doesNotContain("at co.com.david.inversiones");
    }
}
