package design.inherent.skyweavecompat.clientprobe;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.seibel.distanthorizons.api.DhApi;
import com.seibel.distanthorizons.core.level.IDhClientLevel;
import com.seibel.distanthorizons.core.pos.DhSectionPos;
import com.seibel.distanthorizons.core.wrapperInterfaces.world.IClientLevelWrapper;
import design.inherent.skyweavecompat.client.ConstructCoverage;
import design.inherent.skyweavecompat.rendering.CoverageCarrier;
import design.inherent.skyweavecompat.rendering.CoverageColumns;
import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.api.physics.constraint.FixedConstraintConfiguration;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
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
import net.neoforged.neoforge.network.PacketDistributor;
import org.joml.Quaterniond;
import org.joml.Vector3d;

/** Actual native delivery, mesh publication and intervening-source controls in a disposable flat world. */
public final class ClientCoverageProbe {
    private static final java.util.Map<UUID, Integer> DRAWS = new java.util.HashMap<>();
    public static void draw(UUID id) { if (DRAWS.containsKey(id)) DRAWS.compute(id, (key, value) -> value + 1); }
    private static final String WORLD = "rendering-coverage-review";
    private final Path root = FMLPaths.GAMEDIR.get();
    private final JsonObject report = new JsonObject();
    private final JsonArray samples = new JsonArray();
    private CompletableFuture<?> pending;
    private UUID near, far;
    private CoverageCarrier removedSource;
    private CoverageColumns savedColumns;
    private boolean opened, prepared, stopped, rejoining;
    private int tick, readyAt = -1;

    ClientCoverageProbe() {
        if (!Files.isRegularFile(root.resolve("rendering-review-fixture.json"))) throw new IllegalStateException("Missing rendering fixture marker");
        report.add("samples", samples);
        NeoForge.EVENT_BUS.addListener(this::tick);
    }

    private void tick(ClientTickEvent.Post event) {
        var mc = Minecraft.getInstance();
        if (stopped || mc.getOverlay() != null) return;
        try {
            if (!opened && mc.screen instanceof TitleScreen) {
                opened = true;
                if (Files.exists(root.resolve("saves/" + WORLD)) && !rejoining) throw new IllegalStateException("Coverage driver must provide a fresh owned fixture world");
                mc.options.pauseOnLostFocus = false; mc.options.renderDistance().set(4); mc.options.simulationDistance().set(4);
                mc.options.setCameraType(CameraType.FIRST_PERSON);
                if (rejoining) mc.createWorldOpenFlows().openWorld(WORLD, () -> mc.setScreen(new TitleScreen()));
                else mc.createWorldOpenFlows().createFreshLevel(WORLD,
                        new LevelSettings("Coverage review", GameType.CREATIVE, false, Difficulty.PEACEFUL, true,
                                new GameRules(), WorldDataConfiguration.DEFAULT), new WorldOptions(42, false, false),
                        registries -> registries.registryOrThrow(Registries.WORLD_PRESET)
                                .get(ResourceLocation.withDefaultNamespace("flat")).createWorldDimensions(), mc.screen);
                return;
            }
            if (mc.player == null || mc.level == null || mc.getSingleplayerServer() == null) return;
            mc.setScreen(null);
            mc.player.setYRot(-90); mc.player.yRotO = -90; mc.player.setXRot(0); mc.player.xRotO = 0;
            if (pending != null) { if (!pending.isDone()) return; pending.join(); pending = null; }
            if (!prepared) {
                prepared = true;
                Files.writeString(root.resolve("saves/" + WORLD + "/.rendering-review-world"), "owned coverage fixture\n");
                configure();
                server(player -> {
                    var level = player.serverLevel(); level.setDayTime(6000);
                    player.setGameMode(GameType.SPECTATOR);
                    level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, player.getServer());
                    if (Boolean.getBoolean("skyweave_compat.probe.coverage.void"))
                        for (int x = 128; x < 192; x++) for (int z = 0; z < 64; z++) for (int y = -64; y <= -61; y++)
                            level.setBlock(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState(), 2);
                    player.teleportTo(.5, 102, .5); player.setYRot(-90); player.setXRot(0);
                });
            }
            tick++;
            if (tick == 30) server(player -> near = body(player.serverLevel(), new BlockPos(24, 100, -8), 1));
            if (tick == 31 && near != null) DRAWS.put(near, 0);
            // Actual client chunk deliveries seed the existing DH path; the adapter requests none.
            if (tick == 40 || tick == 100 || tick == 160 || tick == 220) {
                int x = (tick - 40) / 60 * 64 + 64;
                server(player -> player.teleportTo(x + .5, 102, .5));
            }
            if (tick == 280) server(player -> player.teleportTo(.5, 102, .5));
            if (tick == 250) server(player -> far = body(player.serverLevel(), new BlockPos(256, 100, 0), 4));
            if (tick == 251 && far != null) DRAWS.put(far, 0);
            if (tick >= 340 && tick % 20 == 0 && readyAt < 0) {
                var distant = find(far); var nearby = find(near);
                if (distant == null || nearby == null) {
                    capture("waiting-for-native-delivery", false);
                    if (tick > 1300) throw new AssertionError("Native SSRD delivery did not supply both constructs");
                    return;
                }
                if (!ConstructCoverage.allows(nearby)) throw new AssertionError("Nearby construct was withheld");
                boolean allowed = ConstructCoverage.allows(distant);
                capture("waiting-for-native-coverage", allowed);
                if (allowed) {
                    removeInterveningSource();
                    if (removedSource != null) readyAt = tick;
                }
                if (tick > 1300 && readyAt < 0) throw new AssertionError("No complete published intervening DH coverage");
            }
            if (readyAt >= 0 && tick == readyAt + 5) DRAWS.put(far, 0);
            if (readyAt >= 0 && tick == readyAt + 20) {
                boolean allowed = ConstructCoverage.allows(find(far)); capture("intervening-source-missing", allowed);
                if (allowed) throw new AssertionError("Distant construct received partial corridor credit");
                if (DRAWS.getOrDefault(far, 0) != 0) throw new AssertionError("Withheld body still entered native color draw");
                removedSource.skyweave$coverage(savedColumns);
            }
            if (readyAt >= 0 && tick == readyAt + 40) {
                boolean allowed = ConstructCoverage.allows(find(far)); capture("source-restored", allowed);
                if (!allowed || DRAWS.getOrDefault(far, 0) == 0) throw new AssertionError("Fresh native coverage did not restore the same native draw");
                benchmark();
                unavailableDhWorld();
                if (Boolean.getBoolean("skyweave_compat.probe.coverage.quick")) { report.addProperty("complete", true); stop(); }
            }
            if (readyAt >= 0 && tick == readyAt + 50) Minecraft.getInstance().levelRenderer.allChanged();
            if (readyAt >= 0 && tick == readyAt + 100) capture("renderer-reload", ConstructCoverage.allows(find(far)));
            if (readyAt >= 0 && tick == readyAt + 110) server(player -> player.teleportTo(1024.5, 102, .5));
            if (readyAt >= 0 && tick == readyAt + 140) server(player -> player.teleportTo(.5, 102, .5));
            if (readyAt >= 0 && tick == readyAt + 220) capture("teleport-return", ConstructCoverage.allows(find(far)));
            if (readyAt >= 0 && tick == readyAt + 230) server(player -> player.teleportTo(player.getServer().getLevel(Level.NETHER), .5, 140, .5, java.util.Set.of(), -90, 0));
            if (readyAt >= 0 && tick == readyAt + 270) {
                if (mc.level.dimension() != Level.NETHER || ConstructCoverage.pendingEntries() != 0) throw new AssertionError("Dimension retained old coverage requests");
                capture("nether-reset", false);
                server(player -> player.teleportTo(player.getServer().overworld(), .5, 102, .5, java.util.Set.of(), -90, 0));
            }
            if (readyAt >= 0 && tick == readyAt + 390) capture("overworld-return", ConstructCoverage.allows(find(far)));
            if (readyAt >= 0 && tick == readyAt + 410) {
                var server = mc.getSingleplayerServer(); server.halt(false); rejoining = true; prepared = opened = false; mc.disconnect(new TitleScreen());
            }
            if (rejoining && readyAt >= 0 && tick >= readyAt + 500 && tick % 20 == 0) {
                var ship = find(far);
                if (ship != null && ConstructCoverage.allows(ship)) {
                    capture("reconnect", true); report.addProperty("complete", true); stop();
                } else if (tick > readyAt + 800) throw new AssertionError("Coverage did not recover after reconnect");
            }
        } catch (Throwable failure) {
            report.addProperty("complete", false); report.addProperty("failure", failure.toString()); failure.printStackTrace(); stop();
        }
    }

    private static UUID body(net.minecraft.server.level.ServerLevel level, BlockPos origin, int radius) {
        var blocks = new ArrayList<BlockPos>();
        for (int x = -radius; x <= radius; x++) for (int y = 0; y < 3; y++) for (int z = -radius; z <= radius; z++) {
            var pos = origin.offset(x, y, z); blocks.add(pos); level.setBlock(pos, Blocks.GOLD_BLOCK.defaultBlockState(), 2);
        }
        var ship = SubLevelAssemblyHelper.assembleBlocks(level, origin, blocks, BoundingBox3i.from(blocks));
        // Fixture-only native ticket isolates draw admission from archived-body discovery.
        if (!SubLevelContainer.getContainer(level).addForceLoadTicket(ship,
                dev.ryanhcode.sable.api.sublevel.ticket.SubLevelLoadingTicketType.COMMAND_FORCED,
                net.minecraft.util.Unit.INSTANCE)) throw new AssertionError("Fixture force-load ticket refused");
        var center = ship.getPlot().getCenterBlock(); var world = ship.logicalPose().transformPosition(center.getCenter());
        SubLevelContainer.getContainer(level).physicsSystem().getPipeline().addConstraint(ship, null,
                new FixedConstraintConfiguration(new Vector3d(center.getX() + .5, center.getY() + .5, center.getZ() + .5),
                        new Vector3d(world.x, world.y, world.z), new Quaterniond(ship.logicalPose().orientation()).conjugate()));
        return ship.getUniqueId();
    }

    private void configure() throws Exception {
        String shader = System.getProperty("skyweave_compat.probe.coverage.shader", "off");
        if (net.neoforged.fml.ModList.get().isLoaded("iris")) IrisOnly.configure(shader);
        else if (!shader.equals("off")) throw new IllegalStateException("Shader requested without Iris");
        net.ranold.ssrd.Config.setPhysicsRenderDistance(32);
        PacketDistributor.sendToServer(new net.ranold.ssrd.ClientConfigSyncPacket(32));
        if (DhApi.Delayed.configs != null) {
            DhApi.Delayed.configs.worldGenerator().enableDistantWorldGeneration().setValue(false);
            DhApi.Delayed.configs.graphics().renderingEnabled().setValue(true);
            DhApi.Delayed.configs.graphics().renderingMode().setValue(com.seibel.distanthorizons.api.enums.rendering.EDhApiRendererMode.DEFAULT);
        }
        report.addProperty("shader", shader);
        report.addProperty("voidFixture", Boolean.getBoolean("skyweave_compat.probe.coverage.void"));
        report.addProperty("irisInstalled", net.neoforged.fml.ModList.get().isLoaded("iris"));
    }
    private static final class IrisOnly {
        static void configure(String shader) throws Exception {
            var iris = net.irisshaders.iris.Iris.getIrisConfig();
            if (iris.shouldAllowUnknownShaders()) throw new IllegalStateException("Unknown shaders must remain restricted");
            if (!shader.equals("off")) iris.setShaderPackName(shader);
            iris.setShadersEnabled(!shader.equals("off")); iris.save(); net.irisshaders.iris.Iris.reload();
        }
        static boolean shadow() { return net.irisshaders.iris.shadows.ShadowRenderer.ACTIVE; }
    }
    public static boolean shadow() { return net.neoforged.fml.ModList.get().isLoaded("iris") && IrisOnly.shadow(); }

    private ClientSubLevel find(UUID id) {
        for (var ship : SubLevelContainer.getContainer(Minecraft.getInstance().level).getAllSubLevels())
            if (ship.getUniqueId().equals(id)) return ship;
        return null;
    }

    private void unavailableDhWorld() {
        var original = DhApi.Delayed.worldProxy;
        int[] calls = {0};
        try {
            DhApi.Delayed.worldProxy = (com.seibel.distanthorizons.api.interfaces.world.IDhApiWorldProxy)
                    java.lang.reflect.Proxy.newProxyInstance(original.getClass().getClassLoader(),
                            new Class<?>[]{com.seibel.distanthorizons.api.interfaces.world.IDhApiWorldProxy.class},
                            (proxy, method, args) -> {
                                if (method.getName().equals("worldLoaded")) { calls[0]++; return false; }
                                throw new AssertionError("No DH world: creating/resolving lookup must not run: " + method.getName());
                            });
            ConstructCoverage.clear();
            ConstructCoverage.allows(find(far));
            if (calls[0] == 0) throw new AssertionError("DH availability boundary was not exercised");
            report.addProperty("unavailableDhWorldGuard", true);
        } finally {
            DhApi.Delayed.worldProxy = original;
            ConstructCoverage.clear();
        }
    }

    private void removeInterveningSource() {
        var wrapper = (IClientLevelWrapper) DhApi.Delayed.worldProxy.getSinglePlayerLevel();
        var level = (IDhClientLevel) wrapper.getDhLevel(); var tree = level.getRenderBufferHandler().lodQuadTree;
        int x = 128, z = 0;
        for (byte detail = tree.treeLeafDetailLevel; detail <= tree.treeRootDetailLevel; detail++) {
            int width = DhSectionPos.getDetailLevelWidthInBlocks(detail);
            long position = DhSectionPos.encode(detail, Math.floorDiv(x, width), Math.floorDiv(z, width));
            var section = tree.tryGetValue(position);
            if (section == null || !section.getRenderingEnabled() || section.renderBufferContainer == null) continue;
            var carrier = (CoverageCarrier) section.renderBufferContainer; var columns = carrier.skyweave$coverage();
            if (columns == null) continue;
            savedColumns = columns; removedSource = carrier; carrier.skyweave$coverage(null);
            report.addProperty("interveningSection", position); report.addProperty("sourceSection", columns.section());
            report.addProperty("sourceState", columns.cell(x / 16, z / 16).name());
            if (Boolean.getBoolean("skyweave_compat.probe.coverage.void") && columns.cell(x / 16, z / 16) != CoverageColumns.State.EMPTY)
                throw new AssertionError("Native void column did not retain explicit EMPTY publication");
            report.addProperty("nativeFullChunkPresent", Minecraft.getInstance().level.getChunkSource()
                    .getChunk(x / 16, z / 16, net.minecraft.world.level.chunk.status.ChunkStatus.FULL, false) != null);
            return;
        }
    }

    private void server(java.util.function.Consumer<ServerPlayer> action) {
        var mc = Minecraft.getInstance(); var server = mc.getSingleplayerServer(); var id = mc.player.getUUID();
        pending = server.submit(() -> action.accept(server.getPlayerList().getPlayer(id)));
    }

    private void capture(String name, boolean allowed) throws Exception {
        var row = new JsonObject(); row.addProperty("case", name); row.addProperty("tick", tick); row.addProperty("allowed", allowed);
        var mc = Minecraft.getInstance();
        row.addProperty("player", mc.player.position().toString()); row.addProperty("nearId", String.valueOf(near)); row.addProperty("farId", String.valueOf(far));
        row.addProperty("clientMode", mc.gameMode.getPlayerMode().getName());
        row.addProperty("camera", mc.gameRenderer.getMainCamera().getPosition().toString());
        row.addProperty("cameraYaw", mc.gameRenderer.getMainCamera().getYRot()); row.addProperty("cameraPitch", mc.gameRenderer.getMainCamera().getXRot());
        var ships = new JsonArray();
        for (var ship : SubLevelContainer.getContainer(mc.level).getAllSubLevels()) {
            var item = new JsonObject(); item.addProperty("id", ship.getUniqueId().toString());
            item.addProperty("minX", ship.boundingBox().minX()); item.addProperty("minY", ship.boundingBox().minY());
            item.addProperty("maxX", ship.boundingBox().maxX()); item.addProperty("maxY", ship.boundingBox().maxY()); ships.add(item);
            item.addProperty("renderData", ship.getRenderData().getClass().getName()); item.addProperty("nativeColorDraws", DRAWS.getOrDefault(ship.getUniqueId(), 0));
        }
        row.add("clientShips", ships);
        var server = mc.getSingleplayerServer(); var playerId = mc.player.getUUID();
        row.add("server", server.submit(() -> {
            var state = new JsonObject();
            var player = server.getPlayerList().getPlayer(playerId);
            state.addProperty("loadedShips", SubLevelContainer.getContainer(player.serverLevel()).getAllSubLevels().size());
            var container = SubLevelContainer.getContainer(player.serverLevel());
            state.addProperty("farHeld", far != null && container.getHoldingChunkMap().getHoldingSubLevel(far) != null);
            var bodies = new JsonArray();
            for (var body : container.getAllSubLevels()) {
                var item = new JsonObject(); item.addProperty("id", body.getUniqueId().toString());
                item.addProperty("position", body.logicalPose().position().toString()); bodies.add(item);
            }
            state.add("bodies", bodies);
            state.addProperty("requestedRange", String.valueOf(net.ranold.ssrd.ssrd.playerRequestedRanges.get(player)));
            state.addProperty("serverRange", net.ranold.ssrd.Config.physicsTrackingRange); return state;
        }).get(5, java.util.concurrent.TimeUnit.SECONDS));
        row.addProperty("reads", ConstructCoverage.queryReads()); row.addProperty("entries", ConstructCoverage.pendingEntries());
        row.addProperty("glError", org.lwjgl.opengl.GL11C.glGetError()); samples.add(row); save();
        if (!name.startsWith("waiting")) Screenshot.grab(Minecraft.getInstance().gameDirectory, "coverage-" + name + ".png",
                Minecraft.getInstance().getMainRenderTarget(), ignored -> {});
    }
    private void save() throws Exception { ProbeStateFiles.write(root.resolve("coverage-results.json"), new GsonBuilder().setPrettyPrinting().create().toJson(report)); }
    private void benchmark() {
        var ship = find(far); var memory = (com.sun.management.ThreadMXBean) java.lang.management.ManagementFactory.getThreadMXBean();
        long thread = Thread.currentThread().threadId();
        for (int i = 0; i < 100; i++) { ConstructCoverage.beginFrame(); if (!ConstructCoverage.allows(ship)) throw new AssertionError("Warm query changed coverage"); }
        int chunks = Minecraft.getInstance().level.getChunkSource().getLoadedChunksCount();
        long bytes = memory.getThreadAllocatedBytes(thread), time = System.nanoTime();
        for (int i = 0; i < 1000; i++) { ConstructCoverage.beginFrame(); if (!ConstructCoverage.allows(ship)) throw new AssertionError("Measured query changed coverage"); }
        var result = new JsonObject(); result.addProperty("iterations", 1000); result.addProperty("nanosPerRevalidation", (System.nanoTime() - time) / 1000.0);
        result.addProperty("bytesPerRevalidation", (memory.getThreadAllocatedBytes(thread) - bytes) / 1000.0);
        result.addProperty("cellReads", ConstructCoverage.queryReads()); result.addProperty("entries", ConstructCoverage.pendingEntries());
        result.addProperty("clientChunksBefore", chunks); result.addProperty("clientChunksAfter", Minecraft.getInstance().level.getChunkSource().getLoadedChunksCount());
        result.addProperty("scope", "Single actual delivered body, current native/DH sources, repeated whole-frame revalidation; not multi-ship frame/FPS timing"); report.add("queryCost", result);
    }
    private void stop() {
        stopped = true;
        if (removedSource != null && removedSource.skyweave$coverage() == null) removedSource.skyweave$coverage(savedColumns);
        try { save(); } catch (Exception failure) { throw new IllegalStateException(failure); }
        Minecraft.getInstance().stop();
    }
}
