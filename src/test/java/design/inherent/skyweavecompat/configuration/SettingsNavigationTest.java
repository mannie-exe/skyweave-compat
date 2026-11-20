package design.inherent.skyweavecompat.configuration;

import design.inherent.skyweavecompat.Feature;
import java.util.ArrayList;
import java.util.EnumSet;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SettingsNavigationTest {
    @Test void mergedPagesGiveEachSettingOneCanonicalRouteAcrossSystems() {
        var found = EnumSet.noneOf(Feature.class);
        var routes = new java.util.HashSet<String>();
        for (var page : SettingsCatalog.pages()) {
            assertTrue(routes.add(page.id()));
            assertTrue(page.files().stream().allMatch(file -> file.group() != null && file.presentOn(page.groups())));
            for (var feature : page.features()) {
                assertTrue(found.add(feature));
                assertEquals(page, SettingsCatalog.page(SettingsCatalog.group(feature)));
            }
        }
        assertEquals(EnumSet.allOf(Feature.class), found);
        assertEquals(SettingsCatalog.page(SettingsCatalog.Group.AUDIO), SettingsCatalog.page(SettingsCatalog.Group.AUDIO_CLIENT));
        assertNotEquals(SettingsCatalog.page(SettingsCatalog.Group.LINEAR), SettingsCatalog.page(SettingsCatalog.Group.LINEAR_GLASS));
    }
    @Test void oneOrderedCompositionOwnsBothSectionsAndEntries() {
        for (var page : SettingsCatalog.pages()) {
            var kinds = new java.util.HashSet<SettingsCatalog.SectionKind>();
            var ids = new java.util.HashSet<String>();
            var fixes = new ArrayList<Feature>();
            for (var section : page.sections()) {
                assertTrue(kinds.add(section.kind())); assertTrue(ids.add(section.id())); assertFalse(section.items().isEmpty());
                for (var item : section.items()) {
                    assertTrue(ids.add(item.id()));
                    if (item instanceof SettingsCatalog.Fix fix) fixes.add(fix.feature());
                    if (item instanceof SettingsCatalog.File file) assertNotEquals("journal", file.entry().help());
                }
            }
            assertEquals(page.features(), fixes);
        }
    }
}
