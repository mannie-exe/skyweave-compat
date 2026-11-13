package design.inherent.skyweavecompat.maps;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MapZoomSettingsTest {
    @Test void timingDistinguishesLiveBoundsFromTheFirstViewOnlyInitialScale() {
        assertEquals(design.inherent.skyweavecompat.configuration.SettingsCatalog.Timing.NEW_VIEW,
                design.inherent.skyweavecompat.configuration.SettingsCatalog.timing(MapZoomSettings.FILE, "zoomInitial"));
        for (String key : java.util.List.of("zoomMinimum", "zoomMaximum", "zoomStep"))
            assertEquals(design.inherent.skyweavecompat.configuration.SettingsCatalog.Timing.IMMEDIATE,
                    design.inherent.skyweavecompat.configuration.SettingsCatalog.timing(MapZoomSettings.FILE, key));
    }
    @Test void publicationIsAtomicAndAnInvalidSavedDocumentCannotReplaceTheLastValidPolicy() {
        String valid = "{\"zoomMinimum\":0.25,\"zoomMaximum\":16,\"zoomInitial\":0,\"zoomStep\":1.5}";
        var previous = MapZoomSettings.current();
        try {
            MapZoomSettings.acceptSaved(valid);
            var published = MapZoomSettings.current();
            assertEquals(.25, published.clamp(.125));
            assertEquals(1.5, published.scroll(1, 1));
            assertThrows(IllegalArgumentException.class, () -> MapZoomSettings.acceptSaved(valid.replace("16", "0.01")));
            assertSame(published, MapZoomSettings.current());
        } finally {
            MapZoomSettings.acceptSaved(new com.google.gson.Gson().toJson(previous));
        }
    }
    @Test void fractionalDeltasAccumulateWithoutChangingNativeGuiScale() {
        var profile = MapZoomSettings.DEFAULT;
        double scale = 1;
        for (int tick = 0; tick < 10; tick++) scale = profile.scroll(scale, .1);
        assertEquals(profile.scroll(1, 1), scale, 1e-12);
        assertEquals(1, profile.scroll(scale, -1), 1e-12);
        assertEquals(1, profile.scroll(1, Double.NaN));
        assertEquals(1, profile.scroll(1, Double.POSITIVE_INFINITY));
    }
    @Test void boundsRemainFiniteAndZoomCanShowSeveralClusterIntervals() {
        var profile = MapZoomSettings.DEFAULT;
        double scale = 1;
        for (int i = 0; i < 100; i++) scale = profile.scroll(scale, -1);
        assertEquals(.125, scale);
        assertTrue(854 / scale > 2 * 2400);
        for (int i = 0; i < 100; i++) scale = profile.scroll(scale, 1);
        assertEquals(32, scale);
        assertEquals(.125, profile.scroll(1, -Double.MAX_VALUE));
        assertEquals(32, profile.scroll(1, Double.MAX_VALUE));
    }
    @Test void codecRejectsInvalidRangesCoercionAndUnknownKeys() {
        String valid = "{\"zoomMinimum\":0.125,\"zoomMaximum\":32,\"zoomInitial\":0,\"zoomStep\":1.25}";
        assertEquals(0, MapZoomSettings.parse(valid).zoomInitial());
        assertThrows(IllegalArgumentException.class, () -> MapZoomSettings.parse(valid.replace("0.125", "\"0.125\"")));
        assertThrows(IllegalArgumentException.class, () -> MapZoomSettings.parse(valid.replace("32", "0.01")));
        assertThrows(IllegalArgumentException.class, () -> MapZoomSettings.parse(valid.replace("\"zoomInitial\":0", "\"zoomInitial\":1000")));
        assertThrows(IllegalArgumentException.class, () -> new MapZoomSettings(Double.NaN,32,1,1.25));
    }
}
