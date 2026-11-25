package design.inherent.skyweavecompat.configuration;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class SettingIdentityTest {
    @TempDir Path directory;
    @Test void documentAndFieldIdentitySurviveFilePageAndLabelChanges() {
        var before = SettingsCatalog.file("skyweave_compat-search.json");
        var after = new SettingsCatalog.FileEntry("different-file.json", before.id(), SettingsCatalog.Group.NAVIGATION,
                "different_label", before.owners(), before.section());
        assertEquals(new SettingsCatalog.File(before).id(), new SettingsCatalog.File(after).id());
        assertEquals(before.field("tickBudgetMillis"), after.field("tickBudgetMillis"));
        assertEquals(before.field("tickBudgetMillis").anchor(), after.field("tickBudgetMillis").anchor());
        assertNotEquals(before.field("tickBudgetMillis"), before.field("concurrentChunks"));
        assertNotEquals(before.field("enabled"), SettingsCatalog.file("skyweave_compat-shapes.json").field("enabled"));
    }
    @Test void admittedDocumentsAreUniqueAndReverseLookupDoesNotAcceptPaths() {
        var ids = new HashSet<String>(); var files = new HashSet<String>();
        for (var file : SettingsCatalog.FILES) {
            assertTrue(ids.add(file.id()), file.id()); assertTrue(files.add(file.filename()), file.filename());
            assertSame(file, SettingsCatalog.document(file.id())); assertSame(file, SettingsCatalog.file(file.filename()));
        }
        assertNull(SettingsCatalog.document("../skyweave_compat-wayfinder.json"));
        assertNull(SettingsCatalog.document("skyweave_compat:not_admitted"));
        assertThrows(IllegalArgumentException.class, () -> SettingsCatalog.field("not-admitted.json", "key"));
        assertThrows(IllegalArgumentException.class, () -> new SettingId("a filename", "key"));
        assertThrows(IllegalArgumentException.class, () -> new SettingId("skyweave_compat:search", " "));
    }
    @Test void featureFileReferencesResolveTheStartupOwnerRatherThanCatalogPosition() {
        for (var feature : design.inherent.skyweavecompat.Feature.values()) {
            var file = SettingsCatalog.file(SettingsCatalog.entry(feature).filename());
            assertNotNull(file);
            assertEquals(design.inherent.skyweavecompat.FeatureSettings.FILE, file.filename());
            assertNull(file.group());
            assertSame(SettingsCatalog.document("skyweave_compat:features"), file);
        }
    }
    @Test void realJsonStoresExposeStableDocumentsAndIndependentDrafts() throws Exception {
        var definition = JsonSettings.find("skyweave_compat-search.json");
        Files.createDirectories(ConfigLocations.ownedDirectory(directory));
        var path = ConfigLocations.file(directory, definition.filename());
        Files.writeString(path, definition.defaults());
        var first = ReformJsonStore.map(directory, definition);
        var second = ReformJsonStore.map(directory, definition);
        assertEquals(SettingsCatalog.file(definition.filename()).id(), first.definition().id().toString());
        assertEquals(first.definition().id(), second.definition().id());
        var a = first.read().draft().orElseThrow();
        var b = second.read().draft().orElseThrow();
        for (var field : definition.fields()) {
            assertTrue(a.contains(ReformDocuments.path(field.key())));
            assertTrue(b.contains(ReformDocuments.path(field.key())));
        }
        var changed = a.change(ReformDocuments.path("tickBudgetMillis"),
                ignored -> new design.inherent.reform.edit.DraftNode.Scalar("3"));
        assertEquals("3", ((design.inherent.reform.edit.DraftNode.Scalar) changed.at(ReformDocuments.path("tickBudgetMillis"))).raw());
        assertEquals("5", ((design.inherent.reform.edit.DraftNode.Scalar) b.at(ReformDocuments.path("tickBudgetMillis"))).raw());
        assertEquals(definition.defaults(), Files.readString(path));
    }
    @Test void everyRegisteredOwnedJsonDefinitionUsesAnAdmittedDocumentWithoutFieldCollisions() {
        var ids = new HashSet<SettingId>();
        for (var definition : JsonSettings.FILES) {
            var document = SettingsCatalog.file(definition.filename()); assertNotNull(document);
            for (var field : definition.fields()) assertTrue(ids.add(document.field(field.key())));
        }
    }
}
