package design.inherent.skyweavecompat.configuration;

import com.electronwill.nightconfig.core.CommentedConfig;
import com.electronwill.nightconfig.toml.TomlWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class LinkSettingsMetadataTest {
    @TempDir Path directory;

    @Test void nativeClientAndCommonLeavesRetainTypesAndRecordTheirActualReaders() throws Exception {
        var report = new com.google.gson.JsonArray();
        for (String suffix : java.util.List.of("client", "common")) {
            var owner = Class.forName("com.ggrgg.createredstonelinkgui." + (suffix.equals("client") ? "ClientConfig" : "Config"));
            var specField = owner.getDeclaredField("SPEC"); specField.setAccessible(true);
            var spec = (net.neoforged.neoforge.common.ModConfigSpec) specField.get(null);
            String filename = "createredstonelinkgui-" + suffix + ".toml";
            var config = CommentedConfig.inMemory(); spec.correct(config);
            Files.writeString(directory.resolve(filename), new TomlWriter().writeToString(config));
            var edits = new RegisteredEdits(directory, filename, spec);
            var names = new java.util.IdentityHashMap<Object, String>();
            for (var field : owner.getDeclaredFields()) if (java.lang.reflect.Modifier.isStatic(field.getModifiers())
                    && net.neoforged.neoforge.common.ModConfigSpec.ConfigValue.class.isAssignableFrom(field.getType())) {
                field.setAccessible(true); names.put(field.get(null), field.getName());
            }
            var readers = NativeSettingsMetadataTest.readers(owner);
            for (var field : edits.fields()) {
                assertDoesNotThrow(() -> field.definition().parse(edits.defaultValue(field.key())));
                String nativeName = names.get(field.binding()); assertNotNull(nativeName);
                assertFalse(readers.getOrDefault(nativeName, java.util.Set.of()).isEmpty());
                var entry = new com.google.gson.JsonObject();
                entry.addProperty("document", SettingsCatalog.file(filename).id()); entry.addProperty("field", field.key());
                entry.addProperty("native_field", nativeName); entry.addProperty("type", field.definition().kind().name());
                entry.addProperty("timing", SettingsCatalog.timing(filename, field.key()).name());
                entry.add("direct_readers", new com.google.gson.Gson().toJsonTree(readers.get(nativeName))); report.add(entry);
            }
        }
        Path output = Path.of("build/reports/config-system-link-gui.json"); Files.createDirectories(output.getParent());
        Files.writeString(output, new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(report) + "\n");
    }
}
