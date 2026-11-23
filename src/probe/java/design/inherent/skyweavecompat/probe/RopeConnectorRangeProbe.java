package design.inherent.skyweavecompat.probe;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.simulated_team.simulated.service.SimConfigService;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.sprocketgames.create_aeronautics_throwable_rope_connector.config.ModCommonConfig;

/** Native COMMON-loading and loaded-server readers in a marked, disposable dedicated fixture. */
final class RopeConnectorRangeProbe {
    private static final String MOD = "create_aeronautics_throwable_rope_connector";
    private final JsonObject report = new JsonObject();
    private final boolean repaired = System.getProperty("neighbors.probe.connector").equals("repaired");
    private JsonObject loading;
    private Throwable loadingFailure;
    private boolean completed;

    RopeConnectorRangeProbe() {
        if (!Files.isRegularFile(Path.of("connector-fixture.json")))
            throw new IllegalStateException("Missing owned connector fixture marker");
        try {
            var container = ModList.get().getModContainerById(MOD).orElseThrow();
            var bus = (IEventBus) container.getClass().getMethod("getEventBus").invoke(container);
            bus.addListener(EventPriority.LOWEST, (ModConfigEvent.Loading event) -> {
                if (event.getConfig().getSpec() != ModCommonConfig.SPEC) return;
                try {
                    loading = observe();
                    check(!SimConfigService.INSTANCE.serverLoaded(), "Loading observer missed the unavailable server-config phase");
                    check(readers().equals(repaired ? List.of(16d, 64d, 128d) : List.of(16d, 40d, 40d)), "Unexpected early native readers");
                } catch (Throwable failure) { loadingFailure = failure; }
            });
        } catch (ReflectiveOperationException failure) { throw new IllegalStateException(failure); }
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post event) -> {
            if (completed) return;
            completed = true;
            try {
                report.addProperty("mode", repaired ? "repaired" : "original");
                report.addProperty("scope", "Native config events and range readers; not projectile flight, remote client sync or full-pack qualification");
                report.add("commonLoading", loading);
                if (loadingFailure != null) throw new AssertionError("COMMON loading observation failed", loadingFailure);
                check(loading != null, "Actual COMMON loading event was not observed");
                check(SimConfigService.INSTANCE.serverLoaded(), "Real server config is not loaded");
                double ceiling = JsonParser.parseString(Files.readString(Path.of("connector-fixture.json")))
                        .getAsJsonObject().get("ceiling").getAsDouble();
                check(ModCommonConfig.getSimulatedMaxRopeRange() == ceiling, "Native server ceiling differs");
                var authored = repaired ? List.of(16d, 64d, 128d) : List.of(16d, 40d, 40d);
                var expected = authored.stream().map(value -> repaired ? Math.min(value, ceiling) : value).toList();
                check(configured().equals(authored), "Configured range was destructively rewritten");
                check(readers().equals(expected), "Loaded server range enforcement differs");
                report.add("loadedServer", observe());
                var before = configured();
                check(!ModCommonConfig.correctRangeValuesToSimulatedMax(), "Native correction changed authored ranges");
                check(configured().equals(before) && readers().equals(expected), "Correction call lost configured/effective separation");
                report.addProperty("passed", true);
            } catch (Throwable failure) {
                report.addProperty("passed", false); report.addProperty("error", failure.toString()); failure.printStackTrace();
            }
            try { Files.writeString(Path.of("connector-proof.json"), report.toString()); }
            catch (Exception failure) { throw new IllegalStateException(failure); }
        });
    }

    private static void check(boolean value, String reason) { if (!value) throw new AssertionError(reason); }
    private static List<Double> configured() {
        return List.of(ModCommonConfig.MAX_THROW_DISTANCE.get(), ModCommonConfig.LAUNCHER_MAX_DISTANCE.get(),
                ModCommonConfig.MOUNTED_LAUNCHER_RANGE.get());
    }
    private static List<Double> readers() {
        return List.of(ModCommonConfig.getClampedThrowDistance(), ModCommonConfig.getClampedLauncherMaxDistance(),
                ModCommonConfig.getClampedMountedLauncherRange());
    }
    private static JsonArray values(List<Double> values) {
        var result = new JsonArray(); values.forEach(result::add); return result;
    }
    private static JsonObject observe() {
        var result = new JsonObject();
        result.addProperty("serverLoaded", SimConfigService.INSTANCE.serverLoaded());
        result.addProperty("nativeCeilingReader", ModCommonConfig.getSimulatedMaxRopeRange());
        result.add("configured", values(configured())); result.add("effective", values(readers())); return result;
    }
}
