package design.inherent.skyweavecompat;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AnimationFeatureSelectionTest {
    @Test void lanternAssetsAndHookWithdrawWithEitherRequiredAdapterOrProvider() {
        var values = new EnumMap<Feature, Boolean>(Feature.class);
        for (var f : Feature.values()) values.put(f, f == Feature.LANTERN_GRIP
                || f == Feature.MOVES_OVERLAY_BONES || f == Feature.ITEM_DEFINITIONS);
        var mods = FeatureSelection.dependencies(Feature.LANTERN_GRIP);
        assertTrue(FeatureSelection.select(new FeatureSettings(values), mods, true).enabled(Feature.LANTERN_GRIP));
        assertFalse(FeatureSelection.select(new FeatureSettings(values), mods, false).enabled(Feature.LANTERN_GRIP));
        for (var dependency : new Feature[]{Feature.LANTERN_GRIP, Feature.MOVES_OVERLAY_BONES, Feature.ITEM_DEFINITIONS}) {
            var disabled = new EnumMap<>(values); disabled.put(dependency, false);
            var selected = FeatureSelection.select(new FeatureSettings(disabled), mods, true);
            assertFalse(selected.enabled(Feature.LANTERN_GRIP));
            assertFalse(selected.mixins(true).contains("client.LanternGripMixin"));
        }
        for (String provider : mods.keySet()) {
            var absent = new HashMap<>(mods); absent.remove(provider);
            assertFalse(FeatureSelection.select(new FeatureSettings(values), absent, true).enabled(Feature.LANTERN_GRIP));
        }
    }

    @Test void animationAdaptersHaveIndependentExactClientAdmissionAndOptOuts() {
        for (var feature : new Feature[]{Feature.MOVES_OVERLAY_BONES, Feature.COMBAT_ATTACK_PITCH}) {
            var values = new EnumMap<Feature, Boolean>(Feature.class);
            for (var f : Feature.values()) values.put(f, f == feature);
            var settings = new FeatureSettings(values);
            var providers = FeatureSelection.dependencies(feature);
            assertTrue(FeatureSelection.select(settings, providers, true).enabled(feature));
            assertFalse(FeatureSelection.select(settings, providers, false).enabled(feature));
            for (var provider : providers.keySet()) {
                var missing = new HashMap<>(providers); missing.remove(provider);
                assertTrue(FeatureSelection.select(settings, missing, true).mixins(true).isEmpty());
                var future = new HashMap<>(providers); future.put(provider, "future");
                assertThrows(IllegalStateException.class, () -> FeatureSelection.select(settings, future, true));
            }
            values.put(feature, false);
            assertTrue(FeatureSelection.select(new FeatureSettings(values), Map.of("punchy_moves", "future",
                    "bettercombat", "future", "playeranimator", "future"), true).mixins(true).isEmpty());
        }
    }
}
