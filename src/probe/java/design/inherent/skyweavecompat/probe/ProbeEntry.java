package design.inherent.skyweavecompat.probe;

import java.nio.file.Path;
import net.minecraft.server.MinecraftServer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Minimum fixtures must bootstrap without resolving optional terrain method signatures. */
@Mod("skyweave_compat_probe")
public final class ProbeEntry {
    private MinecraftServer pending;
    public ProbeEntry() {
        SideLoadingProbe.install();
        if (Boolean.getBoolean("neighbors.probe.settingsPolicy")) { new SettingsPolicyProbe(); return; }
        if (Boolean.getBoolean("neighbors.probe.inspectSableDiscovery"))
            NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.server.ServerStoppingEvent event)->SideLoadingProbe.inspectPhysics(event.getServer()));
        if (!Boolean.getBoolean("neighbors.probe.wayfinder")) { new PlacementProbe(); return; }
        NeoForge.EVENT_BUS.addListener((ServerStartedEvent event) -> pending = event.getServer());
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post event) -> {
            if (pending != null) {
                var server = pending; pending = null;
                try { WayfinderProbe.start(server,Path.of(System.getProperty("neighbors.probe.output"))); }
                catch (Throwable failure) {
                    var report = new com.google.gson.JsonObject(); report.addProperty("complete",false);
                    report.addProperty("error","Wayfinder fixture bootstrap: "+failure);
                    try { java.nio.file.Files.writeString(Path.of(System.getProperty("neighbors.probe.output")),report.toString()); }
                    catch (java.io.IOException saveFailure) { throw new java.io.UncheckedIOException(saveFailure); }
                    failure.printStackTrace(); server.halt(false);
                }
            } else {
                WayfinderProbe.tick(event.getServer());
                if (Boolean.getBoolean("neighbors.probe.wayfinderDh")) WayfinderDhProbe.tick(event.getServer());
            }
        });
    }
}
