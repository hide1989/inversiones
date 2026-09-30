package co.com.david.inversiones.config;

import co.com.david.inversiones.usecase.signal.RefreshSignalSnapshotUseCase;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.support.PropertySourcesPlaceholderConfigurer;
import org.springframework.core.env.MapPropertySource;
import org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor;
import org.springframework.scheduling.config.CronTask;
import org.springframework.scheduling.config.ScheduledTask;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Proves {@link SignalSnapshotSchedulerConfig#refreshSignalSnapshots()} is registered with the
 * configured cron expression and zone, without waiting for the job to actually fire. Spring's
 * {@link ScheduledAnnotationBeanPostProcessor} tracks every {@code @Scheduled} method it registers,
 * so inspecting its {@link ScheduledAnnotationBeanPostProcessor#getScheduledTasks()} is enough —
 * sleeping until the trigger time would make this test slow and flaky for no extra confidence.
 */
class SignalSnapshotSchedulerConfigTest {

    @Test
    void registersTheRefreshMethodWithTheConfiguredCronAndZone() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("test", Map.of(
                    "inversiones.signal-snapshot.refresh-cron", "0 15 10 * * MON-FRI",
                    "inversiones.signal-snapshot.refresh-zone", "America/Bogota")));
            context.registerBean(PropertySourcesPlaceholderConfigurer.class, PropertySourcesPlaceholderConfigurer::new);
            context.registerBean(RefreshSignalSnapshotUseCase.class, () -> mock(RefreshSignalSnapshotUseCase.class));
            context.register(SignalSnapshotSchedulerConfig.class);
            context.refresh();

            ScheduledAnnotationBeanPostProcessor postProcessor =
                    context.getBean(ScheduledAnnotationBeanPostProcessor.class);
            Set<ScheduledTask> tasks = postProcessor.getScheduledTasks();

            assertThat(tasks).hasSize(1);
            ScheduledTask registered = tasks.iterator().next();
            assertThat(registered.getTask()).isInstanceOf(CronTask.class);
            CronTask cronTask = (CronTask) registered.getTask();
            assertThat(cronTask.getExpression()).isEqualTo("0 15 10 * * MON-FRI");
        }
    }

    @Test
    void fallsBackToTheDocumentedDefaultsWhenNoPropertyIsConfigured() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.registerBean(PropertySourcesPlaceholderConfigurer.class, PropertySourcesPlaceholderConfigurer::new);
            context.registerBean(RefreshSignalSnapshotUseCase.class, () -> mock(RefreshSignalSnapshotUseCase.class));
            context.register(SignalSnapshotSchedulerConfig.class);
            context.refresh();

            ScheduledAnnotationBeanPostProcessor postProcessor =
                    context.getBean(ScheduledAnnotationBeanPostProcessor.class);
            CronTask cronTask = (CronTask) postProcessor.getScheduledTasks().iterator().next().getTask();

            assertThat(cronTask.getExpression()).isEqualTo("0 15 10 * * MON-FRI");
        }
    }
}
