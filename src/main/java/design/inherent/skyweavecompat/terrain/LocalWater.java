package design.inherent.skyweavecompat.terrain;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.chunk.LevelChunk;
import org.sathrek.sky_archipelago.worldgen.generator.core.SkyIslandChunkGenerator;

/** Loaded physical water only. Spawn and movement queries never build a watershed or request a chunk. */
public final class LocalWater {
    private static final int MAX_DEPTH = 256, SHORE_REACH = 4, SHORE_HEIGHT = 8;
    private LocalWater() {}

    public static boolean applies(LevelAccessor access) {
        return access instanceof ServerLevelAccessor server
                && server.getLevel().getChunkSource().getGenerator() instanceof SkyIslandChunkGenerator generator
                && ((GeologicalGenerator)(Object)generator).skyweave$isEnabled();
    }
    private static LevelChunk loaded(LevelAccessor access, int x, int z) {
        return access instanceof ServerLevelAccessor server
                ? server.getLevel().getChunkSource().getChunkNow(Math.floorDiv(x,16),Math.floorDiv(z,16)) : null;
    }
    /** The real water cell under a surface entity, without a creating world lookup. */
    public static BlockPos waterCell(LevelAccessor access, BlockPos position) {
        var chunk=loaded(access,position.getX(),position.getZ());
        if(chunk==null) return null;
        if(chunk.getFluidState(position).is(FluidTags.WATER)) return position;
        var below=position.below();
        return chunk.getFluidState(below).is(FluidTags.WATER) ? below : null;
    }
    /** Null for missing chunks, non-water, or excessive depth. */
    public static Integer surface(LevelAccessor access, BlockPos position) {
        var chunk = loaded(access,position.getX(),position.getZ());
        if (chunk == null || !chunk.getFluidState(position).is(FluidTags.WATER)) return null;
        var at = position.mutable();
        for (int depth=0;depth<MAX_DEPTH && at.getY()<chunk.getMaxBuildHeight()-1;depth++) {
            at.move(0,1,0);
            if (!chunk.getFluidState(at).is(FluidTags.WATER))
                return at.getY()-1;
        }
        return null;
    }
    /** A bounded layer near a known physical datum, with local headroom and no chunk creation. */
    public static Integer surfaceNear(LevelAccessor access,int x,int z,int reference,int reach) {
        if(reach<0 || reach>64)throw new IllegalArgumentException("Unsupported local-water reach");
        var chunk=loaded(access,x,z);if(chunk==null)return null;
        for(int distance=0;distance<=reach;distance++)for(int side=0;side<(distance==0?1:2);side++) {
            long y=(long)reference+(long)distance*(side==0?1:-1);
            if(y<chunk.getMinBuildHeight() || y>=chunk.getMaxBuildHeight()-1)continue;
            var at=new BlockPos(x,(int)y,z);
            if(chunk.getFluidState(at).is(FluidTags.WATER) && chunk.getBlockState(at.above()).isAir())return (int)y;
        }
        return null;
    }
    public static int spawnSurface(LevelAccessor access, BlockPos position, int fallback) {
        if (!applies(access)) return fallback;
        var surface = surface(access,position);
        return surface == null ? access.getMinBuildHeight()-128 : surface;
    }
    /** Dry sand need not be a water cell: inspect a small loaded shore neighborhood. */
    public static int shoreSurface(LevelAccessor access, BlockPos position, int fallback) {
        if (!applies(access)) return fallback;
        Integer selected = surface(access,position);
        if (selected != null) return selected;
        for (int distance=1;distance<=SHORE_REACH;distance++) {
            for (int[] direction : DIRECTIONS) {
                int x=position.getX()+direction[0]*distance,z=position.getZ()+direction[1]*distance;
                var chunk = loaded(access,x,z); if (chunk == null) continue;
                for (int dy=0;dy>=-SHORE_HEIGHT;dy--) {
                    var candidate = new BlockPos(x,position.getY()+dy,z);
                    var water = surface(access,candidate);
                    if (water != null && (selected == null || Math.abs(water-position.getY())<Math.abs(selected-position.getY()))) selected=water;
                }
            }
            if (selected != null) return selected;
        }
        return access.getMinBuildHeight()-128;
    }
    private static final int[][] DIRECTIONS={{1,0},{-1,0},{0,1},{0,-1}};
    public static int movementSurface(LevelAccessor access, BlockPos position, int fallback) {
        int selected=shoreSurface(access,position,fallback);
        return selected < access.getMinBuildHeight() ? fallback : selected;
    }
}
