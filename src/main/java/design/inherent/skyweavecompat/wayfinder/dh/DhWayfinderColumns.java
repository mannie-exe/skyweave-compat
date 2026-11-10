package design.inherent.skyweavecompat.wayfinder.dh;

import com.seibel.distanthorizons.api.interfaces.world.IDhApiLevelWrapper;
import com.seibel.distanthorizons.core.api.internal.SharedApi;
import com.seibel.distanthorizons.core.dataObjects.fullData.sources.FullDataSourceV2;
import com.seibel.distanthorizons.core.level.IDhLevel;
import com.seibel.distanthorizons.core.pos.DhSectionPos;
import com.seibel.distanthorizons.core.util.FullDataPointUtil;
import com.seibel.distanthorizons.core.world.AbstractDhWorld;
import com.seibel.distanthorizons.core.wrapperInterfaces.world.ILevelWrapper;
import design.inherent.skyweavecompat.wayfinder.LodColumns;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/** Exact DH 3.3.3 async read boundary; never generates native chunks or mutates terrain. */
public final class DhWayfinderColumns implements LodColumns {
    // Retired sessions cannot leave an unbounded tail of outstanding database reads.
    private static final AtomicInteger READS = new AtomicInteger();
    private final AbstractDhWorld world;
    private final IDhLevel level;
    private final ILevelWrapper wrapper;
    private final int minY, maxY;
    private FullDataSourceV2 source;
    private long section = Long.MIN_VALUE;
    private boolean pending, closed;
    private State failure;
    private int cachedX, cachedZ;
    private Column cached;

    public DhWayfinderColumns(IDhApiLevelWrapper wrapper) {
        if (!(wrapper instanceof ILevelWrapper nativeWrapper)) throw new IllegalArgumentException("Unsupported DH wrapper");
        this.wrapper = nativeWrapper;
        world = SharedApi.getAbstractDhWorld();
        level = world == null ? null : world.getLevel(nativeWrapper);
        if (level == null) throw new IllegalStateException("DH level is unavailable");
        // DH 3.3.3 calls dimension height "maxHeight"; the native bounds are unambiguous.
        if (!(wrapper.getWrappedMcObject() instanceof net.minecraft.world.level.Level nativeLevel))
            throw new IllegalArgumentException("Expected a native DH level");
        minY = nativeLevel.getMinBuildHeight(); maxY = nativeLevel.getMaxBuildHeight();
    }

    @Override public synchronized Column poll(int x, int z) {
        if (closed) return Column.state(State.CLOSED);
        if (SharedApi.getAbstractDhWorld() != world || world.getLevel(wrapper) != level) {
            close(); return Column.state(State.CLOSED);
        }
        long requested = DhSectionPos.encode((byte)6, Math.floorDiv(x, 64), Math.floorDiv(z, 64));
        if (pending) return Column.state(State.PENDING);
        if (section != requested) {
            releaseSource(); failure = null;
            if (!READS.compareAndSet(0, 1)) return Column.state(State.PENDING);
            section = requested; pending = true;
            try {
                level.getFullDataProvider().getAsync(requested).whenComplete((received, error) -> {
                    synchronized (DhWayfinderColumns.this) {
                        try {
                            if (closed || error != null || received == null || received.isEmpty) {
                                if (received != null) received.close();
                                failure = error == null ? State.MISSING : State.FAILED;
                            } else source = received;
                        } finally { pending = false; READS.decrementAndGet(); }
                    }
                });
            } catch (RuntimeException error) {
                pending = false; READS.decrementAndGet(); failure = State.FAILED;
            }
            if (pending) return Column.state(State.PENDING);
        }
        if (failure != null) return Column.state(failure);
        if (source == null) return Column.state(State.MISSING);
        if (cached != null && cachedX == x && cachedZ == z) return cached;
        try {
            var data = source.getColumnAtRelPos(Math.floorMod(x, 64), Math.floorMod(z, 64));
            if (data == null || data.isEmpty()) return Column.state(State.MISSING);
            if (data.size() > maxY - minY + 1 || source.getDataDetailLevel() != 0) return Column.state(State.FAILED);
            var bands = new ArrayList<Band>(data.size());
            for (int index = 0; index < data.size(); index++) {
                long value = data.getLong(index);
                int bottom = minY + FullDataPointUtil.getBottomY(value), top = bottom + FullDataPointUtil.getHeight(value);
                boolean air = source.mapping.getBlockStateWrapper(FullDataPointUtil.getId(value)).isAir();
                // DH's native importer includes the air sample at the exclusive build ceiling.
                if (bottom < minY || top > maxY + (air ? 1 : 0) || bottom >= top) return Column.state(State.FAILED);
                if (bottom < maxY) bands.add(new Band(bottom, Math.min(top,maxY), air));
            }
            cachedX = x; cachedZ = z; cached = new Column(State.READY, bands);
            return cached;
        } catch (RuntimeException error) { return Column.state(State.FAILED); }
    }

    private void releaseSource() {
        cached = null;
        if (source != null) { source.close(); source = null; }
    }
    @Override public synchronized void close() {
        closed = true; releaseSource();
        // Do not cancel DH's future: a running read still owns its pooled result.
        // Its callback disposes that result, and READS prevents replacement work until then.
    }
}
