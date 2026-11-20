package design.inherent.skyweavecompat.configuration;

import com.electronwill.nightconfig.core.CommentedConfig;
import design.inherent.skyweavecompat.client.RenderingPreferences;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RenderingSettingsTest {
    @Test void oneNativeDocumentProjectsEachFieldToExactlyOnePageWithItsAdmissionOwner() throws Exception {
        var config = CommentedConfig.inMemory(); RenderingPreferences.SPEC.correct(config);
        var fields = NativeFields.collect(RenderingPreferences.SPEC);
        var file = SettingsCatalog.file(RenderingPreferences.FILE);
        for (var field : fields) {
            assertDoesNotThrow(() -> field.definition().parse(field.definition().defaultText()));
            assertNotNull(file.fieldOwner(field.key()), field.key());
            var pages = SettingsCatalog.pages().stream().filter(page -> page.files().contains(file))
                    .filter(page -> file.presents(page, field.key())).toList();
            assertEquals(1, pages.size(), field.key());
            assertEquals(field.key().startsWith("worldText.") ? "iris" : "sky_archipelago", pages.getFirst().label());
            assertEquals(Path.of("config", "skyweave"), ConfigLocations.directory(Path.of("config"), file.filename()));
        }
        config.set("clouds.mode", design.inherent.skyweavecompat.climate.CloudPlacement.Mode.ABSOLUTE);
        config.set("clouds.absolute", 700.5);
        assertTrue(RenderingPreferences.SPEC.isCorrect(config));
        config.set("clouds.absolute", Double.NaN);
        assertFalse(RenderingPreferences.SPEC.isCorrect(config));
        config.set("clouds.mode", design.inherent.skyweavecompat.climate.CloudPlacement.Mode.MAPPED);
        assertFalse(RenderingPreferences.SPEC.isCorrect(config), "Inactive fields retain their native validation");
    }
}
