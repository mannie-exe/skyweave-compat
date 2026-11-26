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
import java.lang.ref.WeakReference;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.screens.BackupConfirmScreen;
import net.minecraft.client.gui.screens.ReceivingLevelScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.network.chat.Component;
import net.minecraft.core.BlockPos;
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
    private static final ThreadLocal<Integer> WORLD_CLOSE_DEPTH = ThreadLocal.withInitial(() -> 0);
    private final Path root = FMLPaths.GAMEDIR.get();
    private final JsonObject report = new JsonObject();
    private final ConcurrentHashMap<Integer, Uploads> uploads = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AtomicInteger> usefulByNativeLevel = new ConcurrentHashMap<>();
    private final ConcurrentLinkedQueue<JsonObject> nativeCloses = new ConcurrentLinkedQueue<>();
    private final AtomicInteger servicesDuringClose = new AtomicInteger();
    private final AtomicInteger servicesDuringWorldClose = new AtomicInteger();
    private record LevelEpoch(WeakReference<IDhClientLevel> level, int epoch) {}
    private final CopyOnWriteArrayList<LevelEpoch> enrolledLevels = new CopyOnWriteArrayList<>();
    private final AtomicInteger lateNativeEntries = new AtomicInteger();
    private final ClientDhWorkers workers = Boolean.getBoolean("skyweave_compat.probe.dhWorkers") ? new ClientDhWorkers() : null;
    private final ClientDhNetwork network = new ClientDhNetwork();
    private volatile int epoch;
    private int ticks, phase, phaseTicks;
    private boolean finished;
    private boolean closeQueued;
    private int idleCloseTicks;
    private long resizeContext;
    private int resizeWidth, resizeHeight;
    private boolean resizeVerified;
    private final Integer tcpPort = Integer.getInteger("skyweave_compat.probe.dhTcpPort");
    private final ClientDhDimensionJourney dimensionJourney = Boolean.getBoolean("skyweave_compat.probe.dhDimensions")
            ? new ClientDhDimensionJourney() : null;
    private final ClientDhFieldProbe field = Boolean.getBoolean("skyweave_compat.probe.dhField") ? new ClientDhFieldProbe() : null;
    private final ClientDhRuntimeState runtime = Boolean.getBoolean("skyweave_compat.probe.dhRuntimeState") ? new ClientDhRuntimeState() : null;
    private WeakReference<ClientLevel> observedSourceLevel;
    private String currentNativeLevelId;
    private CompletableFuture<FullDataSourceV2> pending;
    private BlockPos pendingSample;
    private WeakReference<ClientLevel> pendingLevel;
    private long pendingSection;
    private int pendingEpoch, pendingMinY, physicalChunkWaits;
    private CompletableFuture<Void> reload;
    private boolean reloadVerified;
    private int uploadsBeforeReload;
    private JsonArray firstColumn;
    private JsonArray currentColumn;
    private volatile String observerFailure;
    private final ClientDhGpuResources gpu = Boolean.getBoolean("skyweave_compat.probe.dhResources") ? new ClientDhGpuResources() : null;
    private final ClientDhCpuResources cpu = Boolean.getBoolean("skyweave_compat.probe.dhCpuResources") ? new ClientDhCpuResources() : null;

    private static final class Uploads {
        final AtomicInteger queued = new AtomicInteger(), pending = new AtomicInteger(), completed = new AtomicInteger(), failed = new AtomicInteger();
        final AtomicLong vertices = new AtomicLong();
    }

    ClientDhLifecycleProbe() {
        try {
            var marker = JsonParser.parseString(Files.readString(root.resolve("dh-client-fixture.json"))).getAsJsonObject();
            if (!marker.get("owner").getAsString().equals("skyweave-dh-client")) throw new IllegalStateException("Wrong client fixture owner");
            if (System.getProperty("skyweave_compat.probe.dhTcpPort") != null && tcpPort == null)
                throw new IllegalStateException("Invalid explicit loopback port");
            if (tcpPort != null) {
                if (tcpPort < 1024 || tcpPort > 65535 || !marker.has("tcpPort") || marker.get("tcpPort").getAsInt() != tcpPort)
                    throw new IllegalStateException("Loopback port is not admitted by this fixture");
                var serverRoot = Path.of(marker.get("tcpServerRuntime").getAsString()).toRealPath();
                var serverMarker = JsonParser.parseString(Files.readString(serverRoot.resolve("owned-startup-fixture.json"))).getAsJsonObject();
                if (!serverMarker.get("owner").getAsString().equals("startup_probe.py")
                        || !serverMarker.get("artifactSha256").getAsString().equals(marker.get("productSha256").getAsString()))
                    throw new IllegalStateException("Loopback server does not belong to the matching marked product fixture");
            }
        } catch (Exception failure) { throw new IllegalStateException("Refusing DH lifecycle outside its marked disposable client", failure); }
        report.addProperty("scope", "Actual native client DH data, GPU upload futures, disconnect/reopen and reload; marked integrated or loopback transport, no copied renderer or synthetic uploads");
        report.addProperty("transport", tcpPort == null ? "integrated" : "native TCP to marked loopback server");
        active = this; NeoForge.EVENT_BUS.addListener(this::tick);
    }

    public static void uploaded(IDhClientLevel level, CompletableFuture<LodBufferContainer> future) {
        var probe = active; if (probe == null || probe.finished || future == null) return;
        var mc = Minecraft.getInstance();
        int epoch = probe.levelEpoch(level, mc); if (epoch == 0) return;
        var levelUseful=probe.usefulByNativeLevel.computeIfAbsent(epoch+":"+level.getLevelWrapper().getDhIdentifier(),
                ignored -> new AtomicInteger());
        var stats = probe.uploads.computeIfAbsent(epoch, ignored -> new Uploads());
        stats.queued.incrementAndGet(); stats.pending.incrementAndGet();
        // Retain only primitive counters; do not keep the old world, section, future or GPU container alive.
        future.whenComplete((container, failure) -> {
            try {
                if (failure != null) stats.failed.incrementAndGet();
                else if (container != null && container.buffersUploaded) {
                    if (probe.gpu != null) {
                        probe.gpu.uploaded(epoch, container.vboOpaqueWrappers);
                        probe.gpu.uploaded(epoch, container.vboTransparentWrappers);
                    }
                    long vertices = vertices(container.vboOpaqueWrappers) + vertices(container.vboTransparentWrappers);
                    if (vertices > 0) {
                        stats.completed.incrementAndGet(); stats.vertices.addAndGet(vertices); levelUseful.incrementAndGet();
                        if (probe.field!=null) probe.field.uploaded(epoch,container.pos);
                    }
                }
            } catch (Throwable failureInObserver) { probe.observerFailure = failureInObserver.toString(); }
            finally { stats.pending.decrementAndGet(); }
        });
    }
    private static long vertices(com.seibel.distanthorizons.core.wrapperInterfaces.render.objects.IVertexBufferWrapper[] buffers) {
        long count = 0; if (buffers != null) for (var buffer : buffers) if (buffer != null) count += buffer.getVertexCount(); return count;
    }
    public static void deletedGpuBuffer(int id) {
        var probe = active;
        if (probe == null || probe.gpu == null) return;
        try { probe.gpu.nativeDeleted(id); }
        catch (Throwable failure) { probe.observerFailure = failure.toString(); }
    }
    public static long enterCpuFrame(IDhClientLevel level) {
        var probe = active;
        if (probe == null || probe.cpu == null) return 0;
        return probe.cpu.enter(!probe.finished ? probe.levelEpoch(level, Minecraft.getInstance()) : 0);
    }
    public static void leaveCpuFrame(long id) {
        var probe = active; if (id == 0 || probe == null || probe.cpu == null) return;
        try { probe.cpu.leave(id); } catch (Throwable failure) { probe.observerFailure = failure.toString(); }
    }
    public static void cpuBorrowed(String pool, com.seibel.distanthorizons.core.util.objects.pooling.PhantomArrayList.PhantomArrayListCheckout checkout) {
        var probe = active; if (probe != null && probe.cpu != null) probe.cpu.borrowed(pool, checkout);
    }
    public static Object cpuReturning(com.seibel.distanthorizons.core.util.objects.pooling.PhantomArrayList.PhantomArrayListCheckout checkout) {
        var probe = active; return probe == null || probe.cpu == null ? null : probe.cpu.returning(checkout);
    }
    public static void cpuReturned(Object observation) {
        var probe = active; if (observation != null && probe != null && probe.cpu != null) probe.cpu.returned(observation);
    }
    public static void afterActualCpuJoin(Object value) {
        var probe = active; if (probe != null && probe.cpu != null) probe.cpu.afterActualJoin(value);
    }
    public static void nativeCpuUpload(CompletableFuture<?> result) {
        var probe = active; if (probe != null && probe.cpu != null) probe.cpu.nativeUpload(result);
    }
    public static void afterActualVertexUpload(CompletableFuture<?> result,
            com.seibel.distanthorizons.core.wrapperInterfaces.render.objects.IVertexBufferWrapper buffer) {
        var probe = active;
        if (probe == null || probe.cpu == null || buffer.getVertexCount() <= 0) return;
        int epoch = probe.cpu.claimUploadFault(result); if (epoch == 0) return;
        if (probe.gpu != null) probe.gpu.uploaded(epoch,
                new com.seibel.distanthorizons.core.wrapperInterfaces.render.objects.IVertexBufferWrapper[]{buffer});
        // Throw inside the real child task only after its native upload call returns.
        // The native catch/allOf/close pipeline, not this observer, owns settlement.
        throw new IllegalStateException("Skyweave disposable native vertex-upload child fault");
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
    private synchronized int levelEpoch(IDhClientLevel level, Minecraft mc) {
        for (var enrolled : enrolledLevels) if (enrolled.level().get() == level) {
            if (!ownsCurrentLevel(level, mc)) lateNativeEntries.incrementAndGet();
            return enrolled.epoch();
        }
        if (!ownsCurrentLevel(level, mc) || epoch == 0) return 0;
        enrolledLevels.add(new LevelEpoch(new WeakReference<>(level), epoch)); return epoch;
    }
    public static long closing(com.seibel.distanthorizons.core.util.threading.PriorityTaskPicker picker) {
        var probe = active;
        if (probe != null && probe.workers != null) try { probe.workers.capture(picker); }
        catch (Throwable failure) { probe.observerFailure = failure.toString(); }
        long start = System.nanoTime(); CLOSES.get().push(start); return start;
    }
    public static void closed(com.seibel.distanthorizons.core.util.threading.PriorityTaskPicker picker, long start, Throwable failure) {
        var probe = active; if (!CLOSES.get().isEmpty()) CLOSES.get().pop();
        if (probe == null) return;
        var row = new JsonObject(); row.addProperty("observerPhaseEpoch", probe.epoch); row.addProperty("pickerIdentity", System.identityHashCode(picker)); row.addProperty("thread", Thread.currentThread().getName());
        if (probe.workers != null) try { row.addProperty("sharedPickerGeneration", probe.workers.capture(picker)); }
        catch (Throwable error) { probe.observerFailure = error.toString(); }
        row.addProperty("elapsedNanos", System.nanoTime() - start); row.addProperty("nativeReturned", failure == null);
        if (failure != null) row.addProperty("failure", failure.toString()); probe.nativeCloses.add(row);
    }
    public static void serviced() {
        var probe = active;
        if (probe != null && !CLOSES.get().isEmpty()) probe.servicesDuringClose.incrementAndGet();
        if (probe != null && WORLD_CLOSE_DEPTH.get() > 0) probe.servicesDuringWorldClose.incrementAndGet();
    }
    public static void worldClosing() { WORLD_CLOSE_DEPTH.set(WORLD_CLOSE_DEPTH.get()+1); }
    public static void worldClosed() { int depth=WORLD_CLOSE_DEPTH.get()-1; if(depth==0) WORLD_CLOSE_DEPTH.remove(); else WORLD_CLOSE_DEPTH.set(depth); }
    public static void nativeFrame(String event) { var probe=active; if(probe!=null && probe.runtime!=null)probe.runtime.event(probe.epoch,event); }
    public static void nativeRenderService(long budget) { var probe=active; if(probe!=null && probe.runtime!=null)probe.runtime.nativeService(probe.epoch,budget); }
    public static Object networkClosing(com.seibel.distanthorizons.core.multiplayer.client.AbstractFullDataNetworkRequestQueue queue, CompletableFuture<Void> prior, boolean interrupt) {
        var probe = active; return probe == null ? null : probe.network.closing(queue, prior, interrupt);
    }
    public static void networkCloseRequested(Object call, CompletableFuture<Void> future) {
        var probe = active; if (probe != null && call != null) probe.network.requested((ClientDhNetwork.Call)call, future);
    }
    public static void networkSplitReceived() { var probe=active; if(probe!=null) probe.network.splitMessages.incrementAndGet(); }
    public static void networkPayloadDecoded() { var probe=active; if(probe!=null) probe.network.decodedPayloads.incrementAndGet(); }
    public static void networkLateSubmission(CompletableFuture<?> future) { var probe=active; if(probe!=null) probe.network.lateSubmission(future); }
    @SuppressWarnings("unchecked") private static int queuedRenderTasks() throws ReflectiveOperationException {
        var field = RenderThreadTaskHandler.class.getDeclaredField("RENDER_THREAD_RUNNABLE_QUEUE"); field.setAccessible(true);
        return ((Queue<?>)field.get(null)).size();
    }

    private void tick(ClientTickEvent.Post event) {
        if (finished || !automationAllowed()) return;
        var mc = Minecraft.getInstance();
        try {
            if (observerFailure != null) throw new AssertionError("Native upload observer failed: " + observerFailure);
            if (dimensionJourney!=null) dimensionJourney.checkFailure();
            if (++ticks > Integer.getInteger("skyweave_compat.probe.dhLifecycleMaxTicks", 7200))
                throw new AssertionError("Native client lifecycle observation exceeded its disposable window");
            phaseTicks++;
            if (mc.getOverlay() != null) return;
            if (closeQueued) return;
            if (tcpPort != null && (phase == 1 || phase == 3) && mc.screen instanceof DisconnectedScreen)
                throw new AssertionError("Native loopback connection was refused or disconnected before useful data");
            if (phase == 3 && mc.screen instanceof BackupConfirmScreen
                    && mc.screen.getTitle().getString().equals(Component.translatable("selectWorld.backupQuestion.experimental").getString())) {
                if (epoch != 2 || !Files.isDirectory(root.resolve("saves/dh-native-review"))
                        || report.has("nativeExperimentalReopenConfirmed"))
                    throw new AssertionError("Unexpected repeated experimental-world confirmation");
                if (mc.screen.children().stream().anyMatch(child -> child instanceof Checkbox checkbox && checkbox.selected()))
                    throw new AssertionError("Refusing cache erasure in the native reopen fixture");
                String skip = Component.translatable("selectWorld.backupJoinSkipButton").getString();
                var button = mc.screen.children().stream().filter(child -> child instanceof Button nativeButton
                        && nativeButton.active && nativeButton.getMessage().getString().equals(skip)).findFirst();
                if (button.isEmpty()) throw new AssertionError("Native experimental-world choice is unavailable");
                report.addProperty("nativeExperimentalReopenTitle", mc.screen.getTitle().getString());
                report.addProperty("nativeExperimentalReopenChoice", skip);
                report.addProperty("nativeExperimentalReopenConfirmed", true);
                ((Button)button.get()).onPress(); save(); return;
            }
            if (phase == 0 && mc.screen instanceof TitleScreen) {
                mc.options.pauseOnLostFocus = false;
                Config.Client.Advanced.Graphics.Quality.lodChunkRenderDistanceRadius.set(32);
                Config.Common.MultiThreading.numberOfThreads.set(4);
                Config.Common.MultiThreading.threadRunTimeRatio.set(1.0);
                report.addProperty("effectiveLodRadius", Config.Client.Advanced.Graphics.Quality.lodChunkRenderDistanceRadius.get());
                report.addProperty("effectiveDhThreads", Config.Common.MultiThreading.numberOfThreads.get());
                open(mc); phase = 1; phaseTicks = 0;
            } else if ((phase == 1 || phase == 3) && mc.level != null && mc.player != null) {
                if (tcpPort != null) {
                    var connection = mc.getConnection() == null ? null : mc.getConnection().getConnection();
                    if (mc.getSingleplayerServer() != null || connection == null || connection.isMemoryConnection()
                            || !(connection.getRemoteAddress() instanceof java.net.InetSocketAddress endpoint)
                            || !endpoint.getAddress().isLoopbackAddress() || endpoint.getPort() != tcpPort)
                        throw new AssertionError("Native connection is not the admitted loopback TCP server");
                    report.addProperty("verifiedNativeTcpEpoch" + epoch, true);
                }
                var physical = mc.player.chunkPosition();
                if (mc.screen instanceof ReceivingLevelScreen || !mc.level.getChunkSource().hasChunk(physical.x, physical.z)) {
                    report.addProperty("physicalClientChunkWaits" + epoch, ++physicalChunkWaits);
                    report.addProperty("sourceReadState", "waiting for delivered physical client chunks");
                    save(); return;
                }
                if (Boolean.getBoolean("skyweave_compat.probe.dhBackgroundGate") && epoch==2) {
                    report.addProperty("nativeBackgroundGateReadyEpoch",epoch);
                    var gate=root.resolve("dh-background-continue.txt");
                    if (!Files.isRegularFile(gate)) { save();return; }
                    if (!Files.readString(gate).equals("continue epoch2\n"))
                        throw new AssertionError("Unexpected background fixture handshake");
                    report.addProperty("nativeBackgroundGateReleased",true);
                }
                if (gpu != null) gpu.world(epoch, mc.level, SharedApi.getAbstractDhWorld());
                if (observedSourceLevel==null || observedSourceLevel.get()!=mc.level) {
                    // A pending native read still owns its result; retire only this observation.
                    if (pending!=null) pending.whenComplete((source,failure)->{if(source!=null)source.close();});
                    pending=null; pendingSample=null; pendingLevel=null; currentColumn=null; currentNativeLevelId=null;
                    observedSourceLevel=new WeakReference<>(mc.level);
                }
                if (currentColumn == null) readColumn(mc);
                var stats = uploads.get(epoch);
                if (currentColumn == null || stats == null || stats.completed.get() == 0) { save(); return; }
                if (dimensionJourney!=null && !dimensionJourney.ready(mc,currentColumn,currentNativeLevelId,
                        usefulByNativeLevel.getOrDefault(epoch+":"+currentNativeLevelId,new AtomicInteger()).get(),report)) {
                    save(); return;
                }
                if (Boolean.getBoolean("skyweave_compat.probe.dhResize") && !resizeVerified) {
                    long window=mc.getWindow().getWindow();
                    if (!mc.isSameThread() || org.lwjgl.glfw.GLFW.glfwGetCurrentContext()!=window)
                        throw new AssertionError("Native resize lost its actual render context");
                    int[] width={0},height={0}; org.lwjgl.glfw.GLFW.glfwGetWindowSize(window,width,height);
                    if (resizeContext==0) {
                        resizeContext=window; resizeWidth=width[0]+64; resizeHeight=height[0]+32;
                        report.addProperty("nativeResizeOriginalWidth",width[0]);
                        report.addProperty("nativeResizeOriginalHeight",height[0]);
                        org.lwjgl.glfw.GLFW.glfwSetWindowSize(window,resizeWidth,resizeHeight);
                        return;
                    }
                    if (resizeContext!=window) throw new AssertionError("Native resize replaced the admitted context");
                    if (width[0]!=resizeWidth || height[0]!=resizeHeight || mc.getWindow().getWidth()<=0 || mc.getWindow().getHeight()<=0)
                        return;
                    resizeVerified=true; currentColumn=null;
                    report.addProperty("nativeResizeVerified",true);
                    report.addProperty("nativeResizeWidth",width[0]); report.addProperty("nativeResizeHeight",height[0]);
                    report.addProperty("nativeResizeContextPreserved",true); return;
                }
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
                if (field!=null && !field.ready(mc,epoch,report)) { save(); return; }
                int queued = queuedRenderTasks();
                if (Boolean.getBoolean("skyweave_compat.probe.dhIdleClose")) {
                    idleCloseTicks=queued==0 && stats.pending.get()==0 ? idleCloseTicks+1 : 0;
                    report.addProperty("idleCloseConsecutiveTicks"+epoch,idleCloseTicks);
                    if (idleCloseTicks<20) { save(); return; }
                } else if (queued == 0 && stats.pending.get() == 0 && phaseTicks < 600) { save(); return; }
                var before = new JsonObject(); before.addProperty("epoch", epoch); before.addProperty("queuedRenderTasks", queued);
                before.addProperty("pendingNativeUploads", stats.pending.get()); before.addProperty("completedUsefulUploads", stats.completed.get());
                before.addProperty("vertices", stats.vertices.get()); report.add("beforeClose" + epoch, before);
                closeQueued = true;
                var closingLevel = new WeakReference<>(mc.level); int closingEpoch = epoch;
                // Native disconnect can pump its own loading screen. Leave this listener first
                // so later ClientTick.Post listeners do not run across our synchronous teardown.
                mc.tell(() -> {
                    closeQueued = false;
                    if (!automationAllowed() || finished) return;
                    try {
                        if (mc.level == null || mc.level != closingLevel.get() || epoch != closingEpoch)
                            throw new AssertionError("Deferred native disconnect lost its admitted level");
                        boolean stillInObserverTick = StackWalker.getInstance().walk(frames -> frames.anyMatch(frame ->
                                frame.getClassName().equals(ClientDhLifecycleProbe.class.getName()) && frame.getMethodName().equals("tick")));
                        if (stillInObserverTick) throw new AssertionError("Native disconnect was not deferred out of the observer tick");
                        report.addProperty("nativeDisconnectDeferredOutsideObserverTick", true);
                        phase++; phaseTicks = 0; currentColumn = null;
                        mc.level.disconnect(); mc.disconnect(new TitleScreen()); mc.setScreen(new TitleScreen());
                    } catch (Throwable failure) {
                        report.addProperty("complete", false); report.addProperty("failure", failure.toString());
                        failure.printStackTrace(); finish(mc);
                    }
                });
            } else if ((phase == 2 || phase == 4) && mc.screen instanceof TitleScreen && mc.level == null && mc.getSingleplayerServer() == null) {
                if (SharedApi.getAbstractDhWorld() != null) { save(); return; }
                var stats = uploads.get(epoch); if (stats != null && stats.pending.get() != 0) { save(); return; }
                if (gpu != null && !gpu.retired(epoch)) { save(); return; }
                if (cpu != null && !cpu.retired(epoch)) { save(); return; }
                if (workers != null && !workers.snapshot().get("retired").getAsBoolean()) { save(); return; }
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
        epoch++; pending = null; pendingSample = null; pendingLevel = null; physicalChunkWaits = 0; currentColumn = null;
        idleCloseTicks=0;
        observedSourceLevel=null; currentNativeLevelId=null;
        if (tcpPort != null) {
            String address = "127.0.0.1:" + tcpPort;
            var data = new ServerData("Skyweave disposable DH loopback", address, ServerData.Type.OTHER);
            mc.tell(() -> {
                if (!automationAllowed() || finished) return;
                ConnectScreen.startConnecting(new TitleScreen(), mc, ServerAddress.parseString(address), data, false, null);
            });
            return;
        }
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
            var sample = pendingSample; pendingSample = null;
            var owner = pendingLevel; pendingLevel = null;
            try (var source = future.join()) {
                if (owner == null || owner.get() != mc.level || pendingEpoch != epoch || sample == null
                        || mc.player.blockPosition().getX() != sample.getX() || mc.player.blockPosition().getZ() != sample.getZ()
                        || !mc.level.getChunkSource().hasChunk(sample.getX() >> 4, sample.getZ() >> 4)) {
                    report.addProperty("sourceReadState", "retired observation after client source or position changed"); return;
                }
                if (source == null || source.isEmpty) { report.addProperty("sourceReadState", "empty native source"); return; }
                if (source.getPos() != pendingSection || mc.level.getMinBuildHeight() != pendingMinY)
                    throw new AssertionError("Native readback crossed its requested section or height frame");
                var pos = sample; var rows = new JsonArray(); int cells = 0, hiddenDifferences = 0;
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
                        if (actual.isAir()) throw new AssertionError("Native client LOD occupied actual AIR at " + at);
                        if (!actual.equals(point.blockStateWrapper.getWrappedMcObject())) {
                            if (Config.Common.LodBuilding.worldCompression.get()
                                    != com.seibel.distanthorizons.api.enums.config.EDhApiWorldCompressionMode.VISUALLY_EQUAL || !actual.canOcclude())
                                throw new AssertionError("Native client changed an unadmitted material at " + at);
                            for (var direction : net.minecraft.core.Direction.values()) {
                                var neighbor = at.relative(direction);
                                if (!mc.level.getChunkSource().hasChunk(neighbor.getX() >> 4, neighbor.getZ() >> 4)
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
                currentNativeLevelId=level.getLevelWrapper().getDhIdentifier();
                var pos=mc.player.chunkPosition();
                pendingSample = mc.player.blockPosition().immutable(); pendingLevel = new WeakReference<>(mc.level);
                pendingSection = DhSectionPos.encodeContaining(DhSectionPos.SECTION_BLOCK_DETAIL_LEVEL,new DhChunkPos(pos.x,pos.z));
                pendingEpoch = epoch; pendingMinY = mc.level.getMinBuildHeight();
                report.addProperty("requestedSourcePosition" + epoch, pendingSample.toShortString());
                report.addProperty("requestedSourceSection" + epoch, pendingSection);
                report.addProperty("requestedSourceMinY" + epoch, pendingMinY);
                pending=client.getFullDataProvider().getAsync(pendingSection);
                report.addProperty("sourceReadState", "requested actual native source");
                return;
            }
        }
    }

    private void finish(Minecraft mc) {
        if (finished) return;
        if(report.has("nativeRuntimeState"))report.add("runtimeStateBeforeFinish",report.get("nativeRuntimeState").deepCopy());
        finished = true;
        if (field!=null) field.retire();
        if (pending != null) { pending.whenComplete((source,failure)->{if(source!=null)source.close();}); pending=null; }
        pendingSample = null; pendingLevel = null;
        save();
        if (automationAllowed()) {
            // Failure teardown must use the same complete native disconnect outside this tick.
            // Preserve the failed result; successful cleanup never turns a refusal into a pass.
            if (mc.level!=null) mc.tell(() -> {
                if (!automationAllowed()) return;
                try {
                    report.addProperty("fixtureNativeDisconnectRequested",true);
                    if(mc.level!=null)mc.level.disconnect();
                    mc.disconnect(new TitleScreen());mc.setScreen(new TitleScreen());
                    report.addProperty("fixtureNativeDisconnectCompleted",true);
                } catch(Throwable failure) { report.addProperty("fixtureCleanupFailure",failure.toString()); }
                finally { save();mc.stop(); }
            });
            else mc.stop();
        }
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
        report.addProperty("servicesInsideNativeWorldClose",servicesDuringWorldClose.get());
        if(runtime!=null) try {
            // Observe after native service/producer ingress, without starting its timer early.
            report.add("nativeRuntimeState",runtime.snapshot(mc,
                    !uploads.isEmpty() || runtime.nativeServiceObserved() ? queuedRenderTasks() : null));
            report.getAsJsonObject("nativeRuntimeState").addProperty("physicalDataReadPending",pending!=null && !pending.isDone());
            if(field!=null)report.getAsJsonObject("nativeRuntimeState").add("farDataRead",field.pendingObservation());
        }
        catch(Throwable failure) { report.addProperty("runtimeStateObservationFailure",failure.toString()); }
        if (gpu != null) report.add("nativeGpuResources", gpu.snapshot());
        if (cpu != null) report.add("nativeCpuResources", cpu.snapshot());
        if (workers != null) try { report.add("nativeWorkers", workers.snapshot()); }
        catch (Throwable error) { observerFailure = error.toString(); }
        report.addProperty("lateEnrolledNativeEntries", lateNativeEntries.get());
        report.addProperty("weakEnrolledNativeLevels", enrolledLevels.size());
        report.add("nativeNetwork", network.snapshot());
        var states = new JsonObject(); uploads.forEach((key,value)->{
            var row=new JsonObject();row.addProperty("queued",value.queued.get());row.addProperty("pending",value.pending.get());
            row.addProperty("usefulCompleted",value.completed.get());row.addProperty("failed",value.failed.get());row.addProperty("vertices",value.vertices.get());states.add(key.toString(),row);
        });report.add("uploads",states);var closes=new JsonArray();nativeCloses.forEach(closes::add);report.add("nativeCloses",closes);
        try { Files.writeString(root.resolve("dh-client-proof.json"),new GsonBuilder().setPrettyPrinting().create().toJson(report)+"\n"); }
        catch (Exception failure) { throw new IllegalStateException("Cannot retain native DH client proof",failure); }
    }
}
