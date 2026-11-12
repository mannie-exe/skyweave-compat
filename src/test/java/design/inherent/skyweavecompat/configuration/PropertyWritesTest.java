package design.inherent.skyweavecompat.configuration;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class PropertyWritesTest {
    @TempDir Path root;

    @Test void preservesExactNativeBytesOnlyWhenAllValuesMatch() throws Exception {
        var path = root.resolve("native.properties");
        String original = "# Native timestamp and user comments\r\nenabled=false\r\nsalt=7\r\n";
        Files.writeString(path, original);
        var values = new Properties(); values.setProperty("enabled", "false"); values.setProperty("salt", "7");
        assertTrue(PropertyWrites.unchanged(path, values));
        assertEquals(original, Files.readString(path));
        values.setProperty("enabled", "true");
        assertFalse(PropertyWrites.unchanged(path, values));
        values.setProperty("enabled", "false"); values.setProperty("newDefault", "1");
        assertFalse(PropertyWrites.unchanged(path, values));
        values.remove("newDefault"); Files.writeString(path, original + "foreign=keep\n");
        assertFalse(PropertyWrites.unchanged(path, values));
    }

    @Test void missingMalformedOversizedAndSymlinkFilesRetainNativeWriter() throws Exception {
        var path = root.resolve("native.properties"); var values = new Properties();
        assertFalse(PropertyWrites.unchanged(path, values));
        Files.writeString(path, "bad=\\uQQQQ\n"); assertFalse(PropertyWrites.unchanged(path, values));
        Files.writeString(path, "#".repeat(65537)); assertFalse(PropertyWrites.unchanged(path, values));
        Files.writeString(path, "# only comments\n");
        var link = root.resolve("link"); Files.createSymbolicLink(link, path);
        assertFalse(PropertyWrites.unchanged(link, values));
    }
}
