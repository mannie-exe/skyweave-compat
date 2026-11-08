package design.inherent.skyweavecompat;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class WaterFeatureSelectionTest {
    private static FeatureSettings only(Feature... enabled) {
        var values = new EnumMap<Feature, Boolean>(Feature.class);
        for (var feature : Feature.values()) values.put(feature, false);
        for (var feature : enabled) values.put(feature, true);
        return new FeatureSettings(values);
    }
    @Test void waterHooksRequireBothTheActualGeneratorAndSupportedUpstream() {
        var mods = Map.of("sky_archipelago", "1.4", "dynamicwaters", "13.1.0");
        for (boolean client : new boolean[]{false, true}) {
            var settings = only(Feature.TERRAIN, Feature.DYNAMIC_WATERS);
            assertTrue(FeatureSelection.select(settings, mods, client).enabled(Feature.DYNAMIC_WATERS));
            assertFalse(FeatureSelection.select(settings, Map.of("sky_archipelago", "1.4"), client).enabled(Feature.DYNAMIC_WATERS));
            assertFalse(FeatureSelection.select(only(Feature.DYNAMIC_WATERS), mods, client).enabled(Feature.DYNAMIC_WATERS));
            assertFalse(FeatureSelection.select(settings, Map.of("dynamicwaters", "13.1.0"), client).enabled(Feature.DYNAMIC_WATERS));
            assertThrows(IllegalStateException.class, () -> FeatureSelection.select(settings,
                    Map.of("sky_archipelago", "1.4", "dynamicwaters", "future"), client));
            assertDoesNotThrow(() -> FeatureSelection.select(only(Feature.TERRAIN),
                    Map.of("sky_archipelago", "1.4", "dynamicwaters", "future"), client));
        }
    }
    @Test void nativeSkyFeaturesAreClientOnlyAndIndependentOfIris() {
        var settings = only(Feature.TERRAIN, Feature.ISLAND_CLOUDS, Feature.ISLAND_VOID);
        var mods = Map.of("sky_archipelago", "1.4");
        assertTrue(FeatureSelection.select(settings, mods, true).enabled(Feature.ISLAND_VOID));
        assertTrue(FeatureSelection.select(settings, mods, true).enabled(Feature.ISLAND_CLOUDS));
        var server = FeatureSelection.select(settings, mods, false);
        assertFalse(server.enabled(Feature.ISLAND_VOID)); assertFalse(server.enabled(Feature.ISLAND_CLOUDS));
        assertTrue(server.mixins(false).stream().noneMatch(name -> name.startsWith("client.")));
    }
    @Test void shipCurrentsRequireTheirFullOptionalCombination() {
        var settings = only(Feature.TERRAIN, Feature.DYNAMIC_WATERS, Feature.DYNAMIC_WATERS_SABLE);
        var mods = Map.of("sky_archipelago", "1.4", "dynamicwaters", "13.1.0", "sable", "2.0.6");
        for (boolean client : new boolean[]{false, true}) {
            assertTrue(FeatureSelection.select(settings, mods, client).enabled(Feature.DYNAMIC_WATERS_SABLE));
            for (String missing : mods.keySet()) {
                var partial = new HashMap<>(mods); partial.remove(missing);
                assertFalse(FeatureSelection.select(settings, partial, client).mixins(client).contains("water.WaterSableMixin"));
            }
            assertFalse(FeatureSelection.select(only(Feature.TERRAIN, Feature.DYNAMIC_WATERS_SABLE), mods, client)
                    .enabled(Feature.DYNAMIC_WATERS_SABLE));
        }
    }
}
