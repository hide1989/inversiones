package co.com.david.inversiones.api.controller;

import co.com.david.inversiones.api.dto.LoginRequest;
import co.com.david.inversiones.api.dto.LoginResponse;
import co.com.david.inversiones.model.AuthenticatedSession;
import co.com.david.inversiones.usecase.auth.AuthenticateUserUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Issues bearer tokens for the API's single authentication endpoint. */
@RestController
@RequestMapping(value = "/api/v1/auth", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Auth")
public class AuthController {

    private final AuthenticateUserUseCase authenticateUser;

    public AuthController(AuthenticateUserUseCase authenticateUser) {
        this.authenticateUser = authenticateUser;
    }

    @Operation(summary = "Authenticate with a username and password", responses = {
            @ApiResponse(responseCode = "200", description = "Token issued"),
            @ApiResponse(responseCode = "400", description = "Validation failure"),
            @ApiResponse(responseCode = "401", description = "Unknown username or wrong password")
    })
    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        AuthenticatedSession session = authenticateUser.execute(request.username(), request.password());
        return LoginResponse.from(session);
    }
}
