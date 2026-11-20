package design.inherent.skyweavecompat.clientprobe;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FaceAttachedHorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;

/** Native interaction/pose witnesses. The second avatar tests client renderer identity, not TCP transport. */
public final class ClientPoseProbe {
    private static net.minecraft.resources.ResourceLocation cape;
    private static java.util.Set<UUID> capeOwners = java.util.Set.of();
    private static final java.util.Map<UUID, Integer> capeCarries = new java.util.HashMap<>();
    private static final java.util.Map<UUID, Integer> capeKeeps = new java.util.HashMap<>();
    private static final java.util.Map<UUID, Integer> capeDraws = new java.util.HashMap<>();
    public static net.minecraft.resources.ResourceLocation cape(UUID id) { return capeOwners.contains(id) ? cape : null; }
    public static void carried(UUID id) { if (capeOwners.contains(id)) capeCarries.merge(id, 1, Integer::sum); }
    public static void keptCape(UUID id) { if (capeOwners.contains(id)) capeKeeps.merge(id, 1, Integer::sum); }
    public static void drewCape(UUID id) { if (capeOwners.contains(id)) capeDraws.merge(id, 1, Integer::sum); }
    private static final String WORLD = "rendering-pose-review";
    private final Path root = FMLPaths.GAMEDIR.get();
    private final JsonObject report = new JsonObject();
    private final JsonArray samples = new JsonArray();
    private final List<String> shaders = List.of("off", "Complementary Unbound r5.10 + Euphoria 1.11.1 + Skyweave 0.4.0.zip", "BSL_v10.1.8 + Clrwl_1.0.5 + Skyweave_0.3.0.zip");
    private CompletableFuture<?> pending;
    private RemotePlayer remote;
    private boolean opened, prepared, stopped;
    private int tick, stage;
    private long frame, lastTickFrame = -1;
    private record Capture(String name, boolean managed) {}
    private Capture pendingCapture;

    ClientPoseProbe() {
        if (!Files.isRegularFile(root.resolve("rendering-review-fixture.json"))) throw new IllegalStateException("Missing rendering marker");
        report.add("samples", samples); report.addProperty("scope", "Actual local block/item interactions and native local/synthetic-remote avatar poses; generated checker cape; no remote TCP or Sable-control-frame claim");
        NeoForge.EVENT_BUS.addListener(this::tick);
        NeoForge.EVENT_BUS.addListener(this::afterFrame);
    }
    private void afterFrame(net.neoforged.neoforge.client.event.RenderFrameEvent.Post event) {
        ++frame;
        if (stopped || pendingCapture == null || Minecraft.getInstance().getOverlay() != null) return;
        var capture = pendingCapture; pendingCapture = null;
        try { captureDrawn(capture.name(), capture.managed()); }
        catch (Throwable failure) { report.addProperty("complete", false); report.addProperty("failure", failure.toString()); failure.printStackTrace(); stop(); }
    }
    private void tick(ClientTickEvent.Post event) {
        var mc = Minecraft.getInstance(); if (stopped || mc.getOverlay() != null) return;
        try {
            if (!opened && mc.screen instanceof TitleScreen) {
                opened = true; mc.options.pauseOnLostFocus = false; mc.options.renderDistance().set(4); mc.options.simulationDistance().set(4);
                if (Files.exists(root.resolve("saves/" + WORLD))) throw new IllegalStateException("Expected fresh pose fixture world");
                mc.createWorldOpenFlows().createFreshLevel(WORLD, new LevelSettings("Pose review", GameType.SURVIVAL, false, Difficulty.PEACEFUL, true,
                        new GameRules(), WorldDataConfiguration.DEFAULT), new WorldOptions(42, false, false),
                        registries -> registries.registryOrThrow(Registries.WORLD_PRESET).get(ResourceLocation.withDefaultNamespace("flat")).createWorldDimensions(), mc.screen); return;
            }
            if (mc.player == null || mc.level == null || mc.getSingleplayerServer() == null) return;
            if (pending != null) { if (!pending.isDone()) return; pending.join(); pending = null; }
            // Shader/resource reload may run several catch-up ticks before the next actual draw.
            if (lastTickFrame == frame) return;
            lastTickFrame = frame;
            if (!prepared) {
                prepared = true; Files.writeString(root.resolve("saves/" + WORLD + "/.rendering-review-world"), "owned pose fixture\n");
                ClientHandEffectsProbe.suppressDevCosmetics();
                remote = new RemotePlayer(mc.level, new com.mojang.authlib.GameProfile(UUID.fromString("6c624f11-e851-4a4c-8fac-3056325baec7"), "PoseWitness"));
                remote.setId(1_900_000_001); remote.setPos(2.5, -60, .5); remote.setYRot(0); mc.level.addEntity(remote);
                capeOwners = java.util.Set.of(mc.player.getUUID(), remote.getUUID());
                var image = new com.mojang.blaze3d.platform.NativeImage(64, 32, false);
                for (int x = 0; x < 64; x++) for (int y = 0; y < 32; y++) image.setPixelRGBA(x, y, ((x / 4 + y / 4) & 1) == 0 ? 0xff4a40f0 : 0xffe8e8e8);
                cape = mc.getTextureManager().register("skyweave_pose_cape", new net.minecraft.client.renderer.texture.DynamicTexture(image));
                var skinBits = net.minecraft.world.entity.player.Player.class.getDeclaredField("DATA_PLAYER_MODE_CUSTOMISATION"); skinBits.setAccessible(true);
                @SuppressWarnings("unchecked") var accessor = (net.minecraft.network.syncher.EntityDataAccessor<Byte>) skinBits.get(null);
                remote.getEntityData().set(accessor, (byte) 127);
            }
            mc.setScreen(null);
            switch (++tick) {
                case 10 -> {
                    ClientHandEffectsProbe.shaders(shaders.get(stage)); mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
                    server(player -> {
                        var level = player.serverLevel(); player.setGameMode(GameType.SURVIVAL); player.teleportTo(.5, -60, .5); player.setYRot(0); player.setXRot(0);
                        player.setDeltaMovement(Vec3.ZERO);
                        level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, player.getServer()); level.setDayTime(6000);
                        level.setBlock(new BlockPos(-1,-59,2), Blocks.STONE.defaultBlockState(),2);
                        level.setBlock(new BlockPos(-1,-59,1), Blocks.STONE_BUTTON.defaultBlockState()
                                .setValue(FaceAttachedHorizontalDirectionalBlock.FACE, AttachFace.WALL)
                                .setValue(net.minecraft.world.level.block.ButtonBlock.FACING, Direction.NORTH),2);
                        level.setBlock(new BlockPos(0,-60,2), Blocks.LEVER.defaultBlockState(),2);
                        level.setBlock(new BlockPos(1,-60,2), Blocks.CHEST.defaultBlockState(),2);
                        level.setBlock(new BlockPos(2,-60,1), Blocks.AIR.defaultBlockState(),2);
                        player.setItemSlot(EquipmentSlot.MAINHAND, net.minecraft.world.item.ItemStack.EMPTY);
                        for (var slot : List.of(EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET))
                            player.setItemSlot(slot, (slot == EquipmentSlot.HEAD ? Items.IRON_HELMET : slot == EquipmentSlot.CHEST ? Items.IRON_CHESTPLATE : slot == EquipmentSlot.LEGS ? Items.IRON_LEGGINGS : Items.IRON_BOOTS).getDefaultInstance());
                        player.inventoryMenu.broadcastChanges();
                    });
                }
                case 40 -> capture("armored-idle", true);
                case 50 -> interact(new BlockPos(-1,-59,1), Direction.NORTH);
                case 56 -> capture("button-contact", true);
                case 70 -> interact(new BlockPos(0,-60,2), Direction.UP);
                case 76 -> capture("lever-contact", true);
                case 90 -> interact(new BlockPos(1,-60,2), Direction.NORTH);
                case 98 -> { capture("chest-contact", true); mc.player.closeContainer(); }
                case 110 -> item(Items.SHIELD);
                case 130 -> mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);
                case 138 -> capture("shield-use", true);
                case 150 -> { mc.gameMode.releaseUsingItem(mc.player); item(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(ResourceLocation.parse("create:wrench"))); }
                case 170 -> capture("create-tool", true);
                case 180 -> { item(Items.AIR); carryKey(true); }
                case 205 -> interact(new BlockPos(1,-60,2), Direction.NORTH);
                case 216 -> { capture("carry-on-held", true); carryKey(false); }
                case 225 -> {
                    if (!mc.level.getBlockState(new BlockPos(2,-60,1)).isAir()) throw new AssertionError("Carry destination must be empty before placement");
                    interact(new BlockPos(2,-61,1), Direction.UP);
                }
                case 235 -> { capture("carry-on-placed", true); mc.options.keyUp.setDown(true); mc.options.keyJump.setDown(true); }
                case 250 -> { capture("moving", true); mc.options.keyUp.setDown(false); mc.options.keyJump.setDown(false); }
                case 270 -> server(player -> {
                    player.serverLevel().setBlock(new BlockPos(0,-60,0), Blocks.STONE_SLAB.defaultBlockState(),2);
                    player.teleportTo(.5, -59.5, .5);
                });
                case 295 -> capture("uneven-ground", true);
                case 310 -> { mc.options.setCameraType(CameraType.FIRST_PERSON); item(Items.SPYGLASS); }
                case 325 -> mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);
                case 335 -> { capture("firstperson-owner", false); mc.gameMode.releaseUsingItem(mc.player); }
                case 345 -> { mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT); pending = mc.reloadResourcePacks(); }
                case 380 -> capture("resource-reload", true);
                case 385 -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
                case 395 -> capture("cape-back", true);
                case 400 -> {
                    if (++stage == shaders.size()) { report.addProperty("complete", true); stop(); }
                    else { tick = 0; server(player -> player.serverLevel().setBlock(new BlockPos(0,-60,0), Blocks.AIR.defaultBlockState(),2)); }
                }
            }
        } catch (Throwable failure) { report.addProperty("complete", false); report.addProperty("failure", failure.toString()); failure.printStackTrace(); stop(); }
    }
    private void item(net.minecraft.world.item.Item item) { server(player -> { player.setItemSlot(EquipmentSlot.MAINHAND, item.getDefaultInstance()); player.inventoryMenu.broadcastChanges(); }); }
    private static void carryKey(boolean down) {
        var mc = Minecraft.getInstance(); mc.options.keyShift.setDown(down);
        for (var key : mc.options.keyMappings) if (key.getName().toLowerCase(java.util.Locale.ROOT).contains("carryon")) key.setDown(down);
    }
    private static void interact(BlockPos pos, Direction face) {
        var mc = Minecraft.getInstance(); var hit = new BlockHitResult(Vec3.atCenterOf(pos), face, pos, false); mc.hitResult = hit;
        mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, hit);
    }
    private void capture(String name, boolean managed) throws Exception {
        if (pendingCapture != null) throw new AssertionError("Overlapping pose capture requests");
        pendingCapture = new Capture(name, managed);
    }
    private void captureDrawn(String name, boolean managed) throws Exception {
        var mc = Minecraft.getInstance(); var bridge = Class.forName("punchymoves.client.compat.TouchMotionBridge");
        var method = bridge.getMethod("manages", AbstractClientPlayer.class);
        boolean actual = (boolean) method.invoke(null, mc.player); if (actual != managed) throw new AssertionError("Native Moves/TnM owner mismatch: " + name);
        var row = new JsonObject(); row.addProperty("case", name); row.addProperty("shader", shaders.get(stage)); row.addProperty("managed", actual);
        row.addProperty("remoteManaged", (boolean) method.invoke(null, remote)); row.addProperty("usingItem", mc.player.isUsingItem());
        row.addProperty("localCapeCalls", capeCarries.getOrDefault(mc.player.getUUID(), 0));
        row.addProperty("remoteCapeCalls", capeCarries.getOrDefault(remote.getUUID(), 0));
        row.addProperty("localCapeKeeps", capeKeeps.getOrDefault(mc.player.getUUID(), 0));
        row.addProperty("remoteCapeKeeps", capeKeeps.getOrDefault(remote.getUUID(), 0));
        row.addProperty("localCapeDraws", capeDraws.getOrDefault(mc.player.getUUID(), 0));
        row.addProperty("remoteCapeDraws", capeDraws.getOrDefault(remote.getUUID(), 0));
        if (name.equals("cape-back") && (capeKeeps.getOrDefault(mc.player.getUUID(), 0) == 0
                || capeDraws.getOrDefault(mc.player.getUUID(), 0) == 0)) throw new AssertionError("Native cape pose or geometry was not observed");
        row.addProperty("carryTarget", mc.level.getBlockState(new BlockPos(1,-60,2)).toString());
        boolean carrying = carrying(mc.player);
        var server = mc.getSingleplayerServer(); var playerId = mc.player.getUUID();
        boolean serverCarrying = server.submit(() -> carrying(server.getPlayerList().getPlayer(playerId)))
                .get(10, java.util.concurrent.TimeUnit.SECONDS);
        row.addProperty("clientCarrying", carrying); row.addProperty("serverCarrying", serverCarrying);
        if (carrying != serverCarrying) throw new AssertionError("Client/server Carry On custody differs: " + name);
        if (name.equals("carry-on-held") && !carrying || name.equals("carry-on-placed") && carrying)
            throw new AssertionError("Native Carry On custody did not transition: " + name);
        if (name.equals("carry-on-held") && !mc.level.getBlockState(new BlockPos(1,-60,2)).isAir()) throw new AssertionError("Native Carry On pickup did not happen");
        if (name.equals("carry-on-placed") && !mc.level.getBlockState(new BlockPos(2,-60,1)).is(Blocks.CHEST)) throw new AssertionError("Native Carry On placement did not happen");
        row.addProperty("frame", frame); row.addProperty("tick", tick); row.addProperty("stage", stage);
        row.addProperty("position", mc.player.position().toString()); row.addProperty("camera", mc.gameRenderer.getMainCamera().getPosition().toString());
        row.addProperty("glError", org.lwjgl.opengl.GL11C.glGetError());
        if (row.get("glError").getAsInt() != 0) throw new AssertionError("Pose GL error");
        var field = bridge.getDeclaredField("DRAWN"); field.setAccessible(true); var poses = (Map<?,?>) field.get(null); row.addProperty("nativePoseOwners", poses.size());
        var values = new JsonArray(); var allIds = new JsonArray();
        for (var entry : poses.entrySet()) {
            int id = entry.getKey().getClass().getField("id").getInt(entry.getKey());
            allIds.add(id);
            if (id != mc.player.getId() && id != remote.getId()) continue;
            var accessor = entry.getValue().getClass().getDeclaredMethod("pose"); accessor.setAccessible(true);
            var value = new JsonObject(); value.addProperty("entity", id); value.add("pose", new Gson().toJsonTree(accessor.invoke(entry.getValue()))); values.add(value);
        }
        row.add("drawnPoses", values); row.add("nativePoseIds", allIds); samples.add(row); save();
        if (managed && values.isEmpty()) throw new AssertionError("No native drawn pose");
        Screenshot.grab(mc.gameDirectory, "pose-" + stage + "-" + name + ".png", mc.getMainRenderTarget(), ignored -> {});
    }
    private void server(java.util.function.Consumer<ServerPlayer> action) { var mc = Minecraft.getInstance(); var server = mc.getSingleplayerServer(); var id = mc.player.getUUID(); pending = server.submit(() -> action.accept(server.getPlayerList().getPlayer(id))); }
    private static boolean carrying(net.minecraft.world.entity.player.Player player) {
        try {
            var type = Class.forName("tschipp.carryon.common.carry.CarryOnDataManager");
            var data = type.getMethod("getCarryData", net.minecraft.world.entity.player.Player.class).invoke(null, player);
            return (boolean) data.getClass().getMethod("isCarrying").invoke(data);
        } catch (ReflectiveOperationException failure) { throw new IllegalStateException(failure); }
    }
    private void save() throws Exception { ProbeStateFiles.write(root.resolve("pose-results.json"), new GsonBuilder().setPrettyPrinting().create().toJson(report)); }
    private void stop() { stopped = true; carryKey(false); Minecraft.getInstance().options.keyUp.setDown(false); Minecraft.getInstance().options.keyJump.setDown(false); try { save(); } catch (Exception failure) { throw new IllegalStateException(failure); } if (cape != null) Minecraft.getInstance().getTextureManager().release(cape); cape = null; capeOwners = java.util.Set.of(); capeCarries.clear(); capeKeeps.clear(); capeDraws.clear(); Minecraft.getInstance().stop(); }
}
