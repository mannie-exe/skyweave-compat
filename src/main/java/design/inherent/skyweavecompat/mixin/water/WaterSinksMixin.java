package design.inherent.skyweavecompat.mixin.water;

import design.inherent.skyweavecompat.water.IslandWaterTerrain;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import design.inherent.skyweavecompat.water.DrainageBoundary;
import design.inherent.skyweavecompat.water.MouthElevation;
import org.sathrek.sky_archipelago.worldgen.generator.core.SkyIslandChunkGenerator;

/** Empty sky terminates drainage; it is never a basin to fill or a route to another island. */
@Mixin(targets = "com.AdamNew.dynamicwaters.terrain.BasinTile$Builder", remap = false)
public abstract class WaterSinksMixin {
    @Unique private static final org.slf4j.Logger skyweave$logger=com.mojang.logging.LogUtils.getLogger();
    @Shadow @Final private ChunkGenerator generator;
    @Shadow @Final private RandomState randomState;
    @Shadow @Final private int cx0;
    @Shadow @Final private int cz0;
    @Shadow @Final private float[] h;
    @Shadow @Final private boolean[] sink;
    @Unique private MouthElevation skyweave$emission;

    @Unique private DrainageBoundary skyweave$boundary(int x, int z) {
        return DrainageBoundary.sample((SkyIslandChunkGenerator)generator, randomState, x, z);
    }

    @Inject(method = "stampSinks", at = @At("RETURN"))
    private void emptySky(CallbackInfo callback) {
        if (!IslandWaterTerrain.applies(generator)) return;
        int width = (int)Math.sqrt(h.length);
        int[] ground = new int[h.length];
        for (int i = 0; i < h.length; i++) {
            // Upstream has already added routing noise to h. Occupancy must come from
            // the unperturbed plan, otherwise empty sky becomes a false small basin.
            int x = (cx0 + i % width) * 8, z = (cz0 + i / width) * 8;
            var boundary = skyweave$boundary(x, z);
            ground[i] = boundary.kind() == DrainageBoundary.Kind.VOID ? generator.getMinY() : boundary.level() + 1;
            if (boundary.kind() == DrainageBoundary.Kind.LOCAL_WATER) {
                sink[i] = true;
                h[i] = boundary.level();
            }
        }
        for (int i = 0; i < h.length; i++) {
            if (ground[i] != generator.getMinY()) continue;
            sink[i] = true;
            int rim = Integer.MAX_VALUE;
            for (int dz = -1; dz <= 1; dz++) for (int dx = -1; dx <= 1; dx++) {
                int x = i % width + dx, z = i / width + dz;
                if (x < 0 || z < 0 || x >= width || z >= width) continue;
                int adjacent = ground[z * width + x];
                if (adjacent != generator.getMinY()) rim = Math.min(rim, adjacent);
            }
            // A terminal discharge is at its supported rim, not hundreds of blocks
            // down at the void floor. This is a routing boundary, never added terrain.
            if (rim != Integer.MAX_VALUE) h[i] = rim - 1;
        }
    }

    @ModifyExpressionValue(method = "settle", at = @At(value = "FIELD",
            target = "Lcom/AdamNew/dynamicwaters/terrain/BasinTile$Builder;seaLevel:I"))
    private int outletLevel(int original, @Local(name = "c") int cell) {
        return IslandWaterTerrain.applies(generator) && sink[cell] ? Math.round(h[cell]) + 1 : original;
    }

    @WrapMethod(method="emit")
    private void emission(int[] cells,int from,int to,int ownerRegion,boolean source,boolean lake,
            boolean confluence,boolean mouth,boolean hitMain,int inletLakeY,@Coerce Object main,
            double offset,Operation<Void> original) {
        var previous=skyweave$emission;
        skyweave$emission=IslandWaterTerrain.applies(generator) && mouth && !hitMain?new MouthElevation():null;
        try { original.call(cells,from,to,ownerRegion,source,lake,confluence,mouth,hitMain,inletLakeY,main,offset); }
        finally { skyweave$emission=previous; }
    }

    @ModifyExpressionValue(method="emit",at={
            @At(value="FIELD",target="Lcom/AdamNew/dynamicwaters/terrain/BasinTile$Builder;seaLevel:I",ordinal=0),
            @At(value="FIELD",target="Lcom/AdamNew/dynamicwaters/terrain/BasinTile$Builder;seaLevel:I",ordinal=1)},require=2,allow=2)
    private int nodeFloor(int original,@Local(name="k") int node,@Local(name="ys") int[] heights) {
        var frame=skyweave$emission;if(frame==null)return original;
        if(!frame.hasHead()) {
            if(node!=0)throw new IllegalStateException("Native emitter did not expose its incoming head");
            // Both lakeFloorAt and native incision have already been applied here.
            frame.head(heights[node]);
        }
        return frame.datum();
    }

    @ModifyExpressionValue(method="emit",at=@At(value="FIELD",
            target="Lcom/AdamNew/dynamicwaters/terrain/BasinTile$Builder;seaLevel:I",ordinal=2),require=1,allow=1)
    private int endpoint(int original) {
        return skyweave$emission==null?original:skyweave$emission.datum();
    }

    @Inject(method="emit",at=@At(value="INVOKE",
            target="Lcom/AdamNew/dynamicwaters/terrain/BasinTile;reverse([D)V",ordinal=0),cancellable=true,require=1,allow=1)
    private void receiver(int[] cells,int from,int to,int ownerRegion,boolean source,boolean lake,
            boolean confluence,boolean mouth,boolean hitMain,int inletLakeY,@Coerce Object main,
            double offset,CallbackInfo callback) {
        var frame=skyweave$emission;
        if(frame!=null && !frame.joins()) {
            org.sathrek.sky_archipelago.worldgen.benchmark.StructureBenchmarkRecorder.increment("skyweaveWaterMouth.UPHILL_RECEIVER");
            skyweave$logger.debug("Refusing uphill island mouth at cell {}: routed head {}, receiver {}",
                    cells[to],frame.incomingY(),frame.receiverY());
            callback.cancel();
        }
    }

    @Inject(method = "emit", at = @At("HEAD"), cancellable = true)
    private void localMouth(int[] cells, int from, int to, int ownerRegion, boolean source, boolean lake,
            boolean confluence, boolean mouth, boolean hitMain, int inletLakeY, @Coerce Object main,
            double offset, CallbackInfo callback) {
        var frame=skyweave$emission;
        if(frame==null)return;
        if(inletLakeY!=Integer.MIN_VALUE) {frame.waterAt(inletLakeY);return;}
        int width = (int)Math.sqrt(h.length);
        for (int i = to; i >= from; i--) {
            int cell = cells[i];
            var boundary = skyweave$boundary((cx0 + cell % width) * 8, (cz0 + cell / width) * 8);
            if (boundary.kind() == DrainageBoundary.Kind.VOID) continue;
            if(boundary.kind()==DrainageBoundary.Kind.LOCAL_WATER)frame.waterAt(boundary.level());
            else frame.voidAt(boundary.level());
            return;
        }
        callback.cancel(); // No supported approach: emitting a sea-level path would invent a river.
    }

    @Inject(method = "seaOnContact", at = @At("HEAD"), cancellable = true)
    private void noFalseSea(int[] cells, int length, CallbackInfoReturnable<Integer> result) {
        // Native coastal shortcut equates empty-sky sinks with sea, snapping rivers sideways off the body.
        // Real water and void outlets are already reached through the native downstream chain.
        if (IslandWaterTerrain.applies(generator)) result.setReturnValue(length);
    }

    @Inject(method = "walkMouthIntoWater", at = @At("HEAD"), cancellable = true)
    private void physicalMouth(double[] points, int cell, int lake, CallbackInfo callback) {
        if (!IslandWaterTerrain.applies(generator) || lake >= 0) return;
        callback.cancel();
        int count = points.length / 3;
        if (count < 2) return;
        int land = -1, terminal = -1;
        long owner = 0;
        for (int i = 0; i < count; i++) {
            var boundary = skyweave$boundary((int)Math.floor(points[i * 3]), (int)Math.floor(points[i * 3 + 1]));
            if (boundary.kind() == DrainageBoundary.Kind.LAND && (land < 0 || owner == boundary.owner())) {
                land = i; owner = boundary.owner();
            } else if (land >= 0) { terminal = i; break; }
        }
        if (land < 0) return;
        int toward = terminal < 0 ? count - 1 : terminal;
        int behind = Math.max(0, land - 1);
        double x = points[land * 3], z = points[land * 3 + 1];
        double dx = points[toward * 3] - x, dz = points[toward * 3 + 1] - z;
        if (Math.hypot(dx, dz) < 1e-6) { dx = x - points[behind * 3]; dz = z - points[behind * 3 + 1]; }
        double distance = Math.hypot(dx, dz);
        if (distance < 1e-6) return;
        dx /= distance; dz /= distance;
        double endX = points[(count - 1) * 3], endZ = points[(count - 1) * 3 + 1];
        for (int step = 1; step <= 40; step++) {
            double px = x + dx * step, pz = z + dz * step;
            var boundary = skyweave$boundary((int)Math.floor(px), (int)Math.floor(pz));
            if (boundary.kind() != DrainageBoundary.Kind.LAND || boundary.owner() != owner) {
                endX = px; endZ = pz;
                if(boundary.kind()==DrainageBoundary.Kind.LOCAL_WATER && skyweave$emission!=null)
                    skyweave$emission.waterAt(boundary.level());
                break;
            }
        }
        int first = Math.min(land, count - 2);
        double startX = points[first * 3], startZ = points[first * 3 + 1];
        for (int i = first + 1; i < count; i++) {
            double t = (i - first) / (double)(count - 1 - first);
            points[i * 3] = startX + (endX - startX) * t;
            points[i * 3 + 1] = startZ + (endZ - startZ) * t;
        }
    }
}
