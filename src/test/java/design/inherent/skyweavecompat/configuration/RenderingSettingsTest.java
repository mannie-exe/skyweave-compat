package design.inherent.skyweavecompat.configuration;

import com.electronwill.nightconfig.core.CommentedConfig;
import com.electronwill.nightconfig.toml.TomlWriter;
import design.inherent.skyweavecompat.client.RenderingPreferences;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class RenderingSettingsTest {
    @TempDir Path directory;

    @Test void oneNativeDocumentProjectsEachFieldToExactlyOnePageWithItsAdmissionOwner() throws Exception {
        var config = CommentedConfig.inMemory(); RenderingPreferences.SPEC.correct(config);
        Files.writeString(directory.resolve(RenderingPreferences.FILE), new TomlWriter().writeToString(config));
        var edits = new RegisteredEdits(directory, RenderingPreferences.FILE, RenderingPreferences.SPEC);
        var file = SettingsCatalog.file(RenderingPreferences.FILE);
        for (var field : edits.fields()) {
            assertDoesNotThrow(() -> field.definition().parse(edits.defaultValue(field.key())));
            assertNotNull(file.fieldOwner(field.key()), field.key());
            var pages = SettingsCatalog.pages().stream().filter(page -> page.files().contains(file))
                    .filter(page -> file.presents(page, field.key())).toList();
            assertEquals(1, pages.size(), field.key());
            assertEquals(field.key().startsWith("worldText.") ? "iris" : "sky_archipelago", pages.getFirst().label());
            assertEquals(ConfigLocations.ownedDirectory(directory), ConfigLocations.directory(directory, file.filename()));
        }
        edits.set("clouds.mode", "ABSOLUTE"); edits.set("clouds.absolute", "700.5");
        assertDoesNotThrow(edits::candidate);
        edits.set("clouds.absolute", "NaN"); assertThrows(Exception.class, edits::candidate);
        // Inactive conditional values remain in the candidate validation contract.
        edits.set("clouds.mode", "MAPPED"); assertThrows(Exception.class, edits::candidate);
        assertFalse(edits.issues().isEmpty());
    }
}
