package design.inherent.skyweavecompat;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;
import org.junit.jupiter.api.Test;
import org.spongepowered.asm.mixin.extensibility.IMixinErrorHandler.ErrorAction;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import static org.junit.jupiter.api.Assertions.*;

class DependencyDiagnosticsTest {
    @Test void composedHooksRetainEveryRequiredAdapterOwner() {
        var values = new EnumMap<Feature, Boolean>(Feature.class);
        for (var feature : Feature.values()) values.put(feature,
                List.of(Feature.TERRAIN, Feature.CLIMATE, Feature.WATER_SPAWNS, Feature.DYNAMIC_WATERS).contains(feature));
        var selected = FeatureSelection.select(new FeatureSettings(values), Map.of("sky_archipelago", "1.4", "dynamicwaters", "13.1.0"), true);
        DependencyDiagnostics.prepare(selected, true);
        var climate = DependencyDiagnostics.forMixin("design.inherent.skyweavecompat.mixin.water.FloeClimateMixin");
        assertTrue(climate.contains(Feature.CLIMATE.key));
        assertTrue(climate.contains(Feature.DYNAMIC_WATERS.key));
        assertTrue(climate.contains("dynamicwaters: developed against 13.1.0"));
        var water = DependencyDiagnostics.forMixin("design.inherent.skyweavecompat.mixin.water.FloeWaterLevelMixin");
        assertTrue(water.contains(Feature.WATER_SPAWNS.key));
        assertTrue(water.contains(Feature.DYNAMIC_WATERS.key));
        for (var mixin : selected.mixins(true))
            assertFalse(DependencyDiagnostics.forMixin("design.inherent.skyweavecompat.mixin." + mixin).contains("adapter admission unavailable"), mixin);
    }

    @Test void failedPrivateHookRetainsTheActualCauseOwnerAndVersionsWithoutDowngradingMixinFailure() {
        var values = new EnumMap<Feature, Boolean>(Feature.class);
        for (var feature : Feature.values()) values.put(feature, feature == Feature.DH_HANDOFF);
        var selected = FeatureSelection.select(new FeatureSettings(values), Map.of("distanthorizons", "3.3.3", "neoforge", "21.1.999"), true);
        DependencyDiagnostics.prepare(selected, true);
        var events = new ArrayList<LogEvent>();
        var appender = new AbstractAppender("dependency-cause-test", null, null, true, Property.EMPTY_ARRAY) {
            @Override public void append(LogEvent event) { events.add(event.toImmutable()); }
        };
        var logger = (org.apache.logging.log4j.core.Logger) LogManager.getLogger(OptionalMixinErrorHandler.class);
        appender.start();
        logger.addAppender(appender);
        try {
            var original = new NoSuchMethodError("Changed upstream wrapper descriptor");
            var handler = new OptionalMixinErrorHandler();
            var mixin = mixin("design.inherent.skyweavecompat.mixin.dh.ClientWrapperHandoffMixin");
            assertSame(ErrorAction.ERROR, handler.onApplyError("example.UpstreamWrapper", original, mixin, ErrorAction.ERROR));
            assertEquals(1, events.size());
            assertSame(original, events.getFirst().getThrown());
            var message = events.getFirst().getMessage().getFormattedMessage();
            for (var detail : List.of("example.UpstreamWrapper", Feature.DH_HANDOFF.key, "developed against 3.3.3", "installed 3.3.3",
                    "neoforge: developed against 21.1.265", "installed 21.1.999"))
                assertTrue(message.contains(detail), detail);
            assertSame(ErrorAction.WARN, handler.onPrepareError(null, original, mixin, ErrorAction.WARN));
            assertSame(ErrorAction.ERROR, handler.onApplyError("foreign.Target", original, mixin("other.mod.Mixin"), ErrorAction.ERROR));
            assertEquals(2, events.size(), "Other mods retain their own diagnostic owner");
        } finally {
            logger.removeAppender(appender);
            appender.stop();
        }
        DependencyDiagnostics.prepare(FeatureSelection.select(new FeatureSettings(Map.of(Feature.DH_HANDOFF, false)), Map.of(), true), true);
        assertFalse(DependencyDiagnostics.forMixin("design.inherent.skyweavecompat.mixin.dh.ClientWrapperHandoffMixin").contains("installed 3.3.3"));
    }

    private static IMixinInfo mixin(String name) {
        return (IMixinInfo) Proxy.newProxyInstance(IMixinInfo.class.getClassLoader(), new Class<?>[]{IMixinInfo.class},
                (proxy, method, args) -> method.getName().equals("getClassName") ? name : null);
    }
}
