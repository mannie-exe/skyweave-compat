package design.inherent.skyweavecompat.clientprobe;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import design.inherent.skyweavecompat.Feature;
import design.inherent.skyweavecompat.OptionalIntegrations;
import java.nio.file.Files;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;

/** Title-only optional-classloading control; usable without Sable, Iris or any other content mod. */
final class ClientRenderingStartupProbe {
    private int ticks;
    private boolean finished;
    ClientRenderingStartupProbe() {
        if (!Files.isRegularFile(FMLPaths.GAMEDIR.get().resolve("rendering-review-fixture.json"))) throw new IllegalStateException("Missing startup marker");
        NeoForge.EVENT_BUS.addListener(this::tick);
    }
    private void tick(ClientTickEvent.Post event) {
        var mc = Minecraft.getInstance(); if (finished || mc.getOverlay() != null || !(mc.screen instanceof TitleScreen) || ++ticks < 40) return;
        finished = true; var report = new JsonObject();
        try {
            for (var feature : new Feature[]{Feature.HAND_EFFECTS, Feature.HELD_MATERIALS, Feature.MODEL_ROTATIONS,
                    Feature.ITEM_DEFINITIONS, Feature.MOVES_TOUCH_MOTION, Feature.CONSTRUCT_COVERAGE}) {
                boolean active = OptionalIntegrations.enabled(feature); report.addProperty(feature.name(), active);
                if (active) throw new AssertionError("Unexpected optional target activation: " + feature);
            }
            var mods = new JsonObject();
            net.neoforged.fml.ModList.get().getMods().forEach(mod -> mods.addProperty(mod.getModId(), mod.getVersion().toString()));
            report.add("mods", mods); report.addProperty("glError", org.lwjgl.opengl.GL11C.glGetError()); report.addProperty("complete", true);
            Screenshot.grab(mc.gameDirectory, "rendering-startup.png", mc.getMainRenderTarget(), ignored -> {});
        } catch (Throwable failure) { report.addProperty("complete", false); report.addProperty("failure", failure.toString()); }
        try { ProbeStateFiles.write(FMLPaths.GAMEDIR.get().resolve("rendering-startup-results.json"), new GsonBuilder().setPrettyPrinting().create().toJson(report)); }
        catch (Exception failure) { throw new IllegalStateException(failure); }
        mc.stop();
    }
}
