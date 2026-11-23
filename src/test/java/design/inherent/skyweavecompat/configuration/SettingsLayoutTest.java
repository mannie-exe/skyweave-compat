package design.inherent.skyweavecompat.configuration;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SettingsLayoutTest {
    @Test void allOwnedRecordAndExpandedMapPathsAdmitTheirAuthoredComposition() {
        var sources = new java.util.ArrayList<>(JsonSettings.FILES);
        if (sources.stream().noneMatch(source -> source.filename().equals("skyweave_compat-rivers.json")))
            sources.add(design.inherent.skyweavecompat.terrain.RiverConfiguration.definition());
        for (var source : sources) {
            var definition = ReformJsonStore.map(Path.of("unused-fixture"), source).definition();
            var layout = SettingsLayout.json(source);
            assertDoesNotThrow(() -> layout.requireCompatible(definition.type()), source.filename());
            if (source.help().equals("generation"))
                assertDoesNotThrow(() -> layout.requireCompatible(ProfileEditPlan.GENERATION.type()));
            if (source.help().equals("shapes"))
                assertDoesNotThrow(() -> layout.requireCompatible(ProfileEditPlan.SHAPES.type()));
        }
    }

    @Test void existingRelationshipsDoNotTurnUnorderedValuesIntoRangesOrDuplicateRangeControls() {
        var source = JsonSettings.find("skyweave_compat-generation.json");
        var layout = SettingsLayout.json(source);
        var spacing = layout.row(ReformDocuments.path("spacing")).orElseThrow();
        assertTrue(spacing.fields().contains(ReformDocuments.path("jitter")));
        assertTrue(layout.row(ReformDocuments.path("centerMinY")).isEmpty());
        assertTrue(layout.row(ReformDocuments.path("centerMaxY")).isEmpty());
        assertTrue(layout.group(ReformDocuments.path("centerMinY")).isEmpty());
        var cells = SettingsLayout.json(JsonSettings.find("skyweave_compat-shapes.json"));
        assertEquals(List.of(ReformDocuments.path("fractures.cellSize"), ReformDocuments.path("fractures.gapWidth")),
                cells.row(ReformDocuments.path("fractures.cellSize")).orElseThrow().fields());
        var variants = SettingsLayout.json(JsonSettings.find("skyweave_compat-oceans.json"));
        var group = variants.group(ReformDocuments.path("lagoonWeight")).orElseThrow();
        assertEquals(4, group.fields().size());
        assertEquals(2, variants.rows().stream().filter(row -> group.fields().contains(row.fields().getFirst())).count());
    }

    @Test void nativeCategoriesKeepTheirExactPathsAndDoNotInventSiblingPairs() {
        var fields = NativeFields.collect(com.AdamNew.dynamicwaters.Config.SPEC);
        var layout = SettingsLayout.nativeFields(fields);
        assertTrue(layout.rows().isEmpty());
        assertFalse(layout.groups().isEmpty());
        for (var field : fields) {
            var path = NativeSettingsPolicy.path(field);
            if (field.binding().getPath().size() > 1)
                assertTrue(layout.group(path).orElseThrow().fields().contains(path), field.key());
        }
        assertTrue(layout.groups().stream().anyMatch(group -> group.label().equals("River Flow Speeds")));
    }
}
