package design.inherent.skyweavecompat.configuration;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Properties;

/** Recognize a no-value-change native save without rewriting its timestamp/comments. */
public final class PropertyWrites {
    private static final int LIMIT = 65536;

    public static boolean unchanged(Path path, Properties values) {
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) return false;
        try (var input = Files.newInputStream(path)) {
            byte[] bytes = input.readNBytes(LIMIT + 1);
            if (bytes.length > LIMIT) return false;
            var saved = new Properties();
            saved.load(new ByteArrayInputStream(bytes));
            return saved.equals(values);
        } catch (IOException | IllegalArgumentException failure) {
            // Let the native writer retain its own creation, error and normalization behavior.
            return false;
        }
    }

    private PropertyWrites() {}
}
