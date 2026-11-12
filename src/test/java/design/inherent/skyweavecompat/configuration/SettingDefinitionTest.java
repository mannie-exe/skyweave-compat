package design.inherent.skyweavecompat.configuration;

import design.inherent.skyweavecompat.wayfinder.WayfinderSettings;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class SettingDefinitionTest {
    @TempDir Path directory;
    enum Mode { FIRST, SECOND }
    public record Sample(long exact, double decimal, Mode mode, List<String> labels) {}

    @Test void exactLongsDecimalsAndChoicesDoNotRoundThroughFloatingPoint() {
        var fields = RecordSettings.fields(new Sample(1, .5, Mode.FIRST, List.of("one")));
        assertEquals(9007199254740993L, fields.get(0).parse("9007199254740993"));
        assertThrows(SettingIssue.InvalidValue.class, () -> fields.get(0).parse("9007199254740993.5"));
        assertThrows(SettingIssue.InvalidValue.class, () -> fields.get(0).parse("9223372036854775808"));
        assertEquals(new java.math.BigDecimal(".10000000000000000001"), fields.get(1).parse(".10000000000000000001"));
        for (String value : List.of("NaN", "Infinity", "1e309"))
            assertThrows(SettingIssue.InvalidValue.class, () -> fields.get(1).parse(value));
        assertEquals(List.of("FIRST", "SECOND"), fields.get(2).choices());
        assertEquals(Mode.SECOND, fields.get(2).parse("SECOND"));
        assertThrows(SettingIssue.InvalidValue.class, () -> fields.get(2).parse("second"));
    }
    @Test void incompleteDraftStatesAreNotRuntimeValues() {
        var field = JsonSettings.find(WayfinderSettings.FILE).field("cooldownTicks");
        assertEquals(SettingDefinition.InputState.EMPTY, field.inspect("").state());
        for (String partial : List.of("-", ".", "1e", "1e-"))
            assertEquals(SettingDefinition.InputState.INCOMPLETE, field.inspect(partial).state());
        assertEquals(SettingDefinition.InputState.INVALID, field.inspect("1.5").state());
        assertEquals(SettingDefinition.InputState.VALID, field.inspect("0").state());
        assertEquals(0, WayfinderSettings.DEFAULT.cooldownTicks());
    }
    @Test void sharedBoundsRejectThroughBothRuntimeConstructorAndEditor() throws Exception {
        var definition = JsonSettings.find(WayfinderSettings.FILE);
        Files.writeString(directory.resolve(definition.filename()), definition.defaults());
        var edits = new JsonEdits(directory, definition);
        assertEquals(new java.math.BigDecimal(64), edits.field("placementLookupHeight").maximum());
        edits.set("placementLookupHeight", "65");
        assertEquals("placementLookupHeight", edits.issues().getFirst().field());
        assertEquals("maximum", edits.issues().getFirst().code());
        assertThrows(SettingIssue.InvalidValue.class, edits::save);
        var source = com.google.gson.JsonParser.parseString(definition.defaults()).getAsJsonObject();
        source.addProperty("placementLookupHeight", 65);
        assertThrows(SettingIssue.InvalidValue.class, () -> WayfinderSettings.parse(source.toString()));
        edits.set("placementLookupHeight", "64"); edits.save();
        assertEquals(64, WayfinderSettings.parse(Files.readString(directory.resolve(definition.filename()))).placementLookupHeight());
    }
    @Test void textCollectionBoundsAndShapeComeFromTheOwner() throws Exception {
        var definition = JsonSettings.find(WayfinderSettings.FILE);
        var field = definition.field("blockedBlocks"); assertEquals(256, field.maximumEntries());
        var ids = IntStream.range(0, 256).mapToObj(i -> "example:block_" + i).toList();
        var gson = new com.google.gson.Gson();
        assertEquals(ids, field.parse(gson.toJson(ids)));
        for (String input : List.of("[\"same\",\"same\"]", "[1]", "null", "[\"example:ok\"],\"other\":0", "[\"bad id\"]"))
            assertThrows(SettingIssue.InvalidValue.class, () -> field.parse(input));
        var tooMany = new java.util.ArrayList<>(ids); tooMany.add("example:last");
        assertThrows(SettingIssue.InvalidValue.class, () -> field.parse(gson.toJson(tooMany)));
        Files.writeString(directory.resolve(definition.filename()), definition.defaults());
        var edits = new JsonEdits(directory, definition); edits.strings("blockedBlocks", ids); edits.save();
        assertEquals(ids, WayfinderSettings.parse(Files.readString(directory.resolve(definition.filename()))).blockedBlocks());
    }
    @Test void everyOwnedDocumentHasTypedFieldsAndKeepsItsDefaultSerialization() throws Exception {
        for (var definition : JsonSettings.FILES) {
            assertFalse(definition.fields().isEmpty(), definition.filename());
            Files.writeString(directory.resolve(definition.filename()), definition.defaults());
            var edits = new JsonEdits(directory, definition);
            assertEquals(edits.keys(), definition.fields().stream().map(SettingDefinition::key).toList());
            for (var field : definition.fields()) {
                assertDoesNotThrow(() -> field.parse(edits.defaultValue(field.key())), field.key());
                edits.reset(field.key());
            }
            assertFalse(edits.dirty(), definition.filename());
            assertTrue(edits.issues().isEmpty(), definition.filename());
        }
    }
    @Test void crossFieldIssuesDoNotPretendTheLastEditedFieldIsResponsible() throws Exception {
        var definition = JsonSettings.find("skyweave_compat-hoofprint.json");
        Files.writeString(directory.resolve(definition.filename()), definition.defaults());
        var edits = new JsonEdits(directory, definition);
        edits.set("zoomMinimum", "4");
        var issue = edits.issues().getFirst();
        assertEquals("zoomInitial", issue.field());
        assertEquals(List.of("zoomMinimum", "zoomMaximum"), issue.related());
        assertEquals("zoom_range", issue.code());
    }
    @Test void ownedSetParsingCannotSilentlyDeduplicateMalformedSavedValues() {
        var definition = JsonSettings.find("skyweave_compat-bluemap.json");
        var value = com.google.gson.JsonParser.parseString(definition.defaults()).getAsJsonObject();
        value.add("maps", new com.google.gson.Gson().toJsonTree(List.of("same", "same")));
        var issue = assertThrows(SettingIssue.InvalidValue.class, () -> definition.validate().accept(value.toString()));
        assertEquals("unique", issue.issue().code()); assertEquals("maps", issue.issue().field());
    }
}
