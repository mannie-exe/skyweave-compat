package design.inherent.skyweavecompat.clientprobe;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.physics.config.dimension_physics.DimensionPhysicsData;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.joml.Vector3d;
import org.slf4j.LoggerFactory;

/** Client telemetry and opt-in disposable fixtures. Never included in the released mod. */
@Mod(value = "skyweave_client_probe", dist = Dist.CLIENT)
public final class ClientFlightProbe {
    private int ticks;
    public ClientFlightProbe() {
        if (Boolean.getBoolean("skyweave_compat.probe.misc")) { new ClientMiscProbe(); return; }
        if (Boolean.getBoolean("skyweave_compat.probe.resources")) {
            new ClientResourcesProbe();
            if (Boolean.getBoolean("skyweave_compat.probe.colorwheel")) new ClientColorwheelProbe();
            if (Boolean.getBoolean("skyweave_compat.probe.configuration")) new ClientConfigurationProbe();
            return;
        }
        if (Boolean.getBoolean("skyweave_compat.probe.redstone")) {
            new ClientGuidanceProbe();
            new ClientConfigurationProbe();
            new ClientRedstoneProbe();
            return;
        }
        if (Boolean.getBoolean("skyweave_compat.probe.configuration")) {
            new ClientConfigurationProbe();
            return;
        }
        if (Boolean.getBoolean("skyweave_compat.probe.recovery")) {
            new ClientRecoveryProbe();
            return;
        }
        if (Boolean.getBoolean("skyweave_compat.probe.audio")) {
            new ClientAudioProbe();
            return;
        }
        if (Boolean.getBoolean("skyweave_compat.probe.guidance")) new ClientGuidanceProbe();
        if (Boolean.getBoolean("skyweave_compat.probe.colorwheel")) new ClientColorwheelProbe();
        if (Boolean.getBoolean("skyweave_compat.probe.linkage")) {
            new ClientLinkageProbe();
            return;
        }
        if (Boolean.getBoolean("skyweave_compat.probe.linear") || Boolean.getBoolean("skyweave_compat.probe.rendering") || Boolean.getBoolean("skyweave_compat.probe.journal")
                || Boolean.getBoolean("skyweave_compat.probe.lighting")) {
            NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.client.event.RegisterClientCommandsEvent event) ->
                event.getDispatcher().register(net.minecraft.commands.Commands.literal("compatprobe_stop").executes(context -> {
                    var server = Minecraft.getInstance().getSingleplayerServer();
                    if (server == null) throw new IllegalStateException("Expected a disposable integrated server");
                    server.execute(() -> server.halt(false));
                    return 1;
                })));
        }
        if (Boolean.getBoolean("skyweave_compat.probe.linear")) {
            new ClientLinearProbe();
            return;
        }
        if (Boolean.getBoolean("skyweave_compat.probe.lighting")) {
            new ClientLightingProbe();
            return;
        }
        if (Boolean.getBoolean("skyweave_compat.probe.journal")) {
            new ClientJournalProbe();
            return;
        }
        if (Boolean.getBoolean("skyweave_compat.probe.compatibility")) {
            new ClientCompatibilityProbe();
            return;
        }
        if (Boolean.getBoolean("skyweave_compat.probe.dhCache")) new ClientDhCacheProbe();
        if (Boolean.getBoolean("skyweave_compat.probe.dhFluid")) new ClientDhFluidProbe();
        else if (!Boolean.getBoolean("skyweave_compat.probe.dhCache")) NeoForge.EVENT_BUS.addListener(this::tick);
    }
    private void tick(ClientTickEvent.Post event) {
        var client = Minecraft.getInstance();
        if (client.level == null || client.player == null || ++ticks % 100 != 0) return;
        var level = client.level;
        var row = new JsonObject();
        row.addProperty("dimension", level.dimension().location().toString());
        row.addProperty("physics", DimensionPhysicsData.of(level).dimension().toString());
        row.addProperty("minY", level.getMinBuildHeight()); row.addProperty("maxY", level.getMaxBuildHeight());
        row.addProperty("playerY", client.player.getY());
        row.addProperty("rain", level.getRainLevel(1));
        var pressure = new JsonObject();
        for (int y : new int[]{-128, 180, 400, 508, 600, 680, 728, 768})
            pressure.addProperty(Integer.toString(y), DimensionPhysicsData.getAirPressure(level, new Vector3d(0, y, 0)));
        row.add("pressure", pressure);
        var ships = new JsonArray();
        for (Object ship : SubLevelContainer.getContainer(level).getAllSubLevels()) {
            // Companion interfaces are nested upstream runtime artifacts, not compile dependencies.
            try {
                var item = new JsonObject();
                item.addProperty("id", ship.getClass().getMethod("getUniqueId").invoke(ship).toString());
                Object bounds = ship.getClass().getMethod("boundingBox").invoke(ship);
                item.addProperty("minY", (Number)bounds.getClass().getMethod("minY").invoke(bounds));
                item.addProperty("maxY", (Number)bounds.getClass().getMethod("maxY").invoke(bounds));
                ships.add(item);
            } catch (ReflectiveOperationException error) { throw new IllegalStateException("Flight fixture API changed", error); }
        }
        row.add("ships", ships);
        if (Boolean.getBoolean("skyweave_compat.probe.rendering")) {
            try {
                var mapType = Class.forName("dev.eriksonn.aeronautics.content.blocks.hot_air.balloon.map.BalloonMap");
                Object attached = mapType.getField("MAP").get(null);
                Object map = attached.getClass().getMethod("get", net.minecraft.world.level.LevelAccessor.class).invoke(attached, level);
                var balloons = new JsonArray();
                for (Object balloon : (Iterable<?>)mapType.getMethod("getBalloons").invoke(map)) {
                    var item = new JsonObject();
                    var type = balloon.getClass();
                    item.addProperty("valid", (Boolean)type.getMethod("isValid").invoke(balloon));
                    item.addProperty("capacity", (Number)type.getMethod("getCapacity").invoke(balloon));
                    item.addProperty("renderRegion", type.getMethod("getRenderRegion").invoke(balloon) != null);
                    var volumes = new JsonArray();
                    for (Object heater : (Iterable<?>)type.getMethod("getHeaters").invoke(balloon))
                        volumes.add((Number)heater.getClass().getMethod("getClientPredictedVolume").invoke(heater));
                    item.add("predictedVolumes", volumes);
                    balloons.add(item);
                }
                row.add("balloons", balloons);
            } catch (ReflectiveOperationException error) {
                throw new IllegalStateException("Rendering fixture API changed", error);
            }
        }
        LoggerFactory.getLogger(ClientFlightProbe.class).info("CLIENT_FLIGHT_PROBE {}", row);
    }
}
