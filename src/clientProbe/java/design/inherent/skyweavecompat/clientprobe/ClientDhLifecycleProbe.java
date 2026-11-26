/* SPDX-License-Identifier: Apache-2.0 */
package design.inherent.skyweavecompat.clientprobe;

import com.google.gson.*;
import com.seibel.distanthorizons.core.api.internal.SharedApi;
import com.seibel.distanthorizons.core.config.Config;
import com.seibel.distanthorizons.core.dataObjects.fullData.sources.FullDataSourceV2;
import com.seibel.distanthorizons.core.dataObjects.render.bufferBuilding.LodBufferContainer;
import com.seibel.distanthorizons.core.level.IDhClientLevel;
import com.seibel.distanthorizons.core.pos.DhChunkPos;
import com.seibel.distanthorizons.core.pos.DhSectionPos;
import com.seibel.distanthorizons.core.render.RenderThreadTaskHandler;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.*;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;

/** Actual native client data/uploads and lifecycle, confined to its marked disposable game directory. */
public final class ClientDhLifecycleProbe {
    private static volatile ClientDhLifecycleProbe active;
    private static final ThreadLocal<ArrayDeque<Long>> CLOSES = ThreadLocal.withInitial(ArrayDeque::new);
    private final Path root = FMLPaths.GAMEDIR.get();
    private final JsonObject report = new JsonObject();
    private final ConcurrentHashMap<Integer, Uploads> uploads = new ConcurrentHashMap<>();
    private final ConcurrentLinkedQueue<JsonObject> nativeCloses = new ConcurrentLinkedQueue<>();
    private final AtomicInteger servicesDuringClose = new AtomicInteger();
    private volatile int epoch;
    private int ticks, phase, phaseTicks;
    private boolean finished;
    private CompletableFuture<FullDataSourceV2> pending;
    private CompletableFuture<Void> reload;
    private boolean reloadVerified;
    private int uploadsBeforeReload;
    private JsonArray firstColumn;
    private JsonArray currentColumn;
    private volatile String observerFailure;

    private static final class Uploads {
        final AtomicInteger queued = new AtomicInteger(), pending = new AtomicInteger(), completed = new AtomicInteger(), failed = new AtomicInteger();
        final AtomicLong vertices = new AtomicLong();
    }

    ClientDhLifecycleProbe() {
        try {
            var marker = JsonParser.parseString(Files.readString(root.resolve("dh-client-fixture.json"))).getAsJsonObject();
            if (!marker.get("owner").getAsString().equals("skyweave-dh-client")) throw new IllegalStateException("Wrong client fixture owner");
        } catch (Exception failure) { throw new IllegalStateException("Refusing DH lifecycle outside its marked disposable client", failure); }
        report.addProperty("scope", "Actual integrated native DH data, GPU upload futures, disconnect/reopen and reload; no screenshots, copied renderer or synthetic uploads");
        active = this; NeoForge.EVENT_BUS.addListener(this::tick);
    }

    public static void uploaded(IDhClientLevel level, CompletableFuture<LodBufferContainer> future) {
        var probe = active; if (probe == null || probe.finished || future == null) return;
        var mc = Minecraft.getInstance();
        if (!ownsCurrentLevel(level, mc)) return;
        int epoch = probe.epoch; var stats = probe.uploads.computeIfAbsent(epoch, ignored -> new Uploads());
        stats.queued.incrementAndGet(); stats.pending.incrementAndGet();
        // Retain only primitive counters; do not keep the old world, section, future or GPU container alive.
        future.whenComplete((container, failure) -> {
            try {
                if (failure != null) stats.failed.incrementAndGet();
                else if (container != null && container.buffersUploaded) {
                    long vertices = vertices(container.vboOpaqueWrappers) + vertices(container.vboTransparentWrappers);
                    if (vertices > 0) { stats.completed.incrementAndGet(); stats.vertices.addAndGet(vertices); }
                }
            } catch (Throwable failureInObserver) { probe.observerFailure = failureInObserver.toString(); }
            finally { stats.pending.decrementAndGet(); }
        });
    }
    private static long vertices(com.seibel.distanthorizons.core.wrapperInterfaces.render.objects.IVertexBufferWrapper[] buffers) {
        long count = 0; if (buffers != null) for (var buffer : buffers) if (buffer != null) count += buffer.getVertexCount(); return count;
    }
    private static boolean ownsCurrentLevel(IDhClientLevel level, Minecraft mc) {
        if (mc.level == null) return false;
        // DhClientServerLevel.getClientLevelWrapper() returns the global current client wrapper,
        // even for another dimension. Its retained server-level owner supplies the real identity.
        var wrapped = level.getLevelWrapper().getWrappedMcObject();
        if (wrapped == mc.level) return true;
        var server = mc.getSingleplayerServer();
        return server != null && wrapped == server.getLevel(mc.level.dimension());
    }
    public static long closing() {
        long start = System.nanoTime(); CLOSES.get().push(start); return start;
    }
    public static void closed(long start, Throwable failure) {
        var probe = active; if (!CLOSES.get().isEmpty()) CLOSES.get().pop();
        if (probe == null) return;
        var row = new JsonObject(); row.addProperty("epoch", probe.epoch); row.addProperty("thread", Thread.currentThread().getName());
        row.addProperty("elapsedNanos", System.nanoTime() - start); row.addProperty("nativeReturned", failure == null);
        if (failure != null) row.addProperty("failure", failure.toString()); probe.nativeCloses.add(row);
    }
    public static void serviced() {
        var probe = active;
        if (probe != null && !CLOSES.get().isEmpty()) probe.servicesDuringClose.incrementAndGet();
    }
    @SuppressWarnings("unchecked") private static int queuedRenderTasks() throws ReflectiveOperationException {
        var field = RenderThreadTaskHandler.class.getDeclaredField("RENDER_THREAD_RUNNABLE_QUEUE"); field.setAccessible(true);
        return ((Queue<?>)field.get(null)).size();
    }

    private void tick(ClientTickEvent.Post event) {
        if (finished || !automationAllowed()) return;
        var mc = Minecraft.getInstance();
        try {
            if (observerFailure != null) throw new AssertionError("Native upload observer failed: " + observerFailure);
            if (++ticks > Integer.getInteger("skyweave_compat.probe.dhLifecycleMaxTicks", 7200))
                throw new AssertionError("Native client lifecycle observation exceeded its disposable window");
            phaseTicks++;
            if (mc.getOverlay() != null) return;
            if (phase == 0 && mc.screen instanceof TitleScreen) {
                mc.options.pauseOnLostFocus = false;
                Config.Client.Advanced.Graphics.Quality.lodChunkRenderDistanceRadius.set(32);
                Config.Common.MultiThreading.numberOfThreads.set(4);
                Config.Common.MultiThreading.threadRunTimeRatio.set(1.0);
                report.addProperty("effectiveLodRadius", Config.Client.Advanced.Graphics.Quality.lodChunkRenderDistanceRadius.get());
                report.addProperty("effectiveDhThreads", Config.Common.MultiThreading.numberOfThreads.get());
                open(mc); phase = 1; phaseTicks = 0;
            } else if ((phase == 1 || phase == 3) && mc.level != null && mc.player != null) {
                if (currentColumn == null) readColumn(mc);
                var stats = uploads.get(epoch);
                if (currentColumn == null || stats == null || stats.completed.get() == 0) { save(); return; }
                if (firstColumn == null) firstColumn = currentColumn.deepCopy();
                else if (!firstColumn.equals(currentColumn)) throw new AssertionError("Reopened native decoded column changed");
                if (phase == 3 && reload == null) {
                    uploadsBeforeReload = stats.completed.get();
                    reload = mc.reloadResourcePacks(); report.addProperty("nativeResourceReloadRequested", true); return;
                }
                if (reload != null && !reload.isDone()) return;
                if (reload != null && !reloadVerified) {
                    reload.join(); report.addProperty("nativeResourceReloadCompleted", true);
                    currentColumn = null; reloadVerified = true; return;
                }
                if (reloadVerified) {
                    report.addProperty("sourceColumnVerifiedAfterReload", true);
                    report.addProperty("newUsefulUploadsAfterReload", stats.completed.get() - uploadsBeforeReload);
                }
                int queued = queuedRenderTasks();
                if (queued == 0 && stats.pending.get() == 0 && phaseTicks < 600) { save(); return; }
                var before = new JsonObject(); before.addProperty("epoch", epoch); before.addProperty("queuedRenderTasks", queued);
                before.addProperty("pendingNativeUploads", stats.pending.get()); before.addProperty("completedUsefulUploads", stats.completed.get());
                before.addProperty("vertices", stats.vertices.get()); report.add("beforeClose" + epoch, before);
                mc.level.disconnect(); mc.disconnect(new TitleScreen()); mc.setScreen(new TitleScreen());
                phase++; phaseTicks = 0; currentColumn = null;
            } else if ((phase == 2 || phase == 4) && mc.screen instanceof TitleScreen && mc.level == null && mc.getSingleplayerServer() == null) {
                if (SharedApi.getAbstractDhWorld() != null) { save(); return; }
                var stats = uploads.get(epoch); if (stats != null && stats.pending.get() != 0) { save(); return; }
                report.addProperty("nativeWorldRetired" + epoch, true);
                if (phase == 2) { open(mc); phase = 3; phaseTicks = 0; }
                else { report.add("decodedColumn", firstColumn); report.addProperty("complete", true); finish(mc); }
            }
            if (ticks % 20 == 0) save();
        } catch (Throwable failure) {
            report.addProperty("complete", false); report.addProperty("failure", failure.toString()); failure.printStackTrace(); finish(mc);
        }
    }

    private void open(Minecraft mc) {
        epoch++; pending = null; currentColumn = null;
        String world = "dh-native-review";
        if (Files.isDirectory(root.resolve("saves/" + world))) mc.createWorldOpenFlows().openWorld(world, () -> mc.setScreen(new TitleScreen()));
        else mc.createWorldOpenFlows().createFreshLevel(world,
                new LevelSettings("DH native review", GameType.CREATIVE, false, Difficulty.PEACEFUL, true, new GameRules(), WorldDataConfiguration.DEFAULT),
                new WorldOptions(42, false, false), registries -> registries.registryOrThrow(Registries.WORLD_PRESET)
                        .get(ResourceLocation.parse("minecraft:flat")).createWorldDimensions(), mc.screen);
    }

    private void readColumn(Minecraft mc) throws Exception {
        if (pending != null) {
            report.addProperty("sourceReadState", pending.isDone() ? "completed" : "pending native future");
            if (!pending.isDone()) return;
            var future = pending; pending = null;
            try (var source = future.join()) {
                if (source == null || source.isEmpty) { report.addProperty("sourceReadState", "empty native source"); return; }
                var pos = mc.player.blockPosition(); var rows = new JsonArray(); int cells = 0, hiddenDifferences = 0;
                var occupied = new HashSet<Integer>();
                var column = source.getApiDataPointColumn(Math.floorMod(pos.getX(),64), Math.floorMod(pos.getZ(),64));
                report.addProperty("sourceColumnPoints", column.size()); report.addProperty("sourceBlockPosition", pos.toShortString());
                for (var point : column) {
                    if (point.blockStateWrapper.isAir()) continue;
                    int bottom = mc.level.getMinBuildHeight() + point.bottomYBlockPos, top = mc.level.getMinBuildHeight() + point.topYBlockPos;
                    if (bottom < mc.level.getMinBuildHeight() || top > mc.level.getMaxBuildHeight() || bottom >= top)
                        throw new AssertionError("Native client returned an invalid solid interval");
                    for (int y=bottom; y<top; y++) {
                        if (!occupied.add(y)) throw new AssertionError("Native client returned overlapping solid intervals");
                        var at = new net.minecraft.core.BlockPos(pos.getX(),y,pos.getZ()); var actual = mc.level.getBlockState(at);
                        if (actual.isAir()) throw new AssertionError("Native client LOD occupied an actual AIR cell");
                        if (!actual.equals(point.blockStateWrapper.getWrappedMcObject())) {
                            if (Config.Common.LodBuilding.worldCompression.get()
                                    != com.seibel.distanthorizons.api.enums.config.EDhApiWorldCompressionMode.VISUALLY_EQUAL || !actual.canOcclude())
                                throw new AssertionError("Native client changed an unadmitted material at " + at);
                            for (var direction : net.minecraft.core.Direction.values()) {
                                var neighbor = at.relative(direction);
                                if (!mc.level.hasChunk(neighbor.getX() >> 4, neighbor.getZ() >> 4)
                                        || !mc.level.getBlockState(neighbor).canOcclude())
                                    throw new AssertionError("Native client changed an exposed or unverified material at " + at);
                            }
                            hiddenDifferences++;
                        }
                        cells++;
                    }
                    var row = new JsonObject(); row.addProperty("bottomY",bottom); row.addProperty("topY",top);
                    row.addProperty("block",point.blockStateWrapper.getSerialString()); row.addProperty("biome",point.biomeWrapper.getSerialString()); rows.add(row);
                }
                for (int y=mc.level.getMinBuildHeight();y<mc.level.getMaxBuildHeight();y++)
                    if (mc.level.getBlockState(new net.minecraft.core.BlockPos(pos.getX(),y,pos.getZ())).isAir() == occupied.contains(y))
                        throw new AssertionError("Native client LOD occupancy differs from its actual source at Y="+y);
                report.addProperty("sourceReadState", cells > 0 ? "matched solid material" : "no solid material at requested column");
                report.addProperty("nativeHiddenMaterialDifferences" + epoch, hiddenDifferences);
                if (cells > 0) { currentColumn = rows; report.addProperty("matchedSourceCells"+epoch,cells); }
            }
        } else if (ticks % 20 == 0) {
            var world = SharedApi.tryGetDhClientWorld();
            report.addProperty("sourceReadState", world == null ? "no native client world" : "finding actual client-level owner");
            if (world == null) return;
            report.addProperty("nativeClientWorldClass", world.getClass().getName());
            var owners = new JsonArray();
            for (var level : world.getAllLoadedLevels()) {
                var owner = new JsonObject(); owner.addProperty("class", level.getClass().getName());
                if (level instanceof IDhClientLevel client) {
                    var wrapper = client.getClientLevelWrapper();
                    owner.addProperty("clientWrapperPresent", wrapper != null);
                    owner.addProperty("currentClientWorld", wrapper != null && wrapper.getWrappedMcObject() == mc.level);
                    owner.addProperty("actualLevelOwner", ownsCurrentLevel(client, mc));
                    owner.addProperty("nativeLevelId", level.getLevelWrapper().getDhIdentifier());
                }
                owners.add(owner);
            }
            report.add("nativeLevelOwners", owners);
            for (var level : world.getAllLoadedLevels()) if (level instanceof IDhClientLevel client && ownsCurrentLevel(client, mc)) {
                var pos=mc.player.chunkPosition();
                pending=client.getFullDataProvider().getAsync(DhSectionPos.encodeContaining(DhSectionPos.SECTION_BLOCK_DETAIL_LEVEL,new DhChunkPos(pos.x,pos.z)));
                report.addProperty("sourceReadState", "requested actual native source");
                return;
            }
        }
    }

    private void finish(Minecraft mc) {
        finished = true;
        if (pending != null) { pending.whenComplete((source,failure)->{if(source!=null)source.close();}); pending=null; }
        save(); if (automationAllowed()) mc.stop();
    }
    private boolean automationAllowed() {
        try {
            var control=JsonParser.parseString(Files.readString(root.resolve("dh-client-control.json"))).getAsJsonObject();
            return !control.get("paused").getAsBoolean() && System.currentTimeMillis()<control.get("validUntilEpochMillis").getAsLong();
        } catch (Exception unavailable) { return false; }
    }
    private void save() {
        if (ticks % 20 != 0 && !finished) return;
        report.addProperty("ticks",ticks); report.addProperty("phase",phase); report.addProperty("epoch",epoch);
        var mc = Minecraft.getInstance();
        report.addProperty("screen", mc.screen == null ? "none" : mc.screen.getClass().getName());
        report.addProperty("overlay", mc.getOverlay() == null ? "none" : mc.getOverlay().getClass().getName());
        report.addProperty("servicesInsideNativeClose",servicesDuringClose.get());
        var states = new JsonObject(); uploads.forEach((key,value)->{
            var row=new JsonObject();row.addProperty("queued",value.queued.get());row.addProperty("pending",value.pending.get());
            row.addProperty("usefulCompleted",value.completed.get());row.addProperty("failed",value.failed.get());row.addProperty("vertices",value.vertices.get());states.add(key.toString(),row);
        });report.add("uploads",states);var closes=new JsonArray();nativeCloses.forEach(closes::add);report.add("nativeCloses",closes);
        try { Files.writeString(root.resolve("dh-client-proof.json"),new GsonBuilder().setPrettyPrinting().create().toJson(report)+"\n"); }
        catch (Exception failure) { throw new IllegalStateException("Cannot retain native DH client proof",failure); }
    }
}
