package design.inherent.skyweavecompat.terrain;

import com.google.gson.*;
import design.inherent.skyweavecompat.configuration.*;
import java.nio.file.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class RiverConfigurationTest {
    @TempDir Path directory;
    private JsonSettings.Definition definition() {
        var found = JsonSettings.find(RiverConfiguration.FILE);
        if (found != null) return found;
        var definition = RiverConfiguration.definition();
        JsonSettings.register(definition);
        return definition;
    }

    @Test void nativeFieldsExposeTypedBindingsInsteadOfUnboundedNumberFallbacks() {
        var definition = definition();
        var depth = definition.field("minimum_depth"); assertNotNull(depth);
        assertEquals(SettingDefinition.Kind.INTEGER, depth.kind());
        assertInstanceOf(Integer.class, depth.defaultValue());
        assertEquals(java.math.BigDecimal.ONE, depth.minimum());
        assertEquals(new java.math.BigDecimal(Integer.MAX_VALUE), depth.maximum());
        assertEquals(new java.math.BigDecimal("4"), definition.field("anchors.maximum_tributaries").maximum());
        assertEquals(SettingDefinition.Kind.DECIMAL, definition.field("anchors.frequency").kind());
        assertEquals(SettingDefinition.Kind.STRING, definition.field("fluid_block").kind());
    }

    @Test void nestedNativeSettingsRoundTripWithoutOverwritingSiblingFields() throws Exception {
        var definition = definition();
        var file = directory.resolve(definition.filename());
        Files.writeString(file, definition.defaults());
        var edits = new JsonEdits(directory, definition);
        assertTrue(edits.keys().contains("anchors.minimum_width"));
        assertEquals(JsonEdits.Kind.STRING, edits.kind("temperate_biome"));
        edits.set("anchors.minimum_width", "14");
        assertThrows(RuntimeException.class, edits::save);
        assertEquals(definition.defaults(), Files.readString(file));
        edits.set("anchors.maximum_width", "16");
        edits.set("temperate_biome", "minecraft:stony_shore");
        edits.strings("excluded_biomes", List.of("minecraft:desert"));
        edits.save();
        var saved = RiverConfiguration.parse(Files.readString(file));
        assertEquals(14, saved.anchors().minimumWidth());
        assertEquals(16, saved.anchors().maximumWidth());
        assertEquals(6, saved.satellites().maximumWidth());
        assertEquals("minecraft:stony_shore", saved.temperateBiome().toString());
        assertEquals(1, saved.excludedBiomes().size());
        edits.reset("anchors.minimum_width");
        edits.reset("anchors.maximum_width");
        edits.save();
        assertEquals(6, RiverConfiguration.parse(Files.readString(file)).anchors().minimumWidth());
    }

    @Test void everyLeafUsesTypedMetadataAndItsNativeDefault() throws Exception {
        var definition = definition(); Files.writeString(directory.resolve(definition.filename()), definition.defaults());
        var edits = new JsonEdits(directory, definition);
        assertEquals(edits.keys(), definition.fields().stream().map(SettingDefinition::key).toList());
        for (var field : definition.fields()) {
            assertEquals(edits.defaultValue(field.key()), field.defaultText(), field.key());
            assertDoesNotThrow(() -> field.parse(field.defaultText()), field.key());
            assertEquals(SettingsCatalog.field(definition.filename(), field.key()), edits.binding(field.key()).id());
        }
    }

    @Test void numericPresentationBoundsAgreeWithThePinnedCodecAtAndBeyondEachBoundary() {
        var definition = definition();
        for (var field : definition.fields()) {
            if (field.minimum() == null || field.maximum() == null) continue;
            for (var value : List.of(field.minimum().subtract(java.math.BigDecimal.ONE), field.minimum(),
                    field.maximum(), field.maximum().add(java.math.BigDecimal.ONE))) {
                String raw = value.toPlainString();
                boolean metadataAccepts;
                try { field.parse(raw); metadataAccepts = true; } catch (RuntimeException invalid) { metadataAccepts = false; }
                var tree = JsonParser.parseString(definition.defaults()).getAsJsonObject();
                putWithPairedEndpoint(tree, field.key(), value);
                boolean nativeAccepts;
                try { RiverConfiguration.parse(tree.toString()); nativeAccepts = true; } catch (RuntimeException invalid) { nativeAccepts = false; }
                assertEquals(nativeAccepts, metadataAccepts, field.key() + "=" + raw);
            }
            if (field.kind() == SettingDefinition.Kind.INTEGER) {
                String raw = field.minimum().add(new java.math.BigDecimal(".5")).toPlainString();
                var tree = JsonParser.parseString(definition.defaults()).getAsJsonObject();
                putWithPairedEndpoint(tree, field.key(), new java.math.BigDecimal(raw));
                assertThrows(RuntimeException.class, () -> RiverConfiguration.parse(tree.toString()), field.key());
                assertThrows(SettingIssue.InvalidValue.class, () -> field.parse(raw), field.key());
            }
        }
        var nearOne = new java.math.BigDecimal("1.00000000000000001");
        var tree = JsonParser.parseString(definition.defaults()).getAsJsonObject();
        tree.addProperty("headwater_lake_chance", nearOne);
        assertDoesNotThrow(() -> RiverConfiguration.parse(tree.toString()));
        assertDoesNotThrow(() -> definition.field("headwater_lake_chance").parse(nearOne.toString()));
    }

    private static void putWithPairedEndpoint(JsonObject tree, String key, java.math.BigDecimal value) {
        JsonObject object = tree;
        int dot = key.indexOf('.');
        if (dot >= 0) { object = tree.getAsJsonObject(key.substring(0, dot)); key = key.substring(dot + 1); }
        object.addProperty(key, value);
        String sibling = key.startsWith("minimum_") ? key.replaceFirst("minimum_", "maximum_")
                : key.startsWith("maximum_") ? key.replaceFirst("maximum_", "minimum_") : "";
        if (object.has(sibling)) object.addProperty(sibling, value);
    }

    @Test void identifiersUseTheirNativeCodecAndPreserveAuthoredValues() {
        var definition = definition();
        var fluid = definition.field("fluid_block");
        for (String accepted : List.of("water", "minecraft:water", "minecraft:lava", "lava"))
            assertEquals(accepted, fluid.parse(accepted));
        for (String raw : List.of("WATER", " water ", "", "not an identifier", "other:unregistered")) {
            var candidate = JsonParser.parseString(definition.defaults()).getAsJsonObject(); candidate.addProperty("fluid_block", raw);
            boolean nativeAccepts;
            try { RiverConfiguration.parse(candidate.toString()); nativeAccepts = true; } catch (RuntimeException invalid) { nativeAccepts = false; }
            boolean formAccepts;
            try { fluid.parse(raw); formAccepts = true; } catch (RuntimeException invalid) { formAccepts = false; }
            assertEquals(nativeAccepts, formAccepts, raw);
        }
        assertEquals("river", definition.field("temperate_biome").parse("river"));
        var invalid = assertThrows(SettingIssue.InvalidValue.class, () -> fluid.parse("not an identifier"));
        assertEquals("native_value", invalid.issue().code()); assertEquals("fluid_block", invalid.issue().field());
        var list = definition.field("excluded_biomes");
        assertEquals(List.of("river", "river"), list.parse("[\"river\",\"river\"]"));
        var badList = assertThrows(SettingIssue.InvalidValue.class, () -> list.parse("[\"Bad Biome\"]"));
        assertEquals("excluded_biomes", badList.issue().field());
        var tree = JsonParser.parseString(definition.defaults()).getAsJsonObject();
        tree.add("excluded_biomes", JsonParser.parseString("[\"river\",\"river\"]"));
        assertEquals(1, RiverConfiguration.parse(tree.toString()).excludedBiomes().size()); // Native profile deduplicates; authored input stays intact.
    }

    @Test void nativeCodecsDoNotSilentlyCoerceFractionalIntegersOrIgnoreUnknownSettings() {
        var tree = JsonParser.parseString(definition().defaults()).getAsJsonObject();
        tree.getAsJsonObject("anchors").addProperty("maximum_width", 12.5);
        assertThrows(IllegalArgumentException.class, () -> RiverConfiguration.parse(tree.toString()));
        tree.getAsJsonObject("anchors").addProperty("maximum_width", 12);
        tree.getAsJsonObject("anchors").addProperty("unknown", 1);
        assertThrows(IllegalArgumentException.class, () -> RiverConfiguration.parse(tree.toString()));
        assertThrows(IllegalArgumentException.class, () -> RiverConfiguration.parse("{\"enabled\":true,\"enabled\":false}"));
    }
}
