/* SPDX-License-Identifier: Apache-2.0 */
package design.inherent.skyweavecompat.clientprobe;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import design.inherent.skyweavecompat.Feature;
import design.inherent.skyweavecompat.OptionalIntegrations;
import java.nio.file.Files;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;

/** Full packaged-cohort construction control; no personal world or product setting changes. */
final class ClientMaintenanceStartupProbe {
    private int ticks;
    private boolean done;
    ClientMaintenanceStartupProbe() {
        if (!Files.isRegularFile(FMLPaths.GAMEDIR.get().resolve("maintenance-startup-fixture.json")))
            throw new IllegalStateException("Missing owned maintenance startup marker");
        NeoForge.EVENT_BUS.addListener(this::tick);
    }
    private void tick(ClientTickEvent.Post event) {
        var mc=Minecraft.getInstance();
        if(done || mc.getOverlay()!=null || !(mc.screen instanceof TitleScreen) || ++ticks<40)return;
        done=true;var report=new JsonObject();
        try {
            if(!OptionalIntegrations.enabled(Feature.C2ME_CLIENT_LIGHT))throw new AssertionError("Initial light adaptation not admitted");
            // Resolve the actual transformed class, independently of incidental Wayfinder loading.
            report.addProperty("engine",LevelLightEngine.class.getName());
            report.addProperty("clientLightDefault",true);
            var mods=new JsonObject();
            net.neoforged.fml.ModList.get().getMods().forEach(mod->mods.addProperty(mod.getModId(),mod.getVersion().toString()));
            report.add("mods",mods);report.addProperty("passed",true);
        } catch(Throwable failure){report.addProperty("passed",false);report.addProperty("error",failure.toString());}
        try {ProbeStateFiles.write(FMLPaths.GAMEDIR.get().resolve("maintenance-startup-proof.json"),new GsonBuilder().setPrettyPrinting().create().toJson(report));}
        catch(Exception failure){throw new IllegalStateException(failure);}
        mc.stop();
    }
}
