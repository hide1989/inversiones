package co.com.david.inversiones.config;

import co.com.david.inversiones.usecase.signal.RefreshOutcome;
import co.com.david.inversiones.usecase.signal.RefreshSignalSnapshotUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * Schedules {@link RefreshSignalSnapshotUseCase} to run automatically once a day, on weekdays,
 * after the upstream n8n workflows have refreshed their own caches.
 *
 * <p>Scheduling is declared here, in {@code app-service}, and nowhere in the domain: the use case
 * itself takes no Spring annotation and knows nothing about cron expressions, so it stays testable
 * as plain Java and reusable from a context that triggers it differently (a manual admin endpoint,
 * a different scheduler) without any change to the use case.
 *
 * <p>{@link EnableScheduling} is declared explicitly, rather than relying on it being picked up
 * incidentally, so a reader can find the one place scheduling is switched on for this application.
 *
 * <p>The overlap guard that used to live here as its own {@code AtomicBoolean} moved into
 * {@link RefreshSignalSnapshotUseCase} once a second caller (the manual-refresh endpoint) was
 * added: a guard field on this class would only ever exclude this class's own calls from each
 * other, not from the endpoint's, so two independent booleans could never give mutual exclusion
 * between the two callers. The use case now reports back whether it ran via {@link RefreshOutcome}.
 */
@Configuration
@EnableScheduling
public class SignalSnapshotSchedulerConfig {

    private static final Logger log = LoggerFactory.getLogger(SignalSnapshotSchedulerConfig.class);

    private final RefreshSignalSnapshotUseCase refreshSignalSnapshotUseCase;

    /** The cron expression actually in effect, captured so a failure log can name it. */
    private final String refreshCron;

    /** The zone actually in effect, captured so a failure log can name it. */
    private final String refreshZone;

    public SignalSnapshotSchedulerConfig(
            RefreshSignalSnapshotUseCase refreshSignalSnapshotUseCase,
            @Value("${inversiones.signal-snapshot.refresh-cron:0 15 10 * * MON-FRI}") String refreshCron,
            @Value("${inversiones.signal-snapshot.refresh-zone:America/Bogota}") String refreshZone) {
        this.refreshSignalSnapshotUseCase = refreshSignalSnapshotUseCase;
        this.refreshCron = refreshCron;
        this.refreshZone = refreshZone;
    }

    /**
     * Fires at 10:15 America/Bogota, Monday to Friday, by default — after the upstream n8n
     * workflows this method depends on have finished their own morning refresh. Both the cron
     * expression and the zone are read from {@code inversiones.signal-snapshot.refresh-cron} /
     * {@code refresh-zone}, so the schedule can be moved without a rebuild; the literal here is only
     * the fallback if that configuration is absent. America/Bogota (rather than the server's local
     * zone) is used explicitly because a deployment's host timezone is an infrastructure detail that
     * must not silently shift when this job runs relative to the Colombian markets and n8n workflows
     * it depends on.
     *
     * <p>A failure is logged at {@code ERROR} and swallowed, never rethrown: this job is the sole
     * writer of signal-snapshot history, so an uncaught failure would mean that history quietly
     * stops advancing while the dashboard keeps rendering older and older data with nothing
     * indicating the refresh is broken. Swallowing it also means this run's failure does not prevent
     * the next scheduled run from being attempted.
     *
     * <p>If the use case reports {@link RefreshOutcome.AlreadyRunning} — because a previous run, or
     * a manual trigger through the HTTP endpoint, currently holds the guard — this logs a warning
     * and skips, exactly as before the guard moved into the use case.
     */
    @Scheduled(cron = "${inversiones.signal-snapshot.refresh-cron:0 15 10 * * MON-FRI}",
            zone = "${inversiones.signal-snapshot.refresh-zone:America/Bogota}")
    public void refreshSignalSnapshots() {
        try {
            RefreshOutcome outcome = refreshSignalSnapshotUseCase.execute();
            switch (outcome) {
                case RefreshOutcome.Completed completed ->
                        log.info("Scheduled signal snapshot refresh stored {} snapshot(s)", completed.storedCount());
                case RefreshOutcome.AlreadyRunning ignored ->
                        log.warn("Skipping scheduled signal snapshot refresh: a previous run is still in progress");
            }
        } catch (Exception exception) {
            log.error("Scheduled signal snapshot refresh failed (cron={}, zone={}); "
                            + "historical snapshots will not advance until this succeeds",
                    refreshCron, refreshZone, exception);
        }
    }
}
