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
        var nativeNames = new java.util.IdentityHashMap<Object, String>();
        for (var field : Config.class.getDeclaredFields()) if (java.lang.reflect.Modifier.isStatic(field.getModifiers())
                && net.neoforged.neoforge.common.ModConfigSpec.ConfigValue.class.isAssignableFrom(field.getType())) {
            field.setAccessible(true); nativeNames.put(field.get(null), field.getName());
        }
        var readers = readers(Config.class);
        var keys = new HashSet<String>(); var inventory = new JsonArray();
        for (var field : edits.fields()) {
            assertTrue(keys.add(field.key()), field.key());
            var definition = field.definition();
            assertDoesNotThrow(() -> definition.parse(edits.defaultValue(field.key())), field.key());
            assertNull(edits.binding(field.key()).issue(), field.key());
            if (definition.kind() == SettingDefinition.Kind.STRINGS) {
                var binding = edits.binding(field.key());
                assertEquals(field.defaultValue(), binding.readElements().get());
                for (String value : binding.readElements().get()) assertNull(binding.elementIssue(value), field.key());
            }
            var item = new JsonObject();
            item.addProperty("document", edits.binding(field.key()).id().document());
            item.addProperty("field", field.key()); item.addProperty("type", definition.kind().name());
            String nativeName = nativeNames.get(field.binding());
            assertNotNull(nativeName, field.key());
            item.addProperty("native_field", nativeName);
            assertNotEquals(SettingsCatalog.Timing.UNVERIFIED, SettingsCatalog.timing(filename, field.key()), field.key());
            assertFalse(readers.getOrDefault(nativeName, java.util.Set.of()).isEmpty(), field.key());
            item.addProperty("timing", SettingsCatalog.timing(filename, field.key()).name());
            item.add("direct_readers", new com.google.gson.Gson().toJsonTree(readers.getOrDefault(nativeName, java.util.Set.of())));
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

    @Test void fieldTimingMatchesPinnedLifecycleConsumersInsteadOfTheNativeNoneRestartFlag() throws Exception {
        var reads = readers(Config.class);
        assertTrue(reads.get("SHOW_MAIN_MENU_BUTTON").stream().anyMatch(method -> method.contains(".onScreenInit(")));
        assertEquals(SettingsCatalog.Timing.SCREEN_OPEN, SettingsCatalog.timing("dynamicwaters-common.toml", "Visuals.showMainMenuButton"));
        assertTrue(reads.get("SHOW_WELCOME_MESSAGE").stream().anyMatch(method -> method.contains(".onPlayerJoin(")));
        assertEquals(SettingsCatalog.Timing.PLAYER_JOIN, SettingsCatalog.timing("dynamicwaters-common.toml", "General.showWelcomeMessage"));
        assertTrue(reads.get("DISABLE_TECTONIC_UNDERGROUND_RIVERS").stream().anyMatch(method -> method.contains(".onConfigLoaded(")));
        assertTrue(reads.get("DISABLE_TECTONIC_UNDERGROUND_RIVERS").stream().anyMatch(method -> method.contains(".onServerStarting(")));
        assertEquals(SettingsCatalog.Timing.CONFIG_LOAD, SettingsCatalog.timing("dynamicwaters-common.toml", "General.disableTectonicUndergroundRivers"));
        assertFalse(SettingsCatalog.file("dynamicwaters-common.toml").notices("General.disableTectonicUndergroundRivers").isEmpty());
        assertEquals(SettingsCatalog.Timing.UNVERIFIED, SettingsCatalog.timing("dynamicwaters-common.toml", "General.futureOption"));
    }

    /** Inventory from the exact admitted dependency. A reader is evidence to inspect, not proof of live application. */
    static java.util.Map<String, java.util.Set<String>> readers(Class<?> owner) throws Exception {
        var readers = new java.util.TreeMap<String, java.util.Set<String>>();
        String internal = owner.getName().replace('.', '/');
        var source = Path.of(owner.getProtectionDomain().getCodeSource().getLocation().toURI());
        try (var jar = new java.util.zip.ZipFile(source.toFile())) {
            for (var entry : java.util.Collections.list(jar.entries())) {
                if (!entry.getName().endsWith(".class")) continue;
                var node = new org.objectweb.asm.tree.ClassNode();
                try (var stream = jar.getInputStream(entry)) {
                    new org.objectweb.asm.ClassReader(stream.readAllBytes()).accept(node,
                            org.objectweb.asm.ClassReader.SKIP_DEBUG | org.objectweb.asm.ClassReader.SKIP_FRAMES);
                }
                for (var method : node.methods) for (var instruction : method.instructions)
                    if (instruction instanceof org.objectweb.asm.tree.FieldInsnNode field
                            && field.getOpcode() == org.objectweb.asm.Opcodes.GETSTATIC && field.owner.equals(internal))
                        readers.computeIfAbsent(field.name, ignored -> new java.util.TreeSet<>())
                                .add(node.name + "." + method.name + method.desc);
            }
        }
        return readers;
    }
}
