package co.com.david.inversiones.api.dto;

import jakarta.validation.constraints.NotBlank;

/** Credentials submitted to {@code POST /api/v1/auth/login}. */
public record LoginRequest(
        @NotBlank(message = "username must not be blank") String username,
        @NotBlank(message = "password must not be blank") String password) {
}
