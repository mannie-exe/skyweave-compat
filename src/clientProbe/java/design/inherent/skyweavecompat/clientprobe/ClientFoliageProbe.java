package design.inherent.skyweavecompat.clientprobe;

import com.google.gson.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.*;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import static org.lwjgl.opengl.GL11C.glGetError;
import static org.lwjgl.opengl.GL20C.*;

/** Actual GPU foliage draws and Veil recompilation, including an explicit unpatched control. */
public final class ClientFoliageProbe {
    private static int draws;
    private final Path root = FMLPaths.GAMEDIR.get().toAbsolutePath().normalize();
    private final boolean repaired = Boolean.getBoolean("skyweave_compat.probe.foliage.repaired");
    private final JsonObject report = new JsonObject();
    private final JsonArray passes = new JsonArray();
    private boolean opened, initialized, finished;
    private volatile boolean placed;
    private volatile Throwable serverFailure;
    private int ticks, wait, phase, beforeProgram, beforeDraws;
    private ShaderInstance shader;
    private CompletableFuture<?> pending;

    ClientFoliageProbe() {
        if (!Files.isRegularFile(root.resolve("rendering-review-fixture.json")))
            throw new IllegalStateException("Missing owned foliage fixture marker");
        report.addProperty("expectedRepair", repaired); report.add("passes", passes);
        NeoForge.EVENT_BUS.addListener(this::tick);
        NeoForge.EVENT_BUS.addListener(this::serverTick);
    }

    public static void drawn() { draws++; }

    private static Object field(Class<?> type, String name) throws Exception {
        var field = type.getDeclaredField(name); field.setAccessible(true); return field.get(null);
    }

    private void tick(ClientTickEvent.Post event) {
        if (finished) return;
        var mc = Minecraft.getInstance();
        try {
            if (++ticks > 6000) throw new AssertionError("Foliage fixture tick budget exhausted");
            if (serverFailure != null) throw new AssertionError("Foliage scene failed", serverFailure);
            if (!opened && mc.screen instanceof TitleScreen && mc.getOverlay() == null) {
                opened = true; mc.options.pauseOnLostFocus = false;
                mc.options.renderDistance().set(4); mc.options.simulationDistance().set(4);
                mc.createWorldOpenFlows().createFreshLevel("foliage-veil-review",
                        new LevelSettings("Foliage / Veil review", GameType.CREATIVE, false, Difficulty.PEACEFUL,
                                true, new GameRules(), WorldDataConfiguration.DEFAULT), new WorldOptions(42, false, false),
                        registries -> registries.registryOrThrow(Registries.WORLD_PRESET)
                                .get(ResourceLocation.withDefaultNamespace("flat")).createWorldDimensions(), mc.screen);
            }
            if (mc.screen != null && mc.screen.getClass().getName().equals("punchymoves.client.tutorial.TutorialScreen")) {
                Class.forName("punchymoves.client.tutorial.TutorialManager").getMethod("dismiss").invoke(null);
                mc.setScreen(null); wait = 0; return;
            }
            if (!placed || mc.player == null || mc.level == null || mc.getOverlay() != null || mc.screen != null) return;
            if (!initialized) {
                initialized = true; ClientHandEffectsProbe.shaders("off");
                mc.player.setPos(0.5, 101, 8); mc.player.setYRot(180); mc.player.setXRot(10);
                wait = 0; return;
            }
            if (pending != null) {
                if (!pending.isDone()) return;
                pending.join(); pending = null; wait = 0;
            }
            if (++wait < 40) return;
            var gpu = Class.forName("net.karto.mc2.mc2_interactivefoliage.gpu.GpuFoliageRenderer");
            if (!(boolean) field(gpu, "active") || draws <= beforeDraws) return;
            if (phase % 2 == 0) {
                shader = (ShaderInstance) field(gpu, "legacyShader");
                if (shader == null) return;
                var resource = mc.getResourceManager().getResource(ResourceLocation.fromNamespaceAndPath(
                        "mc2_interactivefoliage", "shaders/core/foliage_legacy.vsh")).orElseThrow();
                byte[] source;
                try (var input = resource.open()) { source = input.readAllBytes(); }
                boolean patchedSource = new String(source, java.nio.charset.StandardCharsets.UTF_8).contains("skyweaveWindDirection()");
                if (patchedSource != repaired) throw new AssertionError("Unexpected winning foliage shader");
                report.addProperty("shaderSource", resource.sourcePackId());
                report.addProperty("shaderSha256", HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(source)));
                beforeProgram = shader.getId(); beforeDraws = draws;
                var renderer = Class.forName("foundry.veil.api.client.render.VeilRenderSystem").getMethod("renderer").invoke(null);
                var compiler = renderer.getClass().getMethod("getVanillaShaderCompiler").invoke(renderer);
                pending = (CompletableFuture<?>) compiler.getClass().getMethod("reload", Collection.class).invoke(compiler, List.of(shader));
                phase++; wait = 0; return;
            }
            var extension = Class.forName("foundry.veil.ext.ShaderInstanceExtension");
            extension.getMethod("veil$applyCompile").invoke(shader);
            boolean changed = beforeProgram != shader.getId();
            var pass = new JsonObject(); pass.addProperty("phase", phase); pass.addProperty("draws", draws - beforeDraws);
            pass.addProperty("shader", shader.getName()); pass.addProperty("beforeProgram", beforeProgram);
            pass.addProperty("afterProgram", shader.getId()); pass.addProperty("newCompiledProgram", changed);
            pass.addProperty("linked", glGetProgrami(shader.getId(), GL_LINK_STATUS) == GL_TRUE);
            pass.addProperty("activeBuffers", (int) extension.getMethod("veil$getActiveBuffers").invoke(shader));
            pass.addProperty("glError", glGetError()); passes.add(pass);
            if (changed != repaired || !pass.get("linked").getAsBoolean() || pass.get("glError").getAsInt() != 0)
                throw new AssertionError("Unexpected Veil recompilation result");
            Screenshot.grab(mc.gameDirectory, "foliage-veil-" + phase + ".png", mc.getMainRenderTarget(), ignored -> {});
            if (!repaired || phase == 3) { finish(mc, null); return; }
            phase++; beforeDraws = draws; pending = mc.reloadResourcePacks(); wait = 0;
        } catch (Throwable problem) { finish(mc, problem); }
    }

    private void serverTick(ServerTickEvent.Post event) {
        if (placed || event.getServer().getPlayerList().getPlayers().isEmpty()) return;
        try {
            var server = event.getServer(); var level = server.overworld();
            level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, server);
            level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, server); level.setDayTime(6000);
            for (int x = -6; x <= 6; x++) for (int z = -2; z <= 9; z++) {
                level.setBlock(new BlockPos(x, 100, z), Blocks.GRASS_BLOCK.defaultBlockState(), 3);
                if (z < 6 && Math.floorMod(x + z, 3) != 0)
                    level.setBlock(new BlockPos(x, 101, z), (Math.floorMod(x, 2) == 0 ? Blocks.SHORT_GRASS : Blocks.POPPY).defaultBlockState(), 3);
            }
            var player = server.getPlayerList().getPlayers().getFirst();
            player.teleportTo(0.5, 101, 8); player.setYRot(180); player.setXRot(10); placed = true;
        } catch (Throwable problem) { serverFailure = problem; }
    }

    private void finish(Minecraft mc, Throwable failure) {
        if (finished) return; finished = true;
        report.addProperty("complete", failure == null); if (failure != null) report.addProperty("failure", failure.toString());
        report.addProperty("totalGpuDraws", draws);
        try { ProbeStateFiles.write(root.resolve("foliage-veil-results.json"), new GsonBuilder().setPrettyPrinting().create().toJson(report)); }
        catch (Exception problem) { throw new IllegalStateException(problem); }
        mc.stop();
    }
}
