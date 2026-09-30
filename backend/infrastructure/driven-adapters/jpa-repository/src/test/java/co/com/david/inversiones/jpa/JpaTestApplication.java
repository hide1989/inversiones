package co.com.david.inversiones.jpa;

import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;

/**
 * Minimal Spring Boot configuration root for the {@code @DataJpaTest} slices in this module.
 *
 * <p>This adapter module has no {@code @SpringBootApplication} of its own (only {@code app-service}
 * does), and {@code @DataJpaTest} requires one to be discoverable by searching packages upward from
 * the test class. Placed at the package root so entity scanning and Spring Data repository scanning
 * cover every subpackage ({@code transaction}, {@code instrument}, {@code broker}).
 */
@SpringBootConfiguration
@EnableAutoConfiguration
public class JpaTestApplication {
}
