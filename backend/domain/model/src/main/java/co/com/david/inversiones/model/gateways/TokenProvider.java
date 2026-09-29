package co.com.david.inversiones.model.gateways;

import co.com.david.inversiones.model.AuthenticatedSession;
import co.com.david.inversiones.model.User;

/** Port for issuing bearer tokens to authenticated users. */
public interface TokenProvider {

    /** Issues a new session for this user. */
    AuthenticatedSession issue(User user);
}
