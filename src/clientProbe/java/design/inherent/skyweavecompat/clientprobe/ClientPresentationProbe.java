package design.inherent.skyweavecompat.clientprobe;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import design.inherent.skyweavecompat.client.RenderingPreferences;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.dimension.DimensionType;

/** Read actual registered preferences/provider from a marked, real native client. No product hooks. */
final class ClientPresentationProbe {
    static void observe(Path root, Minecraft mc) throws Exception {
        if (!Files.isRegularFile(root.resolve("environment-review-fixture.json")) || mc.level == null)
            throw new IllegalStateException("Expected a connected environment fixture");
        if (RenderingPreferences.MINIMUM_Y != DimensionType.MIN_Y || RenderingPreferences.MAXIMUM_Y != DimensionType.MAX_Y
                || RenderingPreferences.NATIVE_HEIGHT != DimensionType.Y_SIZE)
            throw new IllegalStateException("Native vertical bounds changed");
        var report = new JsonObject();
        report.addProperty("owned", design.inherent.skyweavecompat.climate.IslandClimate.applies(mc.level));
        report.addProperty("provider", mc.level.effects().getClass().getName());
        report.addProperty("cloudHeight", mc.level.effects().getCloudHeight());
        report.addProperty("cloudMode", RenderingPreferences.CLOUD_MODE.get().name());
        report.addProperty("cloudOffset", RenderingPreferences.CLOUD_OFFSET.get());
        report.addProperty("cloudAbsolute", RenderingPreferences.CLOUD_ABSOLUTE.get());
        report.addProperty("voidFog", RenderingPreferences.VOID_FOG.get());
        report.addProperty("voidPlane", RenderingPreferences.VOID_PLANE.get());
        report.addProperty("textLighting", RenderingPreferences.TEXT_LIGHTING.get());
        report.addProperty("nametagLighting", RenderingPreferences.NAMETAG_LIGHTING.get());
        report.addProperty("textShadow", RenderingPreferences.TEXT_SHADOW.get());
        report.addProperty("nametagShadow", RenderingPreferences.NAMETAG_SHADOW.get());
        // Execute the actual transformed handlers with a controlled draw continuation. This is branch coverage,
        // not a claim about a complete GL frame or an arbitrary shader's sky geometry.
        var fog = java.util.Arrays.stream(net.minecraft.client.renderer.FogRenderer.class.getDeclaredMethods())
                .filter(method -> method.getName().endsWith("baseline") && method.getParameterCount() == 2).findFirst().orElseThrow();
        fog.setAccessible(true);
        report.addProperty("nativeFogBaseline", (float) fog.invoke(null, .25f, mc.level));
        var renderer = mc.levelRenderer;
        var plane = net.minecraft.client.renderer.LevelRenderer.class.getDeclaredField("darkBuffer"); plane.setAccessible(true);
        var sky = java.util.Arrays.stream(net.minecraft.client.renderer.LevelRenderer.class.getDeclaredMethods())
                .filter(method -> method.getName().endsWith("sky") && method.getParameterCount() == 5).findFirst().orElseThrow();
        sky.setAccessible(true);
        var calls = new java.util.concurrent.atomic.AtomicInteger();
        com.llamalad7.mixinextras.injector.wrapoperation.Operation<Void> continuation = arguments -> { calls.incrementAndGet(); return null; };
        sky.invoke(renderer, plane.get(renderer), new org.joml.Matrix4f(), new org.joml.Matrix4f(), null, continuation);
        report.addProperty("nativeDarkPlaneContinuationCalls", calls.get());
        if (design.inherent.skyweavecompat.OptionalIntegrations.enabled(design.inherent.skyweavecompat.Feature.ISLAND_SHADER_ENVIRONMENT))
            report.add("irisSuppliers", ClientShaderPreferencesProbe.observe());
        ProbeStateFiles.write(root.resolve("environment-observation.json"), new GsonBuilder().setPrettyPrinting().create().toJson(report));
    }
}
