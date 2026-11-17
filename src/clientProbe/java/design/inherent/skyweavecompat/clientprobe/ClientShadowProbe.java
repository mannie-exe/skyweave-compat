package design.inherent.skyweavecompat.clientprobe;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.seibel.distanthorizons.api.DhApi;
import com.seibel.distanthorizons.api.methods.events.DhApiEventRegister;
import com.seibel.distanthorizons.api.methods.events.abstractEvents.DhApiBeforeBufferRenderEvent;
import com.seibel.distanthorizons.api.methods.events.sharedParameterObjects.DhApiEventParam;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import net.irisshaders.iris.Iris;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;

/** Bounded cavity/roof/water witnesses and matched source-chain timing; never launches Photon. */
final class ClientShadowProbe {
    private static final String WORLD = "rendering-shadow-review";
    private static final List<String> STAGES = List.of("baseline-inside", "corrected-inside", "corrected-opening",
            "corrected-outside", "corrected-water", "corrected-low-sun", "corrected-rain", "corrected-distance512",
            "corrected-distance768", "corrected-resolution1024", "corrected-dh-off");
    private final Path root = FMLPaths.GAMEDIR.get();
    private final JsonObject report = new JsonObject();
    private final JsonArray scenes = new JsonArray();
    private final ClientFrameTiming timing = new ClientFrameTiming();
    private final String corrected = System.getProperty("skyweave_compat.probe.shadows.shader");
    private final String baseline = System.getProperty("skyweave_compat.probe.shadows.baseline");
    private final List<String> stages = Boolean.getBoolean("skyweave_compat.probe.shadows.quick") ? STAGES.subList(0, 3) : STAGES;
    private CompletableFuture<?> pending;
    private boolean opened, prepared, stopped;
    private int tick, stage = -1, stageTick;
    private long buffers, shadowBuffers;
    private JsonObject shadowContext;
    private float yaw = -90, pitch;

    ClientShadowProbe() {
        if (!Files.isRegularFile(root.resolve("rendering-review-fixture.json"))) throw new IllegalStateException("Missing disposable rendering marker");
        for (String shader : List.of(corrected, baseline)) if (!(shader.startsWith("Complementary ") || shader.startsWith("BSL_")))
            throw new IllegalArgumentException("Supported native shader chain required");
        report.add("scenes", scenes); report.addProperty("corrected", corrected); report.addProperty("baseline", baseline);
        report.addProperty("scope", "Authored controlled cavity and roof geometry; native full-pack GPU draw/timing, not natural island frequency or general performance");
        DhApiEventRegister.on(DhApiBeforeBufferRenderEvent.class, new DhApiBeforeBufferRenderEvent() {
            @Override public void beforeRender(DhApiEventParam<EventParam> event) {
                buffers++;
                if (net.irisshaders.iris.shadows.ShadowRenderer.ACTIVE) {
                    shadowBuffers++;
                    if (stage == 1 && shadowContext == null) shadowContext = shadowContext();
                }
            }
        });
        NeoForge.EVENT_BUS.addListener(this::tick);
    }

    private void tick(ClientTickEvent.Post event) {
        var mc = Minecraft.getInstance();
        if (stopped || mc.getOverlay() != null) return;
        try {
            if (!opened && mc.screen instanceof TitleScreen) {
                opened = true; mc.options.pauseOnLostFocus = false; mc.options.renderDistance().set(4); mc.options.simulationDistance().set(4);
                mc.options.setCameraType(CameraType.FIRST_PERSON); mc.options.enableVsync().set(false); mc.options.framerateLimit().set(260);
                if (Files.exists(root.resolve("saves/" + WORLD))) throw new IllegalStateException("Expected fresh owned shadow world");
                mc.createWorldOpenFlows().createFreshLevel(WORLD,
                        new LevelSettings("Shadow review", GameType.CREATIVE, false, Difficulty.PEACEFUL, true, new GameRules(), WorldDataConfiguration.DEFAULT),
                        new WorldOptions(42, false, false), registries -> registries.registryOrThrow(Registries.WORLD_PRESET)
                                .get(ResourceLocation.withDefaultNamespace("flat")).createWorldDimensions(), mc.screen); return;
            }
            if (mc.player == null || mc.level == null || mc.getSingleplayerServer() == null) return;
            if (pending != null) { if (!pending.isDone()) return; pending.join(); pending = null; }
            mc.setScreen(null); mc.player.setYRot(yaw); mc.player.yRotO = yaw; mc.player.setXRot(pitch); mc.player.xRotO = pitch;
            if (!prepared) {
                report.addProperty("gpu", org.lwjgl.opengl.GL11C.glGetString(org.lwjgl.opengl.GL11C.GL_RENDERER));
                prepared = true; Files.writeString(root.resolve("saves/" + WORLD + "/.rendering-review-world"), "owned shadow fixture\n");
                if (Iris.getIrisConfig().shouldAllowUnknownShaders()) throw new AssertionError("Unknown shaders must remain restricted");
                if (DhApi.Delayed.configs != null) {
                    DhApi.Delayed.configs.worldGenerator().enableDistantWorldGeneration().setValue(false);
                    DhApi.Delayed.configs.graphics().renderingEnabled().setValue(true);
                    DhApi.Delayed.configs.graphics().renderingMode().setValue(com.seibel.distanthorizons.api.enums.rendering.EDhApiRendererMode.DEFAULT);
                }
                select(baseline);
                server(player -> {
                    var level = player.serverLevel(); player.setGameMode(GameType.SPECTATOR);
                    level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, player.getServer()); level.setDayTime(4500);
                    for (int x = -16; x <= 384; x++) for (int z = -48; z <= 48; z++) {
                        level.setBlock(new BlockPos(x, 80, z), Blocks.STONE.defaultBlockState(), 2);
                        if (!(x >= 24 && x < 40 && z >= -8 && z < 8))
                            level.setBlock(new BlockPos(x, 130, z), Blocks.STONE.defaultBlockState(), 2);
                    }
                    for (int x = -16; x <= 384; x++) for (int y = 81; y < 130; y++) for (int z : new int[]{-48, 48})
                        level.setBlock(new BlockPos(x, y, z), Blocks.DEEPSLATE.defaultBlockState(), 2);
                    for (int x = 64; x <= 96; x++) for (int z = -16; z <= 16; z++)
                        level.setBlock(new BlockPos(x, 81, z), Blocks.WATER.defaultBlockState(), 2);
                    for (int y = 81; y < 130; y++) for (int z = -48; z <= 48; z++)
                        level.setBlock(new BlockPos(192, y, z), Blocks.STONE.defaultBlockState(), 2);
                    player.teleportTo(.5, 102, .5);
                }); return;
            }
            tick++;
            if (tick == 30 || tick == 90 || tick == 150 || tick == 210 || tick == 270 || tick == 330) {
                int x = (tick - 30) / 60 * 64 + 32; server(player -> player.teleportTo(x + .5, 102, .5));
            }
            if (tick == 390) server(player -> player.teleportTo(.5, 102, .5));
            if (stage < 0 && tick >= 510) { stage = 0; prepareStage(); }
            if (stage < 0) return;
            stageTick++;
            if (stageTick == 100) { buffers = shadowBuffers = 0; timing.begin(STAGES.get(stage)); }
            if (stageTick == 260) {
                var row = timing.finish(); row.addProperty("dhBuffers", buffers); row.addProperty("dhShadowBuffers", shadowBuffers);
                var camera = mc.gameRenderer.getMainCamera(); row.addProperty("cameraPosition", camera.getPosition().toString());
                row.addProperty("cameraYaw", camera.getYRot()); row.addProperty("cameraPitch", camera.getXRot());
                row.addProperty("nativeFovSetting", mc.options.fov().get()); row.addProperty("nativeRenderDistance", mc.options.getEffectiveRenderDistance());
                row.addProperty("glError", org.lwjgl.opengl.GL11C.glGetError()); row.addProperty("shaderActive", Iris.isPackInUseQuick());
                var pipeline = Iris.getPipelineManager().getPipeline().orElse(null);
                row.addProperty("pipeline", pipeline == null ? "absent" : pipeline.getClass().getName());
                row.addProperty("shadowResolution", net.irisshaders.iris.shadows.ShadowRenderer.RESOLUTION);
                row.addProperty("shadowRenderDistance", net.irisshaders.iris.shadows.ShadowRenderer.renderDistance);
                if (stage == 1 && shadowContext != null) row.add("shadowContext", shadowContext);
                row.addProperty("position", mc.player.position().toString()); row.addProperty("yaw", yaw); row.addProperty("pitch", pitch);
                scenes.add(row); save();
                Screenshot.grab(mc.gameDirectory, "shadow-" + (corrected.startsWith("BSL_") ? "bsl-" : "complementary-") + STAGES.get(stage) + ".png",
                        mc.getMainRenderTarget(), ignored -> {});
                if (!(pipeline instanceof net.irisshaders.iris.pipeline.IrisRenderingPipeline) || !Iris.isPackInUseQuick()
                        || row.get("glError").getAsInt() != 0) throw new AssertionError("Native shader draw failed");
                if (stage == 1 && shadowBuffers == 0) throw new AssertionError("No actual DH shadow buffer submissions");
            }
            if (stageTick == 280) {
                if (++stage == stages.size()) { report.addProperty("complete", true); stop(); }
                else prepareStage();
            }
        } catch (Throwable failure) {
            report.addProperty("complete", false); report.addProperty("failure", failure.toString()); failure.printStackTrace(); stop();
        }
    }
    private void prepareStage() throws Exception {
        stageTick = 0; yaw = -90; pitch = 0;
        String name = STAGES.get(stage);
        if (stage == 1) select(corrected);
        if (stage >= 7 && stage <= 9) option(stage == 9 ? "shadowMapResolution" : "shadowDistance", stage == 7 ? "512.0" : stage == 8 ? "768.0" : "1024");
        if (stage == 10) DhApi.Delayed.configs.graphics().renderingEnabled().setValue(false);
        if (stage == 2) { pitch = -45; server(player -> player.teleportTo(32.5, 101, .5)); }
        else if (stage == 3) { pitch = 20; server(player -> player.teleportTo(64.5, 152, -72.5)); }
        else if (stage == 4) { pitch = 35; server(player -> player.teleportTo(80.5, 85, .5)); }
        else server(player -> { player.teleportTo(.5, 102, .5); player.serverLevel().setDayTime(stage == 5 ? 1000 : 4500);
            player.serverLevel().setWeatherParameters(0, stage == 6 ? 6000 : 0, stage == 6, false); });
    }
    private void select(String shader) throws Exception { var config = Iris.getIrisConfig(); config.setShaderPackName(shader); config.setShadersEnabled(true); config.save(); Iris.reload(); }
    private static JsonObject shadowContext() {
        var row = new JsonObject();
        var inverse = new org.joml.Matrix4f(net.irisshaders.iris.shadows.ShadowRenderer.MODELVIEW).invert();
        var light = inverse.transformDirection(new org.joml.Vector3f(0, 0, 1)).normalize();
        if (light.y < 0) light.negate();
        row.addProperty("sunDirection", light.toString());
        if (light.y > .001) {
            row.addProperty("clearRoofRayFloorX", 32 - 49 * light.x / light.y);
            row.addProperty("clearRoofRayFloorZ", -49 * light.z / light.y);
        }
        int program = org.lwjgl.opengl.GL11C.glGetInteger(org.lwjgl.opengl.GL20C.GL_CURRENT_PROGRAM);
        row.addProperty("program", program);
        for (String name : List.of("iris_ModelViewMatrix", "iris_ProjectionMatrix", "shadowModelView", "shadowModelViewInverse", "shadowProjection", "modelScale", "modelOffset", "far")) {
            int location = org.lwjgl.opengl.GL20C.glGetUniformLocation(program, name);
            if (location < 0) continue;
            var values = new float[name.contains("Matrix") || name.contains("Projection") || name.contains("ModelView") ? 16 : name.equals("far") ? 1 : 3];
            org.lwjgl.opengl.GL20C.glGetUniformfv(program, location, values); var array = new JsonArray();
            for (float value : values) array.add(value); row.add(name, array);
        }
        return row;
    }
    private void option(String key, String value) throws Exception {
        var path = root.resolve("shaderpacks/" + corrected + ".txt");
        String text = Files.readString(path).replaceAll("(?m)^" + key + "=[^\\r\\n]*(?:\\r?\\n)?", "");
        Files.writeString(path, text + "\n" + key + "=" + value + "\n"); Iris.reload();
    }
    private void server(java.util.function.Consumer<ServerPlayer> action) {
        var mc = Minecraft.getInstance(); var server = mc.getSingleplayerServer(); var id = mc.player.getUUID();
        pending = server.submit(() -> action.accept(server.getPlayerList().getPlayer(id)));
    }
    private void save() throws Exception { ProbeStateFiles.write(root.resolve("shadow-results.json"), new GsonBuilder().setPrettyPrinting().create().toJson(report)); }
    private void stop() { stopped = true; timing.close(); try { save(); } catch (Exception failure) { throw new IllegalStateException(failure); } Minecraft.getInstance().stop(); }
}
