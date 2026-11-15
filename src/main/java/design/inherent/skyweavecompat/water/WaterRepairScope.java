package design.inherent.skyweavecompat.water;

import com.AdamNew.dynamicwaters.HydrologyManager;
import java.util.IdentityHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import org.sathrek.sky_archipelago.worldgen.generator.field.TerrainColumn;

/** Authority for DW's pinned post-carver material callback, never for general world writes. */
public final class WaterRepairScope {
    public record Bounds(int bottom, int top, int reference) {
        public boolean contains(int y) { return y >= bottom && y <= top; }
    }

    private record Column(int x, int z, Bounds bounds) {}
    private static final class Frame {
        final ChunkAccess chunk;
        final IdentityHashMap<HydrologyManager.RiverCarving,Column> cuts=new IdentityHashMap<>();
        boolean capturing;
        Frame(ChunkAccess chunk) {this.chunk=chunk;}
    }
    private static final ThreadLocal<Frame> ACTIVE = new ThreadLocal<>();
    private WaterRepairScope() {}

    public static void run(ChunkAccess chunk, Runnable action) {
        var previous = ACTIVE.get();
        ACTIVE.set(new Frame(chunk));
        try { action.run(); }
        finally { if (previous == null) ACTIVE.remove(); else ACTIVE.set(previous); }
    }

    /** Only a direct native writer's proposal is retained; downstream dressing queries grant no new authority. */
    public static <T> T capture(java.util.function.Supplier<T> query) {
        var frame=ACTIVE.get();if(frame==null)return query.get();
        boolean before=frame.capturing;frame.capturing=true;
        try{return query.get();}finally{frame.capturing=before;}
    }

    public static Bounds bounds(HydrologyManager.RiverCarving cut, TerrainColumn retained, int shell) {
        int bottom = Math.max(retained.bottomY(), cut.targetY - shell + 1);
        int top = Math.max(retained.topY(), Math.max(cut.targetY, cut.isWatershedWall ? cut.targetY : cut.waterLevel));
        // A dry wall has no water plane. Only the native material consumer uses its local bank datum.
        return new Bounds(bottom, top, cut.isWatershedWall ? cut.targetY : cut.waterLevel);
    }

    public static void admit(int x, int z, HydrologyManager.RiverCarving cut, TerrainColumn retained, int shell) {
        var frame = ACTIVE.get();
        if (frame == null || !frame.capturing) return;
        if (frame.cuts.size() >= 256 && !frame.cuts.containsKey(cut))
            throw new IllegalStateException("Native water repair exceeded its one-chunk cut budget");
        frame.cuts.put(cut, new Column(x, z, bounds(cut, retained, shell)));
    }

    public static int reference(HydrologyManager.RiverCarving cut, int original) {
        var frame = ACTIVE.get();
        var column = frame == null ? null : frame.cuts.get(cut);
        return column == null ? original : column.bounds().reference();
    }

    public static boolean permits(ChunkAccess chunk, BlockPos pos, HydrologyManager.RiverCarving cut) {
        var frame = ACTIVE.get();
        if (frame == null) return true;
        var column = frame.cuts.get(cut);
        // No admitted proposal grants no write authority, including native no-change sentinels.
        return frame.chunk == chunk && column != null && column.x() == pos.getX() && column.z() == pos.getZ()
                && column.bounds().contains(pos.getY())
                && chunk.getPos().getMinBlockX() <= pos.getX() && pos.getX() <= chunk.getPos().getMaxBlockX()
                && chunk.getPos().getMinBlockZ() <= pos.getZ() && pos.getZ() <= chunk.getPos().getMaxBlockZ();
    }
}
