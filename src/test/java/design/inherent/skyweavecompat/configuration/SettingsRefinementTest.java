package design.inherent.skyweavecompat.configuration;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import design.inherent.skyweavecompat.terrain.ShapeProfile;
import design.inherent.reform.edit.DraftLimits;
import design.inherent.reform.edit.DraftNode;
import design.inherent.reform.edit.DraftTree;
import design.inherent.reform.presentation.FormDependencies;
import design.inherent.reform.serialization.JsonLimits;
import design.inherent.reform.value.ValueShape;
import design.inherent.reform.value.ValueTypes;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SettingsRefinementTest {
    private static final JsonLimits JSON = new JsonLimits(ConfigFile.LIMIT, 16, ConfigFile.LIMIT, ConfigFile.LIMIT, 128, 4096);
    @Test void positiveDomainsAgreeAcrossMapAndTypedDefinitionsWithoutQuantizingTinyInput() {
        var climate = ReformDocuments.record("skyweave_compat-climate.json", design.inherent.skyweavecompat.climate.ClimateProfile.class,
                design.inherent.skyweavecompat.climate.ClimateProfile.DEFAULT);
        for (var definition : java.util.List.of(ProfileEditPlan.GENERATION, climate,
                ReformJsonStore.map(java.nio.file.Path.of("unused-fixture"), JsonSettings.find("skyweave_compat-generation.json")).definition(),
                ReformJsonStore.map(java.nio.file.Path.of("unused-fixture"), JsonSettings.find("skyweave_compat-climate.json")).definition())) {
            String key = definition.id().path().equals("generation") ? "activation" : "bottomDepth";
            var type = design.inherent.reform.edit.DraftTypes.declaredAt(definition.type(), ReformDocuments.path(key));
            var shape = assertInstanceOf(ValueShape.Scalar.class, type.shape());
            assertFalse(shape.domain().minimumInclusive());
            for (String zero : java.util.List.of("0", "-0.0"))
                assertFalse(type.codec().parse(com.mojang.serialization.JsonOps.INSTANCE, com.google.gson.JsonParser.parseString(zero)).isSuccess());
            for (String positive : java.util.List.of("1e-300", Double.toString(Double.MIN_VALUE)))
                assertTrue(type.codec().parse(com.mojang.serialization.JsonOps.INSTANCE, com.google.gson.JsonParser.parseString(positive)).isSuccess(), positive);
            var input = design.inherent.reform.presentation.NumericInput.open(shape, new DraftNode.Scalar("1e-300"));
            input.position();
            assertEquals("1e-300", input.expected().raw());
            if (key.equals("activation")) {
                assertTrue(input.select("percent").accepted());
                var lowerStep = design.inherent.reform.presentation.NumericInput.open(shape, new DraftNode.Scalar("0.01")).step(-1);
                assertFalse(lowerStep.accepted()); assertEquals("0.01", lowerStep.input().text());
            }
        }
    }

    @Test void zeroMeaningsUseSharedCaptionsAndRemainNumericInMapAndTypedCodecs() {
        for (String filename : java.util.List.of("skyweave_compat-wayfinder.json", "skyweave_compat-hoofprint.json")) {
            var map = ReformJsonStore.map(java.nio.file.Path.of("unused-fixture"), JsonSettings.find(filename)).definition();
            var typed = filename.contains("wayfinder") ? PolicyDefinitions.WAYFINDER : design.inherent.skyweavecompat.maps.MapZoomSettings.definition();
            String field = filename.contains("wayfinder") ? "cooldownTicks" : "zoomInitial";
            for (var definition : java.util.List.of(map, typed)) {
                var type = design.inherent.reform.edit.DraftTypes.declaredAt(definition.type(), ReformDocuments.path(field));
                assertTrue(type.codec().parse(com.mojang.serialization.JsonOps.INSTANCE, new com.google.gson.JsonPrimitive(0)).isSuccess());
                var caption = design.inherent.reform.presentation.NumericPresentation.caption(type, new DraftNode.Scalar("0")).orElseThrow();
                assertEquals(filename.contains("wayfinder") ? "Off" : "Native initial view", caption.fallback());
                assertTrue(design.inherent.reform.presentation.NumericPresentation.caption(type, new DraftNode.Scalar("1")).isEmpty());
                assertEquals("0", design.inherent.reform.presentation.NumericInput.open((ValueShape.Scalar)type.shape(), new DraftNode.Scalar("0")).text());
            }
        }
        var field = RecordSettings.fields(ShapeProfile.INITIAL).stream().filter(value -> value.key().equals("strengthVariation")).findFirst().orElseThrow();
        var type = ReformDocuments.scalarType(field.valueType(), field.constraints());
        assertEquals(new BigDecimal(".01"), ((ValueShape.Scalar)type.shape()).numericDisplay().orElseThrow().step());
    }
    @Test void nativeUnitsAreExplicitWithoutChangingTheirStoredDomainOrForeignFields() {
        var filename = "skyweave_compat-common.toml";
        var fields = NativeFields.collect(design.inherent.skyweavecompat.NeighborConfig.SPEC);
        var declared = NativeSettingsPolicy.explicitTypes(SettingsCatalog.file(filename), fields);
        for (var field : fields) {
            if (!java.util.Set.of("minimumGap", "maximumGap", "minimumIslandRadius").contains(field.key())) continue;
            var definition = field.definition(filename);
            assertEquals("blocks", definition.unit());
            assertEquals("", field.definition("foreign-common.toml").unit());
            var type = declared.get(NativeSettingsPolicy.path(field));
            assertNotNull(type);
            assertTrue(type.codec().parse(com.mojang.serialization.JsonOps.INSTANCE, new com.google.gson.JsonPrimitive(2048)).isSuccess());
            assertFalse(type.codec().parse(com.mojang.serialization.JsonOps.INSTANCE, new com.google.gson.JsonPrimitive(2049)).isSuccess());
            assertTrue(assertInstanceOf(ValueShape.Scalar.class, type.shape()).numericDisplay().isPresent());
        }
        assertTrue(NativeSettingsPolicy.explicitTypes(fields).isEmpty());
    }
    @Test void declaredUnitsConvertExactlyWithoutChangingStoredScales() {
        var ticks = display("ticks");
        assertEquals("2", ticks.fromStored("40", "seconds").text());
        assertEquals("40", ticks.toStored("2", "seconds").text());
        assertEquals(BigDecimal.valueOf(20), ticks.referenceTicksPerSecond().orElseThrow());
        assertEquals("35", display("probability").fromStored("0.35", "percent").text());
        assertEquals("0.35", display("percent").fromStored("35", "probability").text());
    }

    private static design.inherent.reform.presentation.NumericDisplay display(String unit) {
        var bounds = new SettingConstraints(null, null, false, BigDecimal.ONE, unit, "", -1, -1, false, "");
        return ((ValueShape.Scalar) ReformDocuments.display(ValueTypes.integer(0, 1000), bounds).shape())
                .numericDisplay().orElseThrow();
    }

    @Test void declaredDependenciesBindAndKeepInactiveValuesAndFullValidation() {
        for (var source : JsonSettings.FILES) {
            var store = ReformJsonStore.map(java.nio.file.Path.of("unused-fixture"), source);
            var definition = store.definition();
            var dependencies = SettingsCatalog.dependencies(source.filename());
            assertDoesNotThrow(() -> dependencies.bind(definition.type()), source.filename());
            for (var range : SettingsCatalog.ranges(source.filename())) {
                assertNotNull(source.field(range.minimum()));
                assertNotNull(source.field(range.maximum()));
            }
            if (!source.help().equals("shapes")) continue;
            var tree = DraftTree.from(definition.type(), definition.defaults(), DraftLimits.CONFIGURATION)
                    .change(ReformDocuments.path("fringe.enabled"), ignored -> new DraftNode.Scalar("false"));
            var original = tree.at(ReformDocuments.path("fringe.density"));
            var observation = tree.inspect(definition, JSON);
            assertEquals(FormDependencies.State.DISABLED, dependencies.bind(definition.type())
                    .evaluate(tree, observation.inputs()).at(ReformDocuments.path("fringe.density")).state());
            assertEquals(original, tree.at(ReformDocuments.path("fringe.density")));
            var invalid = tree.change(ReformDocuments.path("fringe.minimumSmoothFraction"), ignored -> new DraftNode.Scalar(".9"));
            assertFalse(invalid.inspect(definition, JSON).report().valid());
        }
    }

    @Test void referenceMetadataPreservesPortableUnknownIdsInBothOwnedMapAndTypedPolicy() {
        var source = JsonSettings.find("skyweave_compat-wayfinder.json");
        var json = ProfileJson.object(source.defaults());
        var ids = new com.google.gson.JsonArray();
        ids.add("future_mod:hazard"); ids.add("#future_mod:hazards");
        json.add("blockedBlocks", ids);
        assertDoesNotThrow(() -> source.validate().accept(json.toString()));
        var map = ReformJsonStore.map(java.nio.file.Path.of("unused-fixture"), source).definition();
        for (var definition : java.util.List.of(map, PolicyDefinitions.WAYFINDER)) {
            var type = design.inherent.reform.edit.DraftTypes.declaredAt(definition.type(), ReformDocuments.path("blockedBlocks"));
            var element = assertInstanceOf(ValueShape.Sequence.class, type.shape()).element();
            assertEquals(SettingsReferences.Source.BLOCK_SELECTORS.id(), assertInstanceOf(ValueShape.Reference.class, element.shape()).source());
            assertTrue(definition.type().codec().parse(com.mojang.serialization.JsonOps.INSTANCE, json).isSuccess());
        }
        assertEquals(java.util.List.of("future_mod:hazard", "#future_mod:hazards"),
                design.inherent.skyweavecompat.wayfinder.WayfinderSettings.parse(json.toString()).blockedBlocks());
    }

    @Test void typedTerrainDraftRetainsTheSameRelatedFieldIssueAsItsRuntimeParser() {
        var store = ReformDocuments.json(java.nio.file.Path.of("unused-fixture"), "skyweave_compat-shapes.json", ProfileEditPlan.SHAPES);
        var draft = DraftTree.from(store.definition().type(), store.definition().defaults(), DraftLimits.CONFIGURATION)
                .change(ReformDocuments.path("fringe.minimumSmoothFraction"), ignored -> new DraftNode.Scalar(".9"));
        var report = store.inspectDraft(draft).report();
        assertFalse(report.valid());
        assertTrue(report.issues().stream().anyMatch(issue -> issue.path().equals(ReformDocuments.path("fringe.minimumSmoothFraction"))
                && issue.related().contains(ReformDocuments.path("fringe.maximumSmoothFraction"))), report.toString());
    }

    @Test void nestedScalarRuntimeErrorsKeepTheirFullSourcePath() {
        var source = JsonSettings.find("skyweave_compat-shapes.json");
        var value = ProfileJson.object(source.defaults());
        set(value, "outline.maximumAspectRatio", new com.google.gson.JsonPrimitive(3));
        var failure = assertThrows(RuntimeException.class, () -> source.validate().accept(value.toString()));
        assertEquals("outline.maximumAspectRatio", SettingIssue.find(failure).field());
    }

    @Test void fractionalStrengthIncrementPreservesUsefulPrecision() {
        var field = RecordSettings.fields(ShapeProfile.INITIAL).stream()
                .filter(value -> value.key().equals("strengthVariation")).findFirst().orElseThrow();
        assertEquals("0.16", field.adjust("0.15", 1));
        assertEquals("0.14", field.adjust("0.15", -1));
        assertDoesNotThrow(() -> field.parse("0.15000000000000002"));
    }

    @Test void coupledOwnerFailuresIdentifyFieldsWithoutParsingTheirMessages() {
        issue("generation", "jitter", "1600", "jitter", "spacing");
        issue("generation", "centerMinY", "600", "centerMinY", "centerMaxY");
        issue("generation", "anchorMinRadius", "400", "anchorMinRadius", "anchorMaxRadius");
        issue("generation", "anchorMinHeight", "80", "anchorMinHeight", "anchorPlateau");
        issue("generation", "anchorMaxHeight", "200", "anchorMinHeight", "anchorMaxHeight");
        issue("generation", "companionMinRadius", "150", "companionMinRadius", "companionMaxRadius");
        issue("generation", "companionMinOrbit", "700", "companionMinOrbit", "companionMaxOrbit");
        issue("generation", "companionMinOffsetY", "100", "companionMinOffsetY", "companionMaxOffsetY");
        issue("generation", "floorClearance", "800", "floorClearance", "buildingHeadroom");
        issue("interiors", "noiseChance", "0.9", "noiseChance", "hollowChance");
        issue("shapes", "fringe.minimumSmoothFraction", "0.9", "fringe.minimumSmoothFraction", "fringe.maximumSmoothFraction");
        issue("shapes", "fractures.cellSize", "32", "fractures.gapWidth", "fractures.cellSize", "fractures.gapWidth", "12");
        issue("oceans", "coastalChance", "1", "coastalChance", "perimeterChance", "perimeterChance", "0.5");
        issue("oceans", "coveOpeningDegrees", "30", "coveShoulderDegrees", "coveOpeningDegrees");
        issue("encounters", "spawners.rules.gelid.priority", "10", "spawners.rules.gelid.priority", "spawners.rules.husk.priority");
    }

    private static void issue(String name, String changed, String value, String primary, String related, String... additional) {
        var source = JsonSettings.FILES.stream().filter(file -> file.help().equals(name)).findFirst().orElseThrow();
        var json = ProfileJson.object(source.defaults());
        set(json, changed, com.google.gson.JsonParser.parseString(value));
        for (int i = 0; i < additional.length; i += 2)
            set(json, additional[i], com.google.gson.JsonParser.parseString(additional[i + 1]));
        var failure = assertThrows(RuntimeException.class, () -> source.validate().accept(json.toString()), changed);
        var issue = SettingIssue.find(failure);
        assertNotNull(issue, changed + ": " + failure);
        assertEquals(primary, issue.field());
        assertTrue(issue.related().contains(related), issue.toString());
        assertTrue(source.fields().stream().anyMatch(field -> field.key().equals(primary)));
        assertTrue(source.fields().stream().anyMatch(field -> field.key().equals(related)));
    }

    private static void set(JsonObject object, String path, JsonElement value) {
        var parts = path.split("\\.");
        for (int i = 0; i < parts.length - 1; i++) object = object.getAsJsonObject(parts[i]);
        object.add(parts[parts.length - 1], value);
    }
}
