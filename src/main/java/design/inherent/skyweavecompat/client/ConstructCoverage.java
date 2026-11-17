package design.inherent.skyweavecompat.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.seibel.distanthorizons.api.DhApi;
import com.seibel.distanthorizons.api.enums.rendering.EDhApiRendererMode;
import com.seibel.distanthorizons.core.level.IDhClientLevel;
import com.seibel.distanthorizons.core.pos.DhSectionPos;
import com.seibel.distanthorizons.core.render.RenderBufferHandler;
import com.seibel.distanthorizons.core.wrapperInterfaces.world.IClientLevelWrapper;
import design.inherent.skyweavecompat.rendering.CoverageCarrier;
import design.inherent.skyweavecompat.rendering.CoverageColumns;
import design.inherent.skyweavecompat.rendering.CoverageFootprint;
import design.inherent.skyweavecompat.rendering.CoverageRequests;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import java.util.Iterator;
import java.util.NoSuchElementException;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.phys.Vec3;

/** Loaded only for the pinned optional DH/Sable/SSRD client combination. No data retrieval or chunk creation. */
public final class ConstructCoverage {
    private enum Diagnostic { NO_PUBLISHED_SECTION, PENDING_BUFFER, OWNER_MISMATCH, INCOMPLETE_COLUMN, NATIVE_SECTION_UNMESHED, INCOMPLETE_OR_DEFERRED_REQUEST }
    private static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();
    private static final java.util.EnumSet<Diagnostic> REPORTED = java.util.EnumSet.noneOf(Diagnostic.class);
    private static final CoverageRequests REQUESTS = new CoverageRequests(128, 4096, 8192);
    private static ClientLevel level;
    private static IDhClientLevel dhLevel;
    private static RenderBufferHandler renderer;
    private static Object context;
    private static long frame;
    private static long preparedFrame = -1;
    private static Vec3 lastCamera;
    private ConstructCoverage() {}

    public static void beginFrame() {
        frame++;
        var mc = Minecraft.getInstance(); var camera = mc.gameRenderer.getMainCamera().getPosition();
        if (lastCamera != null && lastCamera.distanceToSqr(camera) > 128 * 128) clear();
        lastCamera = camera;
    }

    public static void clear() {
        REQUESTS.clear(); level = null; dhLevel = null; renderer = null; context = null; preparedFrame = -1; lastCamera = null;
        REPORTED.clear();
    }

    /** Orthographic shadow and preview submissions retain the original iterable and geometry. */
    public static Iterable<ClientSubLevel> filter(Iterable<ClientSubLevel> levels) {
        if (Math.abs(RenderSystem.getProjectionMatrix().m33()) >= .5f) return levels;
        return () -> new Iterator<>() {
            private final Iterator<ClientSubLevel> source = levels.iterator();
            private ClientSubLevel next;
            private boolean found;
            private void find() { while (!found && source.hasNext()) { var value = source.next(); if (allows(value)) { next = value; found = true; } } }
            @Override public boolean hasNext() { find(); return found; }
            @Override public ClientSubLevel next() { find(); if (!found) throw new NoSuchElementException(); found = false; return next; }
        };
    }

    public static boolean allows(ClientSubLevel construct) {
        var mc = Minecraft.getInstance();
        if (mc.level == null || construct.isRemoved()) return false;
        var camera = mc.gameRenderer.getMainCamera().getPosition(); var box = construct.boundingBox();
        if (!Double.isFinite(camera.x) || !Double.isFinite(camera.y) || !Double.isFinite(camera.z)) return false;
        if (!coordinate(box.minX()) || !coordinate(box.minY()) || !coordinate(box.minZ())
                || !coordinate(box.maxX()) || !coordinate(box.maxY()) || !coordinate(box.maxZ())) return false;
        if (box.minX() > box.maxX() || box.minY() > box.maxY() || box.minZ() > box.maxZ()) return false;
        double dx = Math.max(Math.abs(camera.x - box.minX()), Math.abs(camera.x - box.maxX()));
        double dy = Math.max(Math.abs(camera.y - box.minY()), Math.abs(camera.y - box.maxY()));
        double dz = Math.max(Math.abs(camera.z - box.minZ()), Math.abs(camera.z - box.maxZ()));
        double near = Math.max(16, mc.options.getEffectiveRenderDistance() * 16 - 16);
        if (dx * dx + dy * dy + dz * dz <= near * near) return true;
        prepare();
        var bounds = new CoverageRequests.Bounds(cell(camera.x), cell(camera.z), cell(box.minX()), cell(box.minZ()),
                cell(box.maxX()), cell(box.maxZ()), cell(Math.min(camera.y, box.minY())) * 16 - 16,
                cell(Math.max(camera.y, box.maxY())) * 16 + 31);
        boolean ready = REQUESTS.ready(construct.getUniqueId(), bounds, ConstructCoverage::read);
        if (!ready) diagnostic(Diagnostic.INCOMPLETE_OR_DEFERRED_REQUEST, bounds.cameraX(), bounds.cameraZ());
        return ready;
    }

    private static boolean coordinate(double value) { return Double.isFinite(value) && Math.abs(value) <= 30_000_000; }
    private static int cell(double value) { return (int) Math.floor(value / 16); }
    private static void diagnostic(Diagnostic reason, int x, int z) {
        if (LOGGER.isDebugEnabled() && REPORTED.add(reason)) LOGGER.debug("Construct coverage {} at cell {},{}; draw withheld, no discovery/load requested", reason, x, z);
    }

    private static void prepare() {
        if (preparedFrame == frame) return;
        preparedFrame = frame;
        var mc = Minecraft.getInstance();
        IDhClientLevel current = null; RenderBufferHandler handler = null;
        if (DhApi.Delayed.worldProxy != null && DhApi.Delayed.configs != null && DhApi.Delayed.worldProxy.worldLoaded()
                && Boolean.TRUE.equals(DhApi.Delayed.configs.graphics().renderingEnabled().getValue())
                && DhApi.Delayed.configs.graphics().renderingMode().getValue() == EDhApiRendererMode.DEFAULT) {
            var wrapper = DhApi.Delayed.worldProxy.getSinglePlayerLevel();
            if (wrapper instanceof IClientLevelWrapper nativeWrapper && wrapper.getWrappedMcObject() == mc.level
                    && nativeWrapper.getDhLevel() instanceof IDhClientLevel client && client.isRendering()) {
                current = client; handler = client.getRenderBufferHandler();
            }
        }
        if (level != mc.level || dhLevel != current || renderer != handler) {
            level = mc.level; dhLevel = current; renderer = handler; context = new Object();
            REPORTED.clear();
        }
        // Native coverage remains usable while DH is disabled or not yet initialized.
        REQUESTS.begin(frame, context);
    }

    private static CoverageColumns.State read(CoverageFootprint.Cell cell, CoverageRequests.Bounds bounds) {
        var nativeState = nativeColumn(cell, bounds);
        if (nativeState != CoverageColumns.State.UNKNOWN) return nativeState;
        if (renderer == null || dhLevel == null) { diagnostic(Diagnostic.NO_PUBLISHED_SECTION, cell.x(), cell.z()); return CoverageColumns.State.UNKNOWN; }
        var tree = renderer.lodQuadTree;
        int x = cell.x() * 16, z = cell.z() * 16;
        for (byte detail = tree.treeLeafDetailLevel; detail <= tree.treeRootDetailLevel && detail <= 30; detail++) {
            int width = DhSectionPos.getDetailLevelWidthInBlocks(detail);
            long position = DhSectionPos.encode(detail, Math.floorDiv(x, width), Math.floorDiv(z, width));
            var section = tree.tryGetValue(position);
            if (section == null || !section.getRenderingEnabled()) continue;
            var buffer = section.renderBufferContainer;
            if (buffer == null || !buffer.buffersUploaded) { diagnostic(Diagnostic.PENDING_BUFFER, cell.x(), cell.z()); return CoverageColumns.State.UNKNOWN; }
            var columns = ((CoverageCarrier) buffer).skyweave$coverage();
            if (columns == null || columns.owner() != dhLevel || columns.section() != position) {
                diagnostic(Diagnostic.OWNER_MISMATCH, cell.x(), cell.z()); return CoverageColumns.State.UNKNOWN;
            }
            var state = columns.cell(cell.x(), cell.z());
            if (state == CoverageColumns.State.UNKNOWN) diagnostic(Diagnostic.INCOMPLETE_COLUMN, cell.x(), cell.z());
            return state;
        }
        diagnostic(Diagnostic.NO_PUBLISHED_SECTION, cell.x(), cell.z());
        return CoverageColumns.State.UNKNOWN;
    }

    private static CoverageColumns.State nativeColumn(CoverageFootprint.Cell cell, CoverageRequests.Bounds bounds) {
        if (level == null) return CoverageColumns.State.UNKNOWN;
        var chunk = level.getChunkSource().getChunk(cell.x(), cell.z(), ChunkStatus.FULL, false);
        if (chunk == null) return CoverageColumns.State.UNKNOWN;
        int first = Math.max(level.getMinSection(), Math.floorDiv(bounds.minimumY(), 16));
        int last = Math.min(level.getMaxSection() - 1, Math.floorDiv(bounds.maximumY(), 16));
        boolean populated = false;
        for (int section = first; section <= last; section++) {
            if (chunk.getSection(level.getSectionIndexFromSectionY(section)).hasOnlyAir()) continue;
            if (!Minecraft.getInstance().levelRenderer.isSectionCompiled(new BlockPos(cell.x() * 16, section * 16, cell.z() * 16))) {
                diagnostic(Diagnostic.NATIVE_SECTION_UNMESHED, cell.x(), cell.z()); return CoverageColumns.State.UNKNOWN;
            }
            populated = true;
        }
        return populated ? CoverageColumns.State.READY : CoverageColumns.State.EMPTY;
    }

    public static int queryReads() { return REQUESTS.reads(); }
    public static int pendingEntries() { return REQUESTS.size(); }
}
