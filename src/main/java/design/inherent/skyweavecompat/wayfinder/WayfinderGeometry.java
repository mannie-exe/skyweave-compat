package design.inherent.skyweavecompat.wayfinder;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.*;

/** Native shapes and bounded upward placement in the targeted column. */
public final class WayfinderGeometry {
    public record Footprint(int minX, int minZ, int maxX, int maxZ) {
        public int chunks() { return (maxX-minX+1)*(maxZ-minZ+1); }
    }
    private record Body(double halfX, double halfZ, double height) {
        static Body of(Entity entity) {
            var box = entity.getBoundingBox();
            return new Body(box.getXsize()/2,box.getZsize()/2,box.getYsize());
        }
        boolean valid() {
            return Double.isFinite(halfX) && Double.isFinite(halfZ) && Double.isFinite(height)
                    && halfX > 0 && halfZ > 0 && height > 0 && halfX <= 30000000 && halfZ <= 30000000;
        }
    }
    private WayfinderGeometry() {}
    private static boolean fitsWithinCell(double half) { return half + 1e-5 < .5; }
    /** Include Minecraft's one-block collision-neighbor margin, without acquiring more than four chunks. */
    public static Footprint footprint(Entity entity, BlockPos target) {
        var body = Body.of(entity); if (!body.valid()) return null;
        double minX = fitsWithinCell(body.halfX) ? target.getX()+1e-5 : target.getX()+.5-body.halfX;
        double maxX = fitsWithinCell(body.halfX) ? target.getX()+1-1e-5 : target.getX()+.5+body.halfX;
        double minZ = fitsWithinCell(body.halfZ) ? target.getZ()+1e-5 : target.getZ()+.5-body.halfZ;
        double maxZ = fitsWithinCell(body.halfZ) ? target.getZ()+1-1e-5 : target.getZ()+.5+body.halfZ;
        int lowX = Math.floorDiv(net.minecraft.util.Mth.floor(minX-1e-7)-1,16);
        int highX = Math.floorDiv(net.minecraft.util.Mth.floor(maxX+1e-7)+1,16);
        int lowZ = Math.floorDiv(net.minecraft.util.Mth.floor(minZ-1e-7)-1,16);
        int highZ = Math.floorDiv(net.minecraft.util.Mth.floor(maxZ+1e-7)+1,16);
        if ((long)(highX-lowX+1)*(highZ-lowZ+1) > 4) return null;
        return new Footprint(lowX,lowZ,highX,highZ);
    }
    private static double centre(double observed, int cell, double half) {
        return fitsWithinCell(half) ? Math.clamp(observed,cell+half+1e-5,cell+1-half-1e-5) : cell+.5;
    }
    public static boolean blocked(BlockState state, List<String> exclusions) {
        for (String entry : exclusions) {
            if (entry.startsWith("#")) {
                var id = ResourceLocation.tryParse(entry.substring(1));
                if (id != null && state.is(TagKey.create(Registries.BLOCK, id))) return true;
            } else {
                var id = ResourceLocation.tryParse(entry);
                if (id != null && net.minecraft.core.registries.BuiltInRegistries.BLOCK.getOptional(id).map(state::is).orElse(false)) return true;
            }
        }
        return false;
    }
    public static BlockHitResult hit(Level level, Entity player, SightRay ray, SightRay.Cursor cursor, List<String> exclusions) {
        var position = new BlockPos(cursor.x(), cursor.y(), cursor.z());
        var state = level.getBlockState(position);
        var start = ray.at(Math.max(0,cursor.distance() - 1e-6));
        var end = ray.at(Math.min(cursor.end(),cursor.cellExit() + 1e-6));
        Vec3 from = new Vec3(start.x(),start.y(),start.z()), to = new Vec3(end.x(),end.y(),end.z());
        var shape = state.getCollisionShape(level,position,net.minecraft.world.phys.shapes.CollisionContext.of(player));
        if (blocked(state,exclusions) || !state.getFluidState().isEmpty())
            shape = net.minecraft.world.phys.shapes.Shapes.block();
        return shape.clip(from,to,position);
    }
    public static BlockHitResult surface(Level level, Entity player, SightRay ray, BlockPos target, double end) {
        var shape = level.getBlockState(target).getCollisionShape(level,target,net.minecraft.world.phys.shapes.CollisionContext.of(player));
        var pieces = shape.toAabbs();
        if (pieces.isEmpty() || pieces.size() > 512) return null;
        var local = new Vec3(ray.x()-target.getX(),ray.y()-target.getY(),ray.z()-target.getZ());
        if (pieces.stream().anyMatch(box -> box.contains(local))) return null;
        var endpoint = ray.at(end);
        // VoxelShape.clip samples 0.001 along its segment for "inside". A world-length
        // ray can put that sample inside a distant target even when its origin is outside.
        return AABB.clip(pieces,new Vec3(ray.x(),ray.y(),ray.z()),new Vec3(endpoint.x(),endpoint.y(),endpoint.z()),target);
    }
    public static Vec3 landing(Level level, Entity player, BlockPos target, Vec3 observed, List<String> exclusions) {
        return landing(level,player,target,observed,exclusions,0);
    }
    public static Vec3 landing(Level level, Entity player, BlockPos target, Vec3 observed, List<String> exclusions, int lookupHeight) {
        if (lookupHeight < 0 || lookupHeight > 64) throw new IllegalArgumentException("Invalid placement lookup height");
        var dimensions = Body.of(player);
        if (!dimensions.valid() || footprint(player,target) == null) return null;
        double x = centre(observed.x,target.getX(),dimensions.halfX);
        double z = centre(observed.z,target.getZ(),dimensions.halfZ);
        int remaining = 4096;
        for (int offset = 0; offset <= lookupHeight; offset++) {
            var support = target.above(offset);
            if (support.getY() >= level.getMaxBuildHeight()) break;
            var state = level.getBlockState(support);
            if (blocked(state,exclusions)) return null;
            var shape = state.getCollisionShape(level,support,net.minecraft.world.phys.shapes.CollisionContext.of(player));
            var pieces = shape.toAabbs();
            if (pieces.isEmpty() && !state.getFluidState().isEmpty()) return null;
            if (pieces.size() > 512 || offset == 0 && pieces.isEmpty()) return null;
            var heights = pieces.stream().filter(box -> box.minX <= x - support.getX() && box.maxX >= x - support.getX()
                    && box.minZ <= z - support.getZ() && box.maxZ >= z - support.getZ())
                    .map(box -> box.maxY + support.getY()).distinct()
                    .sorted(Comparator.comparingDouble(y -> Math.abs(y - observed.y))).toList();
            for (double y : heights) {
                if (!Double.isFinite(y) || y < support.getY() || y < level.getMinBuildHeight()
                        || y + dimensions.height > level.getMaxBuildHeight()) continue;
                var body = new AABB(x-dimensions.halfX,y+1e-5,z-dimensions.halfZ,
                        x+dimensions.halfX,y+dimensions.height,z+dimensions.halfZ);
                if (!(body.minX < body.maxX && body.minY < body.maxY && body.minZ < body.maxZ)) return null;
                if (!level.getWorldBorder().isWithinBounds(body)) continue;
                var low = BlockPos.containing(body.minX,body.minY,body.minZ);
                var high = BlockPos.containing(Math.nextDown(body.maxX),Math.nextDown(body.maxY),Math.nextDown(body.maxZ));
                long sizeX = (long)high.getX()-low.getX()+1, sizeY = (long)high.getY()-low.getY()+1, sizeZ = (long)high.getZ()-low.getZ()+1;
                if (sizeX > remaining || sizeY > remaining || sizeZ > remaining || sizeX*sizeY*sizeZ > remaining) return null;
                remaining -= (int)(sizeX*sizeY*sizeZ);
                if (!level.noCollision(player,body)) continue;
                boolean bad = false;
                for (var pos : BlockPos.betweenClosed(low,high)) {
                    var occupied = level.getBlockState(pos);
                    if (blocked(occupied,exclusions) || !occupied.getFluidState().isEmpty()) { bad = true; break; }
                }
                if (!bad) return new Vec3(x,y,z);
            }
        }
        return null;
    }
}
