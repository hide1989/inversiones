package co.com.david.inversiones.model.gateways;

import java.time.Instant;
import java.time.LocalDate;

/** Provides the current date and instant, so use cases never call the system clock directly. */
public interface TimeProvider {

    /** Returns today's date. */
    LocalDate today();

    /** Returns the current instant. */
    Instant now();
}
