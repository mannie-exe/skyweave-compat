package design.inherent.skyweavecompat.configuration;

import com.electronwill.nightconfig.core.CommentedConfig;
import com.electronwill.nightconfig.toml.TomlWriter;
import com.google.gson.*;
import design.inherent.skyweavecompat.Feature;
import design.inherent.skyweavecompat.FeatureSelection;
import design.inherent.skyweavecompat.FeatureSettings;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import net.neoforged.neoforge.common.ModConfigSpec;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

/** Review artifact derived from actual owned records and pinned registered specs, never a second schema. */
class SettingsInventoryTest {
    @TempDir Path directory;
    private final Set<SettingId> identities = new HashSet<>();
    private final Set<String> documents = new HashSet<>();
    private final JsonArray entries = new JsonArray();

    @Test void everyCatalogDocumentAndLeafHasOneTypedIdentityAndAnExplicitApplicationBoundary() throws Exception {
        var definitions = new ArrayList<>(JsonSettings.FILES);
        if (definitions.stream().noneMatch(definition -> definition.filename().equals("skyweave_compat-rivers.json"))) {
            var rivers = design.inherent.skyweavecompat.terrain.RiverConfiguration.definition();
            JsonSettings.register(rivers); // Same schema registration as the admitted owner; no runtime values installed.
            definitions.add(rivers);
        }
        for (var definition : definitions) {
            var file = document(definition.filename());
            Files.writeString(directory.resolve(file.filename()), definition.defaults());
            var edits = new JsonEdits(directory, definition);
            assertEquals(edits.keys(), definition.fields().stream().map(SettingDefinition::key).toList());
            for (var field : definition.fields()) {
                assertDoesNotThrow(() -> field.parse(edits.defaultValue(field.key())));
                entry(file, field, "owned-json");
            }
        }
        nativeFile("skyweave_compat-rendering-client.toml", design.inherent.skyweavecompat.client.RenderingPreferences.SPEC, "CLIENT");
        nativeFile("skyweave_compat-wayfinder-client.toml", design.inherent.skyweavecompat.client.WayfinderPreferences.SPEC, "CLIENT");
        nativeFile("skyweave_compat-journal-client.toml", design.inherent.skyweavecompat.questlog.client.JournalPreferences.SPEC, "CLIENT");
        nativeFile("skyweave_compat-common.toml", design.inherent.skyweavecompat.NeighborConfig.SPEC, "COMMON");
        nativeFile("dynamicwaters-common.toml", com.AdamNew.dynamicwaters.Config.SPEC, "COMMON");
        for (String suffix : List.of("client", "common")) {
            var owner = Class.forName("com.ggrgg.createredstonelinkgui." + (suffix.equals("client") ? "ClientConfig" : "Config"));
            var spec = owner.getDeclaredField("SPEC"); spec.setAccessible(true);
            nativeFile("createredstonelinkgui-" + suffix + ".toml", (ModConfigSpec) spec.get(null), suffix.toUpperCase(Locale.ROOT));
        }
        var startup = document(FeatureSettings.FILE);
        for (var feature : Feature.values()) {
            var row = entry(startup, new SettingDefinition(feature.key, boolean.class, feature.defaultEnabled, SettingConstraints.NONE), "startup-properties");
            row.addProperty("physical_side", FeatureSelection.clientOnly(feature) ? "client" : "shared");
            row.add("required_mods", new Gson().toJsonTree(FeatureSelection.dependencies(feature)));
            var prerequisite = FeatureSelection.prerequisite(feature);
            if (prerequisite != null) row.addProperty("prerequisite", prerequisite.key);
        }
        assertEquals(SettingsCatalog.FILES.stream().map(SettingsCatalog.FileEntry::id).collect(java.util.stream.Collectors.toSet()), documents);
        Path output = Path.of("build/reports/config-system-inventory.json"); Files.createDirectories(output.getParent());
        var report = new JsonObject(); report.addProperty("documents", documents.size()); report.addProperty("leaves", entries.size());
        report.addProperty("scope", "all current typed schemas and declared application boundaries; active availability/effects require a real admitted context");
        report.add("entries", entries);
        Files.writeString(output, new GsonBuilder().setPrettyPrinting().create().toJson(report) + "\n");
    }

    private SettingsCatalog.FileEntry document(String filename) {
        var file = SettingsCatalog.file(filename); assertNotNull(file, filename); assertTrue(documents.add(file.id()), file.id()); return file;
    }
    private void nativeFile(String filename, ModConfigSpec spec, String registration) throws Exception {
        var file = document(filename); var config = CommentedConfig.inMemory(); spec.correct(config);
        Files.writeString(directory.resolve(filename), new TomlWriter().writeToString(config));
        var edits = new RegisteredEdits(directory, filename, spec);
        for (var field : edits.fields()) {
            assertDoesNotThrow(() -> field.definition().parse(edits.defaultValue(field.key())));
            var row = entry(file, field.definition(), "registered-toml");
            row.addProperty("timing", field.timing(filename).name());
            row.addProperty("native_registration", registration); row.addProperty("native_restart", field.spec().restartType().name());
        }
    }
    private JsonObject entry(SettingsCatalog.FileEntry file, SettingDefinition definition, String backend) {
        var id = file.field(definition.key()); assertTrue(identities.add(id), id.toString());
        var timing = file.timing(definition.key()); assertNotEquals(SettingsCatalog.Timing.UNVERIFIED, timing, id.toString());
        assertNotEquals(SettingsCatalog.Timing.MIXED, timing, id.toString());
        var row = new JsonObject(); row.addProperty("document", id.document()); row.addProperty("field", id.field());
        row.addProperty("filename", file.filename()); row.addProperty("backend", backend); row.addProperty("type", definition.kind().name());
        row.addProperty("default", definition.defaultText()); row.addProperty("timing", timing.name());
        row.add("constraints", new Gson().toJsonTree(definition.constraints()));
        row.addProperty("declared_integrated_policy_service", Arrays.stream(ServerSettingsContext.Document.values())
                .anyMatch(document -> document.filename().equals(file.filename())));
        row.add("admission_owners", new Gson().toJsonTree(file.owners().stream().map(feature -> feature.key).toList()));
        if (definition.minimum() != null) row.addProperty("minimum", definition.minimum());
        if (definition.maximum() != null) row.addProperty("maximum", definition.maximum());
        if (!definition.choices().isEmpty()) row.add("choices", new Gson().toJsonTree(definition.choices()));
        if (!definition.unit().isEmpty()) row.addProperty("unit", definition.unit());
        row.add("notices", new Gson().toJsonTree(file.notices(definition.key()))); entries.add(row); return row;
    }
}
