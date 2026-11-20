package design.inherent.skyweavecompat.maps;

import design.inherent.reform.owner.CommitReceipt;
import design.inherent.reform.owner.FileOwner;
import design.inherent.reform.owner.OwnerContext;
import design.inherent.skyweavecompat.configuration.ConfigLocations;
import design.inherent.skyweavecompat.configuration.ReformDocuments;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class MapZoomSettingsTest {
    @Test void timingDistinguishesLiveBoundsFromTheFirstViewOnlyInitialScale() {
        assertEquals(design.inherent.skyweavecompat.configuration.SettingsCatalog.Timing.NEW_VIEW,
                design.inherent.skyweavecompat.configuration.SettingsCatalog.timing(MapZoomSettings.FILE, "zoomInitial"));
        for (String key : java.util.List.of("zoomMinimum", "zoomMaximum", "zoomStep"))
            assertEquals(design.inherent.skyweavecompat.configuration.SettingsCatalog.Timing.IMMEDIATE,
                    design.inherent.skyweavecompat.configuration.SettingsCatalog.timing(MapZoomSettings.FILE, key));
    }
    @Test void publicationIsAtomicAndAnInvalidSavedDocumentCannotReplaceTheLastValidPolicy(@TempDir Path root) throws Exception {
        String valid = "{\"zoomMinimum\":0.25,\"zoomMaximum\":16,\"zoomInitial\":0,\"zoomStep\":1.5}";
        var source = ConfigLocations.file(root, MapZoomSettings.FILE);
        Files.createDirectories(source.getParent());
        Files.writeString(source, valid);
        try (var context = new OwnerContext(Runnable::run, Runnable::run, () -> {}, OwnerContext.Limits.STANDARD)) {
            var owner = new FileOwner<>(context, ReformDocuments.json(root, MapZoomSettings.FILE, MapZoomSettings.definition()), 4);
            try (var session = owner.open(context.access(() -> true, () -> true))) {
                assertEquals(CommitReceipt.Application.APPLIED, session.initialize().toCompletableFuture().join().application());
                var admitted = owner.handle().view();
                var published = admitted.value().orElseThrow();
                assertEquals(MapZoomSettings.parse(valid), published);
                assertEquals(.25, published.clamp(.125));
                assertEquals(1.5, published.scroll(1, 1));
                String invalid = valid.replace("16", "0.125");
                Files.writeString(source, invalid);
                assertFalse(session.initialize().toCompletableFuture().join().applicationReport().valid());
                assertSame(admitted.revision(), owner.handle().view().revision());
                assertEquals(published, owner.handle().view().value().orElseThrow());
                assertEquals(invalid, Files.readString(source));
            }
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
