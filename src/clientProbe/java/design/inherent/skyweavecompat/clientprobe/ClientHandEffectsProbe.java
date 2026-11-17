package design.inherent.skyweavecompat.clientprobe;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import design.inherent.skyweavecompat.Feature;
import design.inherent.skyweavecompat.OptionalIntegrations;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.*;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.lwjgl.system.MemoryStack;
import static org.lwjgl.opengl.GL11C.*;
import static org.lwjgl.opengl.GL20C.GL_CURRENT_PROGRAM;
import static org.lwjgl.opengl.GL30C.*;

/** Bounded real hand/lifecycle scenes. Requires its own profile marker; no installed instance access. */
public final class ClientHandEffectsProbe {
    private static final String WORLD = "rendering-integration-review";
    private static final Map<String, Integer> DRAWS = new TreeMap<>();
    private static final Map<String, JsonObject> TARGETS = new TreeMap<>();
    private static int compositeFrame = -1, compositesInFrame, shadowColorWrites;
    private static Object compositePipeline, compositeLevel;
    private final Path root = FMLPaths.GAMEDIR.get();
    private final JsonObject report = new JsonObject();
    private final JsonArray samples = new JsonArray();
    private final String shader = System.getProperty("skyweave_compat.probe.handEffects.shader", "off");
    private boolean opened, prepared, stopped;
    private int tick;
    private CompletableFuture<?> pending;
    private final ClientFrameTiming timing = Boolean.getBoolean("skyweave_compat.probe.handEffects.cost") ? new ClientFrameTiming() : null;

    ClientHandEffectsProbe() {
        if (!Files.isRegularFile(root.resolve("rendering-review-fixture.json"))) throw new IllegalStateException("Missing disposable rendering marker");
        if (!shader.equals("off") && (!shader.endsWith(".zip") || shader.contains("/") || shader.contains("\\")
                || !(shader.startsWith("Complementary ") || shader.startsWith("BSL_"))))
            throw new IllegalArgumentException("Expected the pinned supported-GPU shader chain or off");
        report.add("samples", samples);
        report.addProperty("shader", shader);
        report.addProperty("adapter", OptionalIntegrations.enabled(Feature.HAND_EFFECTS));
        report.addProperty("tracing", Boolean.getBoolean("skyweave_compat.probe.handEffects.trace"));
        NeoForge.EVENT_BUS.addListener(this::tick);
    }

    private void tick(ClientTickEvent.Post event) {
        var mc = Minecraft.getInstance();
        if (stopped || mc.getOverlay() != null) return;
        try {
            if (!opened && mc.screen instanceof TitleScreen) {
                opened = true; mc.options.pauseOnLostFocus = false;
                mc.options.renderDistance().set(4); mc.options.simulationDistance().set(4);
                if (Files.isDirectory(root.resolve("saves/" + WORLD))) {
                    if (!Files.isRegularFile(root.resolve("saves/" + WORLD + "/.rendering-review-world")))
                        throw new IllegalStateException("Refusing an unmarked existing world");
                    mc.createWorldOpenFlows().openWorld(WORLD, () -> mc.setScreen(new TitleScreen()));
                } else mc.createWorldOpenFlows().createFreshLevel(WORLD,
                        new LevelSettings("Rendering integration review", GameType.CREATIVE, false, net.minecraft.world.Difficulty.PEACEFUL,
                                true, new GameRules(), WorldDataConfiguration.DEFAULT), new WorldOptions(42, false, false),
                        registries -> registries.registryOrThrow(Registries.WORLD_PRESET)
                                .get(ResourceLocation.withDefaultNamespace("flat")).createWorldDimensions(), mc.screen);
                return;
            }
            if (mc.player == null || mc.level == null || mc.getSingleplayerServer() == null) return;
            if (pending != null) { if (!pending.isDone()) return; pending.join(); pending = null; }
            if (!prepared) {
                prepared = true;
                Files.writeString(root.resolve("saves/" + WORLD + "/.rendering-review-world"), "owned rendering fixture\n");
                server(player -> {
                    player.teleportTo(.5, -60, .5); player.setYRot(0); player.setXRot(0);
                    player.setGameMode(GameType.SURVIVAL);
                    player.serverLevel().getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, player.getServer());
                    player.serverLevel().setDayTime(6000);
                });
            }
            mc.setScreen(null);
            if (timing != null) { costTick(); return; }
            switch (++tick) {
                case 30 -> { suppressDevCosmetics(); shaders(shader); gear(Items.DIAMOND_SWORD, null, true); mc.options.setCameraType(CameraType.FIRST_PERSON); setting("enabled", false); setting("glow", false); setting("runes", false); }
                case 45 -> stateControls();
                case 65 -> capture("effects-off");
                case 75 -> { setting("enabled", true); setting("glow", true); setting("runes", true); }
                case 110 -> capture("effects-on");
                case 120 -> setting("glow", false);
                case 145 -> capture("glow-off");
                case 155 -> setting("glow", true);
                case 180 -> capture("glow-on");
                case 190 -> setting("runes", false);
                case 215 -> capture("runes-off");
                case 225 -> { setting("runes", true); gear(Items.DIAMOND_SWORD, Items.GLASS, false); }
                case 250 -> capture("mixed-hands");
                case 260 -> gear(null, Items.GLASS, false);
                case 285 -> capture("translucent-only");
                case 295 -> gear(null, null, false);
                case 320 -> capture("empty-hands");
                case 329 -> capture("empty-hands-settled");
                case 330 -> gear(Items.TRIDENT, null, false);
                case 355 -> capture("builtin-trident");
                case 365 -> gear(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(ResourceLocation.parse("travelersbackpack:standard")), null, false);
                case 390 -> capture("builtin-backpack");
                case 400 -> { gear(Items.DIAMOND_SWORD, null, false); mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT); }
                case 425 -> capture("thirdperson-armor");
                case 435 -> { mc.options.setCameraType(CameraType.FIRST_PERSON); setting("disableVanillaGlint", true); }
                case 460 -> capture("glint-disabled");
                case 470 -> setting("disableVanillaGlint", false);
                case 495 -> capture("glint-enabled");
                case 505 -> shaders("off");
                case 530 -> capture("shaders-off");
                case 540 -> shaders(shader);
                case 565 -> capture("shader-reload");
                case 575 -> org.lwjgl.glfw.GLFW.glfwSetWindowSize(mc.getWindow().getWindow(), 800, 450);
                case 600 -> capture("resize");
                case 610 -> mc.options.hideGui = true;
                case 635 -> capture("hidden-hands");
                case 644 -> capture("hidden-hands-settled");
                case 645 -> { mc.options.hideGui = false; server(player -> player.setGameMode(GameType.SPECTATOR)); }
                case 670 -> capture("spectator");
                case 679 -> capture("spectator-settled");
                case 680 -> server(player -> player.setGameMode(GameType.SURVIVAL));
                case 705 -> capture("survival-return");
                case 715 -> pending = mc.reloadResourcePacks();
                case 740 -> capture("resource-reload");
                case 750 -> server(player -> player.teleportTo(player.getServer().getLevel(Level.NETHER), .5, 140, .5, Set.of(), 0, 0));
                case 780 -> capture("nether");
                case 790 -> server(player -> player.teleportTo(player.getServer().overworld(), .5, -60, .5, Set.of(), 0, 0));
                case 820 -> capture("overworld-return");
                case 830 -> { var server = mc.getSingleplayerServer(); server.halt(false); prepared = opened = false; mc.disconnect(new TitleScreen()); }
                case 870 -> capture("reconnect");
                case 880 -> { report.addProperty("complete", true); stop(); }
            }
        } catch (Throwable failure) {
            report.addProperty("complete", false); report.addProperty("failure", failure.toString()); failure.printStackTrace(); stop();
        }
    }

    private void server(Consumer<ServerPlayer> action) {
        var mc = Minecraft.getInstance(); var server = mc.getSingleplayerServer(); var id = mc.player.getUUID();
        pending = server.submit(() -> action.accept(server.getPlayerList().getPlayer(id)));
    }
    private void costTick() throws Exception {
        switch (++tick) {
            case 30 -> {
                suppressDevCosmetics(); shaders(shader); gear(Items.DIAMOND_SWORD, null, true);
                Minecraft.getInstance().options.setCameraType(CameraType.FIRST_PERSON);
                setting("enabled", false); setting("glow", false); setting("runes", false);
            }
            case 130 -> timing.begin("effects-off");
            case 290 -> { report.add("costOff", timing.finish()); capture("cost-effects-off"); }
            case 300 -> { setting("enabled", true); setting("glow", true); setting("runes", true); }
            case 400 -> timing.begin("effects-on");
            case 560 -> { report.add("costOn", timing.finish()); capture("cost-effects-on"); }
            case 580 -> { report.addProperty("complete", true); timing.close(); stop(); }
        }
    }

    private void gear(net.minecraft.world.item.Item main, net.minecraft.world.item.Item off, boolean armor) {
        server(player -> {
            player.setItemInHand(InteractionHand.MAIN_HAND, enchanted(player, main));
            player.setItemInHand(InteractionHand.OFF_HAND, enchanted(player, off));
            if (armor) for (var slot : List.of(EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET)) {
                var item = switch (slot) { case HEAD -> Items.DIAMOND_HELMET; case CHEST -> Items.DIAMOND_CHESTPLATE; case LEGS -> Items.DIAMOND_LEGGINGS; default -> Items.DIAMOND_BOOTS; };
                player.setItemSlot(slot, enchanted(player, item));
            }
            player.inventoryMenu.broadcastChanges();
        });
    }

    private static ItemStack enchanted(ServerPlayer player, net.minecraft.world.item.Item item) {
        if (item == null || item == Items.AIR) return ItemStack.EMPTY;
        var stack = new ItemStack(item);
        stack.enchant(player.registryAccess().registryOrThrow(Registries.ENCHANTMENT).getHolderOrThrow(Enchantments.UNBREAKING), 3);
        return stack;
    }

    static void shaders(String name) throws Exception {
        if (!ModList.get().isLoaded("iris")) { if (!name.equals("off")) throw new IllegalStateException("Missing Iris"); return; }
        var iris = Class.forName("net.irisshaders.iris.Iris"); var config = iris.getMethod("getIrisConfig").invoke(null);
        if ((boolean) config.getClass().getMethod("shouldAllowUnknownShaders").invoke(config)) throw new IllegalStateException("Unknown-shader control must remain false");
        if (!name.equals("off")) config.getClass().getMethod("setShaderPackName", String.class).invoke(config, name);
        config.getClass().getMethod("setShadersEnabled", boolean.class).invoke(config, !name.equals("off"));
        config.getClass().getMethod("save").invoke(config); iris.getMethod("reload").invoke(null);
        if ((boolean) iris.getMethod("isPackInUseQuick").invoke(null) == name.equals("off")) throw new IllegalStateException("Shader activation mismatch");
        if (!name.equals("off")) {
            var manager = iris.getMethod("getPipelineManager").invoke(null);
            var pipeline = (java.util.Optional<?>) manager.getClass().getMethod("getPipeline").invoke(manager);
            if (pipeline.isEmpty() || !Class.forName("net.irisshaders.iris.pipeline.IrisRenderingPipeline").isInstance(pipeline.get()))
                throw new IllegalStateException("Expected actual Iris rendering pipeline");
        }
    }

    private static void setting(String key, boolean value) throws Exception {
        if (!ModList.get().isLoaded("hmioutline")) return;
        var type = Class.forName("net.hmioutline.config.OutlineConfig"); type.getField(key).setBoolean(type.getMethod("get").invoke(null), value);
    }

    static void suppressDevCosmetics() throws Exception {
        if (!ModList.get().isLoaded("subtle_effects")) return;
        var general = Class.forName("einstein.subtle_effects.init.ModConfigs").getField("GENERAL").get(null);
        general.getClass().getField("enableEasterEggs").setBoolean(general, false);
    }

    private void capture(String name) throws Exception {
        var mc = Minecraft.getInstance(); var row = new JsonObject();
        row.addProperty("case", name); row.addProperty("glError", glGetError());
        if (row.get("glError").getAsInt() != 0) throw new IllegalStateException("Native GL error in " + name);
        row.addProperty("dimension", mc.level.dimension().location().toString()); row.addProperty("camera", mc.options.getCameraType().name());
        String clientMode = mc.gameMode.getPlayerMode().getName();
        var server = mc.getSingleplayerServer(); var playerId = mc.player.getUUID();
        String serverMode = server.submit(() -> server.getPlayerList().getPlayer(playerId).gameMode.getGameModeForPlayer().getName())
                .get(10, java.util.concurrent.TimeUnit.SECONDS);
        row.addProperty("clientMode", clientMode); row.addProperty("serverMode", serverMode);
        if (!clientMode.equals(serverMode)) throw new IllegalStateException("Fixture game modes disagree: " + clientMode + "/" + serverMode);
        if (name.startsWith("spectator") && mc.gameMode.getPlayerMode() != GameType.SPECTATOR)
            throw new IllegalStateException("Spectator fixture did not reach the client");
        row.addProperty("width", mc.getWindow().getWidth()); row.addProperty("height", mc.getWindow().getHeight());
        row.add("draws", new Gson().toJsonTree(DRAWS)); row.add("targets", new Gson().toJsonTree(TARGETS));
        row.addProperty("shadowColorWrites", shadowColorWrites);
        if (shadowColorWrites != 0) throw new IllegalStateException("Effect wrote into an Iris shadow pass");
        samples.add(row); save();
        Screenshot.grab(mc.gameDirectory, "hand-effects-" + name + ".png", mc.getMainRenderTarget(), ignored -> {});
    }

    private static final class StateFailure extends RuntimeException {}
    private void stateControls() throws Exception {
        if (!OptionalIntegrations.enabled(Feature.HAND_EFFECTS)) return;
        var callback = design.inherent.skyweavecompat.client.IrisHandEffects.class.getDeclaredMethod("callback", Runnable.class);
        callback.setAccessible(true);
        var before = glState();
        try {
            callback.invoke(null, (Runnable) () -> {
                com.mojang.blaze3d.systems.RenderSystem.viewport(1, 2, 3, 4);
                com.mojang.blaze3d.systems.RenderSystem.disableDepthTest();
                com.mojang.blaze3d.systems.RenderSystem.depthFunc(GL_ALWAYS);
                com.mojang.blaze3d.systems.RenderSystem.depthMask(false);
                com.mojang.blaze3d.systems.RenderSystem.colorMask(false, true, false, true);
                com.mojang.blaze3d.systems.RenderSystem.enableBlend();
                com.mojang.blaze3d.systems.RenderSystem.blendFunc(GL_ONE, GL_ONE);
                com.mojang.blaze3d.systems.RenderSystem.disableCull();
                glBindFramebuffer(GL_READ_FRAMEBUFFER, 0); glBindFramebuffer(GL_DRAW_FRAMEBUFFER, 0);
                var intermediate = glState();
                try { callback.invoke(null, (Runnable) () -> { com.mojang.blaze3d.systems.RenderSystem.viewport(5, 6, 7, 8); throw new StateFailure(); }); }
                catch (java.lang.reflect.InvocationTargetException expected) { if (!(expected.getCause() instanceof StateFailure)) throw new IllegalStateException(expected); }
                catch (IllegalAccessException inaccessible) { throw new IllegalStateException(inaccessible); }
                if (!intermediate.equals(glState())) throw new IllegalStateException("Nested GL restoration mismatch");
                throw new StateFailure();
            });
            throw new IllegalStateException("Expected native callback failure");
        } catch (java.lang.reflect.InvocationTargetException expected) {
            if (!(expected.getCause() instanceof StateFailure)) throw new IllegalStateException(expected);
        }
        if (!before.equals(glState())) throw new IllegalStateException("Exceptional GL restoration mismatch");
        if (design.inherent.skyweavecompat.client.IrisHandEffects.nativeCallback()) throw new IllegalStateException("Leaked native callback scope");
        report.addProperty("nestedExceptionalGlRestoration", true);
    }

    private static List<Integer> glState() {
        var state = new java.util.ArrayList<Integer>();
        for (int key : new int[]{GL_READ_FRAMEBUFFER_BINDING, GL_DRAW_FRAMEBUFFER_BINDING, GL_CURRENT_PROGRAM, GL_DEPTH_FUNC,
                GL_DEPTH_WRITEMASK, GL_BLEND, GL_CULL_FACE, GL_DEPTH_TEST, org.lwjgl.opengl.GL14C.GL_BLEND_SRC_RGB,
                org.lwjgl.opengl.GL14C.GL_BLEND_DST_RGB, org.lwjgl.opengl.GL14C.GL_BLEND_SRC_ALPHA, org.lwjgl.opengl.GL14C.GL_BLEND_DST_ALPHA,
                org.lwjgl.opengl.GL13C.GL_ACTIVE_TEXTURE, GL_VERTEX_ARRAY_BINDING, org.lwjgl.opengl.GL15C.GL_ARRAY_BUFFER_BINDING}) state.add(glGetInteger(key));
        int[] viewport = new int[4]; glGetIntegerv(GL_VIEWPORT, viewport); for (int value : viewport) state.add(value);
        try (var stack = MemoryStack.stackPush()) { var mask = stack.malloc(4); glGetBooleanv(GL_COLOR_WRITEMASK, mask); for (int i = 0; i < 4; i++) state.add((int)mask.get(i)); }
        state.add(System.identityHashCode(com.mojang.blaze3d.systems.RenderSystem.getShader()));
        state.add(design.inherent.skyweavecompat.mixin.client.OutlineShaderCacheAccess.skyweave$lastProgram());
        state.add(System.identityHashCode(design.inherent.skyweavecompat.mixin.client.OutlineShaderCacheAccess.skyweave$lastShader()));
        return state;
    }

    public static void draw(net.minecraft.client.renderer.ShaderInstance shader) {
        String name = shader.getName(); if (!name.contains("hmioutline")) return;
        boolean shadow = ModList.get().isLoaded("iris") && net.irisshaders.iris.shadows.ShadowRenderer.ACTIVE;
        boolean writes;
        try (var stack = MemoryStack.stackPush()) { var mask = stack.malloc(4); glGetBooleanv(GL_COLOR_WRITEMASK, mask); writes = mask.get(0) != 0 || mask.get(1) != 0 || mask.get(2) != 0 || mask.get(3) != 0; }
        if (shadow) { if (writes) shadowColorWrites++; return; }
        if (!writes && OptionalIntegrations.enabled(Feature.HAND_EFFECTS))
            throw new IllegalStateException("Admitted native effect has color writes disabled: " + name);
        DRAWS.merge(name, 1, Integer::sum);
        if (name.endsWith("glow_composite") && ModList.get().isLoaded("iris") && net.irisshaders.iris.Iris.isPackInUseQuick()) {
            int frame = net.irisshaders.iris.uniforms.SystemTimeUniforms.COUNTER.getAsInt();
            Object pipeline = net.irisshaders.iris.Iris.getPipelineManager().getPipelineNullable();
            Object level = Minecraft.getInstance().level;
            if (frame != compositeFrame || pipeline != compositePipeline || level != compositeLevel) {
                compositeFrame = frame; compositePipeline = pipeline; compositeLevel = level; compositesInFrame = 0;
            }
            if (++compositesInFrame > 1) throw new IllegalStateException("Repeated glow composite in one native frame");
        }
        var target = new JsonObject(); target.addProperty("framebuffer", glGetInteger(GL_DRAW_FRAMEBUFFER_BINDING));
        target.addProperty("writes", writes); target.addProperty("program", glGetInteger(GL_CURRENT_PROGRAM));
        target.addProperty("expectedProgram", shader.getId());
        if (target.get("program").getAsInt() != shader.getId()) throw new IllegalStateException("Effect program binding mismatch");
        int[] viewport = new int[4]; glGetIntegerv(GL_VIEWPORT, viewport); target.addProperty("width", viewport[2]); target.addProperty("height", viewport[3]);
        TARGETS.put(name, target);
    }

    private void save() throws Exception {
        ProbeStateFiles.write(root.resolve("hand-effects-results.json"), new GsonBuilder().setPrettyPrinting().create().toJson(report));
    }
    private void stop() {
        stopped = true;
        try { save(); } catch (Exception failure) { throw new IllegalStateException(failure); }
        Minecraft.getInstance().stop();
    }
}
