package co.com.david.inversiones.usecase.signal;

/**
 * What {@link RefreshSignalSnapshotUseCase#execute()} produced: either it ran and stored some
 * number of snapshots, or it declined because another run was already in flight.
 *
 * <p>Modelled as a sealed result rather than a thrown exception so the domain stays free of any
 * notion of HTTP status codes: each caller (the scheduler, the manual-trigger endpoint) maps this
 * outcome to its own behaviour — the scheduler logs and skips, the endpoint answers 409 — without
 * the use case knowing either caller exists.
 */
public sealed interface RefreshOutcome {

    /** The run executed and stored {@code storedCount} snapshots (zero if there was nothing new). */
    record Completed(int storedCount) implements RefreshOutcome {
    }

    /** The run was refused because another run already held the guard. */
    record AlreadyRunning() implements RefreshOutcome {
    }
}
