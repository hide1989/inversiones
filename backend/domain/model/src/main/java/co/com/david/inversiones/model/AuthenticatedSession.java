package co.com.david.inversiones.model;

import java.time.Instant;

/** A successful authentication: the bearer token, when it stops being valid, and who it belongs to. */
public record AuthenticatedSession(String token, Instant expiresAt, String username) {
}
