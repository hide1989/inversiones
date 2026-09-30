package co.com.david.inversiones.api.controller;

import co.com.david.inversiones.api.ApiTestContext;
import co.com.david.inversiones.model.AuthenticatedSession;
import co.com.david.inversiones.model.exception.InvalidCredentialsException;
import co.com.david.inversiones.usecase.auth.AuthenticateUserUseCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies {@code POST /api/v1/auth/login}, in particular that a wrong password and an unknown
 * username produce byte-identical response bodies — the HTTP layer must not undo the domain's
 * username-enumeration guarantee.
 */
@SpringBootTest(classes = {ApiTestContext.class, AuthController.class}, webEnvironment = SpringBootTest.WebEnvironment.MOCK)
class AuthControllerTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    @MockitoBean
    private AuthenticateUserUseCase authenticateUser;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).apply(springSecurity()).build();
    }

    @Test
    void loginWithValidCredentialsReturnsAToken() throws Exception {
        AuthenticatedSession session = new AuthenticatedSession("jwt-token", Instant.parse("2026-09-30T00:00:00Z"), "david");
        when(authenticateUser.execute("david", "Inversiones2026!")).thenReturn(session);

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"david","password":"Inversiones2026!"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("jwt-token"))
                .andExpect(jsonPath("$.username").value("david"));
    }

    @Test
    void loginWithBlankFieldsReturnsValidationProblemDetail() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"","password":""}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.errors").isArray());
    }

    @Test
    void wrongPasswordAndUnknownUsernameReturnByteIdenticalBodies() throws Exception {
        when(authenticateUser.execute("david", "wrong-password")).thenThrow(new InvalidCredentialsException());
        when(authenticateUser.execute("ghost", "whatever")).thenThrow(new InvalidCredentialsException());

        MvcResult wrongPassword = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"david","password":"wrong-password"}"""))
                .andExpect(status().isUnauthorized())
                .andReturn();

        MvcResult unknownUsername = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"ghost","password":"whatever"}"""))
                .andExpect(status().isUnauthorized())
                .andReturn();

        String wrongPasswordBody = wrongPassword.getResponse().getContentAsString();
        String unknownUsernameBody = unknownUsername.getResponse().getContentAsString();

        assertThat(wrongPasswordBody).isEqualTo(unknownUsernameBody);
    }
}
