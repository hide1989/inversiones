package co.com.david.inversiones.api.controller;

import co.com.david.inversiones.api.ApiTestContext;
import co.com.david.inversiones.model.*;
import co.com.david.inversiones.model.exception.TransactionNotFoundException;
import co.com.david.inversiones.model.exception.UnknownCatalogEntryException;
import co.com.david.inversiones.usecase.transaction.DeleteTransactionUseCase;
import co.com.david.inversiones.usecase.transaction.ListTransactionsUseCase;
import co.com.david.inversiones.usecase.transaction.RegisterTransactionUseCase;
import co.com.david.inversiones.usecase.transaction.TransactionFilter;
import co.com.david.inversiones.usecase.transaction.UpdateTransactionUseCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies the transaction CRUD endpoints, in particular that an unknown ticker on
 * {@code POST /transactions} is a 400 (not a 500) and an unknown id on
 * {@code DELETE /transactions/{id}} is a 404 (not a 400) — the easy-to-invert mapping.
 */
@SpringBootTest(classes = {ApiTestContext.class, TransactionController.class}, webEnvironment = SpringBootTest.WebEnvironment.MOCK)
class TransactionControllerTest {

    @Autowired
    private WebApplicationContext webApplicationContext;
    @Autowired
    private JwtEncoder jwtEncoder;

    @MockitoBean
    private ListTransactionsUseCase listTransactions;
    @MockitoBean
    private RegisterTransactionUseCase registerTransaction;
    @MockitoBean
    private UpdateTransactionUseCase updateTransaction;
    @MockitoBean
    private DeleteTransactionUseCase deleteTransaction;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).apply(springSecurity()).build();
    }

    private String token() {
        return "Bearer " + ApiTestContext.issueToken(jwtEncoder, "david");
    }

    private static Transaction transaction() {
        return Transaction.builder()
                .id(1L).ticker(Ticker.of("AAPL")).brokerName("Hapi").type(TransactionType.BUY)
                .quantity(Quantity.of("10")).unitPrice(Money.usd("150.00")).tradedAt(LocalDate.of(2026, 1, 1))
                .build();
    }

    private static final String VALID_REQUEST = """
            {"ticker":"AAPL","brokerName":"Hapi","type":"BUY","quantity":"10","unitPrice":"150.00",
             "currency":"USD","tradedAt":"2026-01-01"}""";

    @Test
    void listWithoutATokenIsRejected() throws Exception {
        mockMvc.perform(get("/api/v1/transactions")).andExpect(status().isUnauthorized());
    }

    @Test
    void listReturnsTransactions() throws Exception {
        when(listTransactions.execute(any(TransactionFilter.class))).thenReturn(List.of(transaction()));

        mockMvc.perform(get("/api/v1/transactions").header("Authorization", token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].ticker").value("AAPL"))
                .andExpect(jsonPath("$[0].unitPrice.amount").value("150.0000000000"))
                .andExpect(jsonPath("$[0].unitPrice.currency").value("USD"));
    }

    @Test
    void registerWithoutATokenIsRejected() throws Exception {
        mockMvc.perform(post("/api/v1/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_REQUEST))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void registerWithMissingFieldsReturnsValidationProblemDetail() throws Exception {
        mockMvc.perform(post("/api/v1/transactions")
                        .header("Authorization", token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.errors").isArray());
    }

    @Test
    void registerWithAnUnknownTickerReturnsBadRequestNotServerError() throws Exception {
        when(registerTransaction.execute(any(Transaction.class)))
                .thenThrow(UnknownCatalogEntryException.forInstrument(Ticker.of("AAPL")));

        mockMvc.perform(post("/api/v1/transactions")
                        .header("Authorization", token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_REQUEST))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void registerReturnsTheStoredTransaction() throws Exception {
        when(registerTransaction.execute(any(Transaction.class))).thenReturn(transaction());

        mockMvc.perform(post("/api/v1/transactions")
                        .header("Authorization", token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_REQUEST))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    void updateWithoutATokenIsRejected() throws Exception {
        mockMvc.perform(put("/api/v1/transactions/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_REQUEST))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void updateOfAnUnknownIdReturnsNotFound() throws Exception {
        when(updateTransaction.execute(org.mockito.ArgumentMatchers.eq(99L), any(Transaction.class)))
                .thenThrow(new TransactionNotFoundException(99L));

        mockMvc.perform(put("/api/v1/transactions/99")
                        .header("Authorization", token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_REQUEST))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void deleteWithoutATokenIsRejected() throws Exception {
        mockMvc.perform(delete("/api/v1/transactions/1")).andExpect(status().isUnauthorized());
    }

    @Test
    void deleteOfAnUnknownIdReturnsNotFoundNotBadRequest() throws Exception {
        org.mockito.Mockito.doThrow(new TransactionNotFoundException(404L))
                .when(deleteTransaction).execute(404L);

        mockMvc.perform(delete("/api/v1/transactions/404").header("Authorization", token()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void deleteOfAnExistingIdReturnsNoContent() throws Exception {
        mockMvc.perform(delete("/api/v1/transactions/1").header("Authorization", token()))
                .andExpect(status().isNoContent());
    }
}
