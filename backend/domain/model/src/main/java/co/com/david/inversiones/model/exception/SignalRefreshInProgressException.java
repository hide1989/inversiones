package co.com.david.inversiones.model.exception;

/**
 * Raised by the manual-refresh endpoint when {@code RefreshSignalSnapshotUseCase} reports that a
 * run is already in flight (scheduled or manually triggered) and therefore refused this one.
 *
 * <p>This is a conflict, not a validation failure or a bug: the request was well-formed and
 * nothing is broken, but running it now would race the in-flight run against the same day's
 * snapshot rows. It must not be treated as the generic {@link DomainException} 400 mapping, so
 * {@code ApiExceptionHandler} maps it to its own status (409) rather than falling through.
 */
public class SignalRefreshInProgressException extends DomainException {

    public SignalRefreshInProgressException() {
        super("A signal refresh is already running; try again once it finishes");
    }
}
