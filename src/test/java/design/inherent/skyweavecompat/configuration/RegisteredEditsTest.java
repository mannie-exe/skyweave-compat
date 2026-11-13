package design.inherent.skyweavecompat.configuration;

import com.electronwill.nightconfig.core.CommentedConfig;
import com.electronwill.nightconfig.toml.TomlWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import net.neoforged.neoforge.common.ModConfigSpec;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class RegisteredEditsTest {
    @TempDir Path directory;

    @Test void nativeRestartDeclarationsAndSavedGenerationIdentityOverrideLiveFileDefaults() throws Exception {
        var builder = new ModConfigSpec.Builder();
        builder.define("live", true);
        builder.worldRestart().define("world", true);
        builder.gameRestart().define("game", true);
        var spec = builder.build(); var file = write("createredstonelinkgui-client.toml", spec);
        var edits = new RegisteredEdits(directory, file.getFileName().toString(), spec);
        var timing = new java.util.HashMap<String, SettingsCatalog.Timing>();
        edits.fields().forEach(field -> timing.put(field.key(), field.timing(edits.filename())));
        assertEquals(SettingsCatalog.Timing.IMMEDIATE, timing.get("live"));
        assertEquals(SettingsCatalog.Timing.SERVER_START, timing.get("world"));
        assertEquals(SettingsCatalog.Timing.GAME_RESTART, timing.get("game"));
        for (String key : java.util.List.of("minimumGap", "maximumGap", "minimumIslandRadius"))
            assertTrue(SettingsCatalog.changesGeneration("skyweave_compat-common.toml", java.util.List.of(key)));
        assertEquals(SettingsCatalog.Timing.SERVER_START,
                SettingsCatalog.timing("skyweave_compat-search.json", "timeLimitSeconds"));
    }

    @Test void integerDraftsAndCrossFieldBoundsUseTheRegisteredSpecAndRuntimeRule() throws Exception {
        var builder = new ModConfigSpec.Builder();
        builder.worldRestart().defineInRange("minimumGap", 64, 0, 2048);
        builder.worldRestart().defineInRange("maximumGap", 512, 0, 2048);
        builder.worldRestart().defineInRange("minimumIslandRadius", 96, 0, 2048);
        var spec = builder.build(); Path file = write("skyweave_compat-common.toml", spec);
        var edits = new RegisteredEdits(directory, file.getFileName().toString(), spec);
        String original = Files.readString(file);
        edits.set("minimumGap", "600"); assertThrows(Exception.class, edits::candidate); assertEquals(original, Files.readString(file));
        edits.set("maximumGap", "700"); assertEquals(600, edits.candidate().get("minimumGap"));
        edits.set("minimumGap", "12.5"); assertThrows(Exception.class, edits::save); assertEquals("12.5", edits.value("minimumGap"));
        edits.reset("minimumGap"); assertEquals("64", edits.value("minimumGap"));
        edits.set("minimumGap", "-1"); assertThrows(Exception.class, edits::candidate);
    }

    enum Mode { FIRST, SECOND }
    @Test void stringListsUseQuotedSyntaxAndKeepSpecValidation() throws Exception {
        var builder = new ModConfigSpec.Builder();
        builder.defineListAllowEmpty(java.util.List.of("biomes"), java.util.List.of("minecraft:badlands"),
                () -> "minecraft:plains", value -> value instanceof String text && text.startsWith("minecraft:"));
        var spec = builder.build(); var file = write("dynamicwaters-common.toml", spec);
        var edits = new RegisteredEdits(directory, file.getFileName().toString(), spec);
        String original = Files.readString(file);
        assertEquals("[\"minecraft:badlands\"]", edits.value("biomes"));
        edits.set("biomes", "[\"minecraft:plains\", \"minecraft:river\"]");
        assertEquals(java.util.List.of("minecraft:plains", "minecraft:river"), edits.candidate().get("biomes"));
        edits.set("biomes", "[]"); assertEquals(java.util.List.of(), edits.candidate().get("biomes"));
        for (String invalid : java.util.List.of("[1]", "[[\"minecraft:river\"]]", "[\"bad\"]", "[\"minecraft:river\"]\nextra = 1")) {
            edits.set("biomes", invalid); assertThrows(Exception.class, edits::candidate);
            assertTrue(edits.dirty()); assertEquals(original, Files.readString(file));
        }
        edits.reset("biomes"); assertFalse(edits.dirty());
        assertEquals(SettingsCatalog.Timing.NEW_WORLD, SettingsCatalog.timing(file.getFileName().toString(), "Generation.mountainRiverDensity"));
        assertEquals(SettingsCatalog.Timing.IMMEDIATE, SettingsCatalog.timing(file.getFileName().toString(), "Visuals.enableRiverParticles"));
    }
    @Test void collectionRowsDecodeNativeSyntaxAndKeepElementAndAggregateValidationSeparate() throws Exception {
        var builder = new ModConfigSpec.Builder();
        builder.defineListAllowEmpty(java.util.List.of("biomes"), java.util.List.of("minecraft:plains"),
                () -> "minecraft:plains", value -> value instanceof String text && text.startsWith("minecraft:"));
        var spec = builder.build(); var file = write("dynamicwaters-common.toml", spec);
        var edits = new RegisteredEdits(directory, file.getFileName().toString(), spec);
        edits.set("biomes", "['minecraft:river']");
        var data = new TextCollection(edits.binding("biomes"));
        assertEquals("minecraft:river", data.rows().getFirst().value()); assertNull(data.error());
        long row = data.rows().getFirst().id(); data.set(row, "other:bad");
        assertEquals("native_value", data.issue(row).code()); assertNotNull(edits.binding("biomes").issue());
        assertEquals(java.util.List.of("other:bad"), edits.binding("biomes").readElements().get());

        var aggregate = new ModConfigSpec.Builder();
        aggregate.define("names", java.util.List.of("one", "two"), value -> value instanceof java.util.List<?> list && list.size() == 2);
        var aggregateSpec = aggregate.build(); write("dynamicwaters-common.toml", aggregateSpec);
        var aggregateEdits = new RegisteredEdits(directory, "dynamicwaters-common.toml", aggregateSpec);
        var collection = new TextCollection(aggregateEdits.binding("names")); collection.remove(collection.rows().getFirst().id());
        assertNull(collection.issue(collection.rows().getFirst().id()));
        assertNotNull(aggregateEdits.binding("names").issue());
        assertTrue(aggregateEdits.dirty()); // No made-up per-element failure or hidden default repair.
    }

    @Test void enumChoicesAndExternalFileConflictsRetainTheDraft() throws Exception {
        var builder = new ModConfigSpec.Builder(); builder.defineEnum("mode", Mode.FIRST); var spec = builder.build();
        Path file = write("createredstonelinkgui-client.toml", spec);
        var edits = new RegisteredEdits(directory, file.getFileName().toString(), spec);
        assertEquals(java.util.List.of("FIRST", "SECOND"), edits.fields().getFirst().choices());
        edits.set("mode", "SECOND"); assertEquals(Mode.SECOND, edits.candidate().get("mode"));
        edits.set("mode", "INVALID"); assertThrows(Exception.class, edits::candidate);
        edits.set("mode", "SECOND"); Files.writeString(file, Files.readString(file) + "\n# outside editor\n");
        assertThrows(ConfigFile.ChangedOnDisk.class, edits::save); assertTrue(edits.dirty());
        assertTrue(Files.readString(file).contains("outside editor"));
        edits.reload(); assertFalse(edits.dirty()); assertEquals("FIRST", edits.value("mode"));
    }
    @Test void fieldMetadataAndIssuesUseNativeBoundsInsteadOfAnIndependentRange() throws Exception {
        var builder = new ModConfigSpec.Builder(); builder.defineInRange("width", 2.0, .25, 4.0);
        var spec = builder.build(); var file = write("createredstonelinkgui-client.toml", spec);
        var edits = new RegisteredEdits(directory, file.getFileName().toString(), spec);
        var field = edits.binding("width");
        assertEquals(new java.math.BigDecimal("0.25"), field.definition().minimum());
        assertEquals(new java.math.BigDecimal("4.0"), field.definition().maximum());
        field.edit().accept("4.1");
        assertEquals("width", field.issue().field()); assertThrows(Exception.class, edits::candidate);
        assertThrows(SettingIssue.InvalidValue.class, () -> field.definition().parse("4.1"));
        field.edit().accept("3.75"); assertNull(field.issue());
        assertEquals(3.75, edits.candidate().get("width"));
    }
    @Test void operationalAndClientJsonChangesDoNotRequireANewWorld() {
        assertFalse(SettingsCatalog.changesGeneration("skyweave_compat-wayfinder.json", java.util.List.of("cooldownTicks")));
        assertFalse(SettingsCatalog.changesGeneration("skyweave_compat-search.json", java.util.List.of("timeLimitSeconds")));
        assertFalse(SettingsCatalog.changesGeneration("skyweave_compat-hoofprint.json", java.util.List.of("zoomStep")));
        assertTrue(SettingsCatalog.changesGeneration("skyweave_compat-oceans.json", java.util.List.of("admissionChance")));
    }

    private Path write(String name, ModConfigSpec spec) throws Exception {
        var values = CommentedConfig.inMemory(); spec.correct(values);
        Path file = directory.resolve(name); Files.writeString(file, new TomlWriter().writeToString(values)); return file;
    }
}
