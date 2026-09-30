package co.com.david.inversiones.consumer;

import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;

/**
 * Minimal Spring Boot configuration root for the {@code @SpringBootTest} slices in this module.
 *
 * <p>This adapter module has no {@code @SpringBootApplication} of its own (only {@code app-service}
 * does); placed at the package root so component scanning covers every subpackage.
 */
@SpringBootConfiguration
@EnableAutoConfiguration
@ComponentScan
public class RestConsumerTestApplication {
}
