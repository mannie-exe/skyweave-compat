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
        assertEquals(before.field("timeLimitSeconds"), after.field("timeLimitSeconds"));
        assertEquals(before.field("timeLimitSeconds").anchor(), after.field("timeLimitSeconds").anchor());
        assertNotEquals(before.field("timeLimitSeconds"), before.field("candidateLimit"));
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
    @Test void realJsonBindingsShareCatalogIdentitiesAcrossIndependentSessions() throws Exception {
        var definition = JsonSettings.find("skyweave_compat-search.json");
        Files.writeString(directory.resolve(definition.filename()), definition.defaults());
        var first = new JsonEdits(directory, definition); var second = new JsonEdits(directory, definition);
        for (String key : first.keys()) {
            assertEquals(SettingsCatalog.field(definition.filename(), key), first.binding(key).id());
            assertEquals(first.binding(key).id(), second.binding(key).id());
        }
        first.set("timeLimitSeconds", "300");
        assertEquals(first.binding("timeLimitSeconds").id(), second.binding("timeLimitSeconds").id());
        assertEquals("600", second.value("timeLimitSeconds"));
    }
    @Test void everyRegisteredOwnedJsonDefinitionUsesAnAdmittedDocumentWithoutFieldCollisions() {
        var ids = new HashSet<SettingId>();
        for (var definition : JsonSettings.FILES) {
            var document = SettingsCatalog.file(definition.filename()); assertNotNull(document);
            for (var field : definition.fields()) assertTrue(ids.add(document.field(field.key())));
        }
    }
}
