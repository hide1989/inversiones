package co.com.david.inversiones.consumer.support;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

/** Loads the live payloads captured under {@code src/test/resources/n8n/} into strings. */
public final class Fixtures {

    private Fixtures() {
    }

    public static String load(String name) {
        String path = "/n8n/" + name + ".json";
        try (InputStream in = Fixtures.class.getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalArgumentException("Fixture not found: " + path);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
