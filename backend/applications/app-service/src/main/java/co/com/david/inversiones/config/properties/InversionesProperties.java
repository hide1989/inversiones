package co.com.david.inversiones.config.properties;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;

/**
 * Typed, validated view of the {@code inversiones.*} configuration tree.
 *
 * <p>Every value here already has an environment-variable default in {@code application.yml}, so
 * this record rarely rejects a deployment for a missing property. Its job is the other failure
 * mode: a value that is present but wrong — an empty secret, a blank base URL, a non-positive TRM.
 * Spring Boot binds and validates this bean during context refresh (Jakarta Bean Validation, via
 * {@code spring-boot-starter-validation} on the classpath), so a bad value fails startup with a
 * {@code BindValidationException} naming the offending property path, instead of surfacing as a
 * {@code NullPointerException} or a silently wrong calculation the first time something reads it.
 *
 * <p>This class lives under {@code config} and is excluded from the module coverage gate by
 * convention (see {@code main.gradle}); its tests exist anyway because an exclusion from the
 * coverage gate is not permission to leave the binding and validation behaviour untested.
 */
@ConfigurationProperties(prefix = "inversiones")
@Validated
public record InversionesProperties(
        @NotNull @Positive BigDecimal baseTrm,
        @NotNull @Valid N8n n8n,
        @NotNull @Valid Security security,
        @NotNull @Valid SignalSnapshot signalSnapshot) {

    /** The n8n REST client's base URL and the timeout budget every call against it gets. */
    public record N8n(
            @NotBlank String baseUrl,
            @NotNull Duration connectTimeout,
            @NotNull Duration readTimeout,
            @NotNull Duration slowReadTimeout) {
    }

    /** The bearer-token secret, its lifetime, and the exact cross-origin allow-list. */
    public record Security(
            @NotBlank @Size(min = 32, message = "must be at least 32 bytes for HS256 signing")
            String jwtSecret,
            @Positive int jwtExpirationMinutes,
            @NotEmpty List<String> allowedOrigins) {
    }

    /** When the scheduled {@code RefreshSignalSnapshotUseCase} run fires, and in which zone. */
    public record SignalSnapshot(
            @NotBlank String refreshCron,
            @NotBlank String refreshZone) {
    }
}
