package design.inherent.skyweavecompat.configuration;

import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class JsonEditsTest {
    @TempDir Path directory;
    private JsonEdits create(String help) throws IOException {
        var definition = JsonSettings.FILES.stream().filter(file -> file.help().equals(help)).findFirst().orElseThrow();
        Files.writeString(directory.resolve(definition.filename()), definition.defaults());
        if (help.equals("generation")) {
            var shapes = JsonSettings.find("skyweave_compat-shapes.json");
            Files.writeString(directory.resolve(shapes.filename()), shapes.defaults());
        }
        return new JsonEdits(directory, definition);
    }
    @Test void coupledZoomIssuesRetainFieldIdentityThroughTheOwningParser() throws IOException {
        var edits = create("hoofprint_zoom");
        String original = Files.readString(directory.resolve(edits.definition().filename()));
        edits.set("zoomMinimum", "64");
        var issue = edits.issues().getFirst();
        assertEquals("zoomMinimum", issue.field()); assertTrue(issue.affects("zoomMaximum"));
        assertFalse(issue.affects("zoomInitial"));
        edits.set("zoomMaximum", "96");
        issue = edits.issues().getFirst();
        assertEquals("zoomInitial", issue.field()); assertTrue(issue.affects("zoomMinimum"));
        edits.set("zoomInitial", "0"); assertTrue(edits.issues().isEmpty());
        assertEquals(original, Files.readString(directory.resolve(edits.definition().filename())));
        edits.discard(); assertEquals(edits.savedValue("zoomInitial"),edits.value("zoomInitial"));
    }
    @Test void canonicalOceanEditsPreserveValuesAndDetectExternalChanges() throws IOException {
        var definition = JsonSettings.find("skyweave_compat-oceans.json");
        var json = JsonParser.parseString(definition.defaults()).getAsJsonObject();
        json.addProperty("enabled", false); json.addProperty("coastalChance", .04);
        var original = json.toString();
        var path = directory.resolve(definition.filename()); Files.writeString(path, original);
        var edits = new JsonEdits(directory, definition);
        assertEquals(original, Files.readString(path)); assertFalse(edits.dirty());
        assertEquals("0.2", edits.value("admissionChance")); assertEquals("false", edits.value("enabled"));
        edits.set("spireClearance", "24");
        Files.writeString(path, original + " ");
        assertThrows(ConfigFile.ChangedOnDisk.class, edits::save); assertTrue(edits.dirty());
        Files.writeString(path, original); edits.save();
        var saved = design.inherent.skyweavecompat.terrain.OceanConfiguration.parse(Files.readString(path));
        assertFalse(saved.enabled()); assertEquals(.04, saved.coastalChance()); assertEquals(24, saved.spireClearance());
        assertEquals(.20, saved.admissionChance());
        assertThrows(IllegalArgumentException.class, () -> definition.validate().accept(original.replace("\"enabled\":false,", "")));
    }
    @Test void structureFamiliesUseTheSharedTypedEditorAndRetainOtherRules() throws IOException {
        var edits = create("structures");
        var path = directory.resolve(edits.definition().filename());
        var before = design.inherent.skyweavecompat.terrain.structure.StructureConfiguration.parse(Files.readString(path));
        edits.set("families.villages.maximumPerCluster", "4");
        edits.set("families.villages.targetStartsPer1024Chunks", ".75");
        edits.save();
        var after = design.inherent.skyweavecompat.terrain.structure.StructureConfiguration.parse(Files.readString(path));
        assertEquals(4, after.rule(design.inherent.skyweavecompat.terrain.structure.StructureFamily.VILLAGES).maximumPerCluster());
        assertEquals(.75, after.rule(design.inherent.skyweavecompat.terrain.structure.StructureFamily.VILLAGES).targetStartsPer1024Chunks());
        assertEquals(before.rule(design.inherent.skyweavecompat.terrain.structure.StructureFamily.PORTALS),
                after.rule(design.inherent.skyweavecompat.terrain.structure.StructureFamily.PORTALS));
        edits.set("families.villages.maximumPerCluster", "1.5");
        String saved = Files.readString(path);
        var invalid = assertThrows(SettingIssue.InvalidValue.class, edits::save);
        assertEquals("families.villages.maximumPerCluster", invalid.issue().field());
        assertEquals("type", invalid.issue().code());
        assertEquals(saved, Files.readString(path));
        assertTrue(edits.dirty());
    }
    @Test void stagedValuesDoNotChangeRuntimeOrDiskAndRoundTripThroughTheOwningParser() throws IOException {
        var edits = create("generation");
        var path = directory.resolve(edits.definition().filename());
        String baseline = Files.readString(path);
        edits.set("spacing", "2600");
        assertTrue(edits.dirty()); assertEquals(baseline, Files.readString(path));
        assertEquals(2600, ProfileJson.generation(edits.candidate()).spacing());
        edits.save(); assertFalse(edits.dirty()); assertEquals("2600", edits.value("spacing"));
        assertEquals(2600, ProfileJson.generation(Files.readString(path)).spacing());
    }
    @Test void invalidDraftRetainsItsTextAndCannotOverwriteDisk() throws IOException {
        var edits = create("oceans"); Path path = directory.resolve(edits.definition().filename());
        String original = Files.readString(path);
        edits.set("coveShoulderDegrees", "9.5");
        assertThrows(RuntimeException.class, edits::save); assertEquals("9.5", edits.value("coveShoulderDegrees"));
        assertEquals(original, Files.readString(path));
        edits.set("coveShoulderDegrees", "55");
        assertThrows(RuntimeException.class, edits::save); // Individual type is valid, linked maximum is not.
        edits.set("coveOpeningDegrees", "120"); edits.save(); assertFalse(edits.dirty());
    }
    @Test void conflictRetainsDraftAndExternalBytesWhileFailedReloadKeepsTheBaseline() throws IOException {
        var edits = create("interiors"); Path path = directory.resolve(edits.definition().filename());
        edits.set("shellThickness", "16");
        String external = Files.readString(path).replace("12", "14"); Files.writeString(path, external);
        assertThrows(ConfigFile.ChangedOnDisk.class, edits::save); assertEquals(external, Files.readString(path));
        Files.writeString(path, "{"); assertThrows(IOException.class, edits::reload);
        assertTrue(edits.dirty()); assertEquals("16", edits.value("shellThickness"));
        Files.writeString(path, external); edits.reload(); assertFalse(edits.dirty());
        assertEquals("14", edits.value("shellThickness"));
    }
    @Test void arraysBooleansAndDefaultsRemainTyped() throws IOException {
        var maps = create("bluemap"); maps.strings("maps", List.of("overworld", "island-map")); maps.save();
        assertEquals(List.of("overworld", "island-map"), maps.strings("maps"));
        maps.strings("maps", List.of("same", "same")); assertThrows(IllegalArgumentException.class, maps::save);
        maps.discard(); assertFalse(maps.dirty());
        var oceans = create("oceans"); oceans.set("enabled", "false"); oceans.save();
        assertFalse(JsonParser.parseString(Files.readString(directory.resolve(oceans.definition().filename())))
                .getAsJsonObject().get("enabled").getAsBoolean());
        oceans.reset("enabled"); assertTrue(oceans.dirty()); oceans.save(); assertEquals("true", oceans.value("enabled"));
    }
    @Test void missingDuplicateUnknownAndOversizedInputsAreNotRepairedOrMaterialized() throws IOException {
        var definition = JsonSettings.FILES.getFirst(); Path path = directory.resolve(definition.filename());
        assertThrows(IOException.class, () -> new JsonEdits(directory, definition)); assertFalse(Files.exists(path));
        assertThrows(IOException.class, () -> JsonEdits.object("{\"spacing\":1,\"spacing\":2}"));
        Files.writeString(path, definition.defaults().replace("{", "{\"unknown\":1,"));
        assertThrows(IllegalArgumentException.class, () -> new JsonEdits(directory, definition));
        Files.writeString(path, " ".repeat(ConfigFile.LIMIT + 1));
        assertThrows(IOException.class, () -> new JsonEdits(directory, definition));
        assertThrows(IOException.class, () -> new ConfigFile(directory, "../outside.json"));
    }
    @Test void fieldOrderComesFromTheOwnedSchemaRatherThanFileFormatting() throws Exception {
        var definition = JsonSettings.find("skyweave_compat-generation.json");
        var original = JsonParser.parseString(definition.defaults()).getAsJsonObject();
        var keys = new java.util.ArrayList<>(original.keySet()); var reversed = new com.google.gson.JsonObject();
        for (int i = keys.size() - 1; i >= 0; i--) reversed.add(keys.get(i), original.get(keys.get(i)));
        Files.writeString(directory.resolve(definition.filename()), reversed.toString());
        var edits = new JsonEdits(directory, definition); assertEquals(keys, edits.keys());
        assertEquals("2400", edits.value("spacing"));
    }
    @Test void nestedGroupsRetainTheAuthoredOrderAcrossReadbackAndReformatting() throws Exception {
        var edits = create("shapes");
        var defaults = JsonParser.parseString(edits.definition().defaults()).getAsJsonObject();
        var expectedGroups = new java.util.ArrayList<>(defaults.keySet());
        assertEquals(expectedGroups, edits.keys().stream().map(key -> key.split("\\.")[0]).distinct().toList());
        var reversed = new com.google.gson.JsonObject();
        for (String group : expectedGroups.reversed()) reversed.add(group, defaults.get(group));
        Files.writeString(directory.resolve(edits.definition().filename()), reversed.toString());
        edits.reload();
        assertEquals(expectedGroups, edits.keys().stream().map(key -> key.split("\\.")[0]).distinct().toList());
    }
    @Test void installationParsersRejectTheSameAmbiguousKeysAsTheEditor() {
        assertAll(JsonSettings.FILES.stream()
                .filter(file -> List.of("generation", "interiors", "oceans").contains(file.help()))
                .map(file -> (org.junit.jupiter.api.function.Executable) () -> {
                    assertDoesNotThrow(() -> file.validate().accept(file.defaults()));
                    var tree = JsonParser.parseString(file.defaults()).getAsJsonObject();
                    String key = tree.keySet().iterator().next();
                    String duplicate = "{\"" + key + "\":" + tree.get(key) + "," + file.defaults().substring(file.defaults().indexOf('{') + 1);
                    assertThrows(IllegalArgumentException.class, () -> file.validate().accept(duplicate), file.filename());
                }));
    }
}
