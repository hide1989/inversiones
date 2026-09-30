package co.com.david.inversiones.api.dto;

import co.com.david.inversiones.model.AuthenticatedSession;

/** The issued bearer token, its expiry, and the authenticated username. */
public record LoginResponse(String token, String expiresAt, String username) {

    public static LoginResponse from(AuthenticatedSession session) {
        return new LoginResponse(session.token(), session.expiresAt().toString(), session.username());
    }
}
