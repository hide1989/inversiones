package co.com.david.inversiones.consumer.n8n;

import org.slf4j.Logger;

import java.util.Optional;
import java.util.function.Supplier;

/**
 * Turns any failure from an {@link N8nGateway} call into an absent result.
 *
 * <p>A 500, a read timeout, a Jackson parse error, or {@code CallNotPermittedException} from an
 * open circuit breaker are all just "n8n did not give us this" from the caller's point of view.
 * Catching only {@link RuntimeException} is deliberate and sufficient: every failure mode a
 * {@link org.springframework.web.client.RestClient} call or a resilience4j-wrapped call can raise —
 * HTTP error status, connect/read timeout, JSON mapping failure, an open circuit breaker — surfaces
 * as an unchecked exception, so there is nothing checked left to widen the catch to.
 */
public final class N8nSafeCall {

    private N8nSafeCall() {
    }

    public static <T> Optional<T> safely(Logger log, String endpoint, Supplier<T> call) {
        try {
            return Optional.ofNullable(call.get());
        } catch (RuntimeException e) {
            log.warn("n8n call to {} failed, degrading to no data: {}", endpoint, e.toString());
            return Optional.empty();
        }
    }
}
