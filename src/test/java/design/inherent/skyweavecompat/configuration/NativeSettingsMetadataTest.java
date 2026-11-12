package design.inherent.skyweavecompat.configuration;

import com.AdamNew.dynamicwaters.Config;
import com.electronwill.nightconfig.core.CommentedConfig;
import com.electronwill.nightconfig.toml.TomlWriter;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class NativeSettingsMetadataTest {
    @TempDir Path directory;

    @Test void completePinnedDynamicWatersSpecHasTypedEditableDefaultsAndDistinctIdentities() throws Exception {
        var config = CommentedConfig.inMemory(); Config.SPEC.correct(config);
        String filename = "dynamicwaters-common.toml";
        Files.writeString(directory.resolve(filename), new TomlWriter().writeToString(config));
        var edits = new RegisteredEdits(directory, filename, Config.SPEC);
        var keys = new HashSet<String>(); var inventory = new JsonArray();
        for (var field : edits.fields()) {
            assertTrue(keys.add(field.key()), field.key());
            var definition = field.definition();
            assertDoesNotThrow(() -> definition.parse(edits.defaultValue(field.key())), field.key());
            assertNull(edits.binding(field.key()).issue(), field.key());
            var item = new JsonObject();
            item.addProperty("document", edits.binding(field.key()).id().document());
            item.addProperty("field", field.key()); item.addProperty("type", definition.kind().name());
            item.addProperty("default", definition.defaultText());
            if (definition.minimum() != null) item.addProperty("minimum", definition.minimum());
            if (definition.maximum() != null) item.addProperty("maximum", definition.maximum());
            if (!definition.choices().isEmpty()) {
                var choices = new JsonArray(); definition.choices().forEach(choices::add); item.add("choices", choices);
            }
            inventory.add(item);
        }
        assertFalse(keys.isEmpty()); assertFalse(edits.dirty()); assertDoesNotThrow(edits::candidate);
        // Inspection artifact from the actual admitted spec, not another manually maintained schema.
        Path report = Path.of("build/reports/config-system-dynamic-waters.json");
        Files.createDirectories(report.getParent());
        Files.writeString(report, new GsonBuilder().setPrettyPrinting().create().toJson(inventory) + "\n");
    }
}
