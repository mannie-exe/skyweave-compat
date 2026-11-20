package design.inherent.skyweavecompat.configuration;

import design.inherent.skyweavecompat.wayfinder.WayfinderSettings;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SettingDefinitionTest {
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
    @Test void runtimeAndDeclaredFieldUseTheSameBounds() {
        var definition = JsonSettings.find(WayfinderSettings.FILE);
        var field = definition.field("placementLookupHeight");
        assertEquals(new java.math.BigDecimal(64), field.maximum());
        var issue = assertThrows(SettingIssue.InvalidValue.class, () -> field.parse("65"));
        assertEquals("placementLookupHeight", issue.issue().field());
        assertEquals("maximum", issue.issue().code());
        var source = com.google.gson.JsonParser.parseString(definition.defaults()).getAsJsonObject();
        source.addProperty("placementLookupHeight", 65);
        assertThrows(SettingIssue.InvalidValue.class, () -> WayfinderSettings.parse(source.toString()));
        source.addProperty("placementLookupHeight", 64);
        assertEquals(64, WayfinderSettings.parse(source.toString()).placementLookupHeight());
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
        var value = com.google.gson.JsonParser.parseString(definition.defaults()).getAsJsonObject();
        value.add("blockedBlocks", gson.toJsonTree(ids));
        assertEquals(ids, WayfinderSettings.parse(value.toString()).blockedBlocks());
    }
    @Test void everyOwnedDocumentDeclaresTypedDefaultsAcceptedByItsRuntimeParser() {
        for (var definition : JsonSettings.FILES) {
            assertFalse(definition.fields().isEmpty(), definition.filename());
            for (var field : definition.fields())
                assertDoesNotThrow(() -> field.parse(field.defaultText()), field.key());
            assertDoesNotThrow(() -> definition.validate().accept(definition.defaults()), definition.filename());
        }
    }

    @Test void crossFieldIssuesDoNotPretendTheLastEditedFieldIsResponsible() {
        var definition = JsonSettings.find("skyweave_compat-hoofprint.json");
        var value = com.google.gson.JsonParser.parseString(definition.defaults()).getAsJsonObject();
        value.addProperty("zoomMinimum", 4);
        var invalid = assertThrows(IllegalArgumentException.class, () -> definition.validate().accept(value.toString()));
        var issue = SettingIssue.find(invalid);
        assertNotNull(issue);
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
    @Test void explicitSteppingClampsAtBoundsWithoutClampingInvalidAuthoredInput() {
        var field = JsonSettings.find(WayfinderSettings.FILE).field("placementLookupHeight");
        assertEquals("64", field.adjust("64", 1)); assertEquals("0", field.adjust("0", -1));
        assertThrows(SettingIssue.InvalidValue.class, () -> field.adjust("65", -1));
        assertThrows(SettingIssue.InvalidValue.class, () -> field.adjust("-", 1));
        var decimal = JsonSettings.find("skyweave_compat-climate.json").field("bottomDepth");
        assertEquals(".01", decimal.adjust(".01", -1));
        assertEquals("0.11", decimal.adjust(".01", 1));
        var probability = JsonSettings.find("skyweave_compat-shapes.json").field("outline.aspectChance");
        var hugeScale = assertThrows(SettingIssue.InvalidValue.class, () -> probability.adjust("1e-1000000000", 1));
        assertEquals("precision", hugeScale.issue().code());
    }
    @Test void decimalBoundsUseTheNativeTypeWhileRetainingAuthoredPrecision() {
        var definition = JsonSettings.find("skyweave_compat-shapes.json");
        var field = definition.field("fractures.deepDarkChance");
        String boundary = "0.100000000000000001";
        assertEquals(new java.math.BigDecimal(boundary), field.parse(boundary));
        var value = com.google.gson.JsonParser.parseString(definition.defaults()).getAsJsonObject();
        value.getAsJsonObject("fractures").addProperty("deepDarkChance", new java.math.BigDecimal(boundary));
        assertEquals(.1, design.inherent.skyweavecompat.terrain.ShapeConfiguration.parse(value.toString()).fractures().deepDarkChance());
        var invalid = assertThrows(SettingIssue.InvalidValue.class, () -> field.parse("0.1000000000000001"));
        assertEquals("maximum", invalid.issue().code());
    }

}
