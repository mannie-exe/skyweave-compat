package design.inherent.skyweavecompat.configuration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class ConfigLocationsTest {
    @TempDir Path root;

    @Test void ownedAndForeignFilesKeepTheirSeparateAuthorities() throws Exception {
        assertEquals(root.resolve("skyweave"), ConfigLocations.prepare(root));
        assertEquals(root.resolve("skyweave/skyweave_compat-oceans.json"), ConfigLocations.file(root, "skyweave_compat-oceans.json"));
        assertEquals(root.resolve("dynamicwaters-common.toml"), ConfigLocations.file(root, "dynamicwaters-common.toml"));
        assertEquals("skyweave/skyweave_compat-common.toml", ConfigLocations.registeredName("skyweave_compat-common.toml"));
    }

    @Test void unmigratedSettingsCannotBecomeFreshDefaults() throws Exception {
        var old = root.resolve("skyweave_compat-features.properties");
        Files.writeString(old, "terrain.preset=false\n");
        assertThrows(IOException.class, () -> ConfigLocations.prepare(root));
        assertEquals("terrain.preset=false\n", Files.readString(old));
        assertFalse(Files.exists(root.resolve("skyweave/skyweave_compat-features.properties")));
    }

    @Test void differentCopiesNeedAnExplicitDecision() throws Exception {
        Files.writeString(root.resolve("skyweave_compat-features.properties"), "terrain.preset=false\n");
        Files.createDirectory(root.resolve("skyweave"));
        Files.writeString(root.resolve("skyweave/skyweave_compat-features.properties"), "terrain.preset=true\n");
        assertThrows(IOException.class, () -> ConfigLocations.prepare(root));
        assertEquals("terrain.preset=true\n", Files.readString(root.resolve("skyweave/skyweave_compat-features.properties")));
    }

    @Test void alreadyMigratedValuesAreNotRewritten() throws Exception {
        var owned = ConfigLocations.prepare(root);
        Files.writeString(owned.resolve("skyweave_compat-features.properties"), "# Keep this comment\nterrain.preset=false\n");
        ConfigLocations.prepare(root);
        assertEquals("# Keep this comment\nterrain.preset=false\n", Files.readString(owned.resolve("skyweave_compat-features.properties")));
    }

    @Test void shaderRecipesNeedTheSameExplicitRelocation() throws Exception {
        Files.createDirectories(root.resolve("skyweave-shaders/recipes"));
        assertThrows(IOException.class, () -> ConfigLocations.prepare(root));
    }
}
