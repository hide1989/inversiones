package co.com.david.inversiones.acceptance.support;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Reads the n8n fixture payloads captured live from n8n in Tasks 6 and 7, off the exact same
 * files {@code rest-consumer}'s unit tests already use.
 *
 * <p>The directory is passed in by the {@code acceptanceTest} Gradle task as the
 * {@code fixtures.n8n.dir} system property (see {@code app-service/build.gradle}), rather than a
 * second copy of these payloads living under this source set: a duplicated fixture set could drift
 * from the one {@code rest-consumer} exercises without anyone noticing, which is exactly the
 * failure mode reusing a single set of files avoids.
 */
@Component
class N8nFixtures {

    private final Path directory;

    N8nFixtures() {
        String configured = System.getProperty("fixtures.n8n.dir");
        this.directory = configured != null
                ? Path.of(configured)
                : Path.of("../../infrastructure/driven-adapters/rest-consumer/src/test/resources/n8n");
    }

    /** Returns the raw JSON text of the named fixture file (e.g. {@code "trm-hoy.json"}). */
    String read(String fileName) {
        try {
            return Files.readString(directory.resolve(fileName));
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read n8n fixture '" + fileName
                    + "' from " + directory.toAbsolutePath(), e);
        }
    }
}
