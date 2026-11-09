package design.inherent.skyweavecompat.macaw;

import com.mcwbridges.kikoz.objects.items.Bridge_Lantern;
import design.inherent.skyweavecompat.mixin.macaw.ClipModeAccessor;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

/** Check only the neighboring owners whose lantern outlines enter a visited cell. */
public final class LanternSelection {
    public static BlockHitResult extend(BlockGetter getter, ClipContext context, BlockHitResult nativeHit) {
        if (((ClipModeAccessor) context).skyweave$blockMode() != ClipContext.Block.OUTLINE
                || !(getter instanceof Level level) || context.getFrom().equals(context.getTo())) return nativeHit;
        var from = context.getFrom();
        var to = context.getTo();
        if (!finite(from) || !finite(to)) return nativeHit;
        var end = nativeHit.getType() == HitResult.Type.MISS ? context.getTo() : nativeHit.getLocation();
        if (!finite(end) || !SelectionRay.contains(to.x - from.x, to.y - from.y, to.z - from.z,
                end.x - from.x, end.y - from.y, end.z - from.z)) return nativeHit;
        long[] remaining = {SelectionRay.cellBudget(from.x, from.y, from.z, end.x, end.y, end.z)};
        var found = BlockGetter.traverseBlocks(from, end, context, (ray, cell) -> {
            if (remaining[0]-- <= 0) return nativeHit;
            BlockHitResult nearest = null;
            // Each rotated lantern overhangs one horizontal face and the cell below.
            for (int dy = 0; dy <= 1; dy++) {
                for (int side = 0; side < 5; side++) {
                    var owner = cell.offset(side == 1 ? -1 : side == 2 ? 1 : 0, dy,
                            side == 3 ? -1 : side == 4 ? 1 : 0);
                    if (!level.hasChunkAt(owner)) continue;
                    var state = level.getBlockState(owner);
                    if (!(state.getBlock() instanceof Bridge_Lantern)) continue;
                    var shape = LanternShapes.outline(state);
                    if (shape == null) continue;
                    var hit = shape.clip(from, context.getTo(), owner);
                    if (hit == null || !inCell(hit, cell)) continue;
                    double distance = from.distanceToSqr(hit.getLocation());
                    if (distance >= from.distanceToSqr(end)) continue;
                    if (nearest == null || distance < from.distanceToSqr(nearest.getLocation())) nearest = hit;
                }
            }
            return nearest;
        }, ray -> null);
        return found == null ? nativeHit : found;
    }

    private static boolean finite(net.minecraft.world.phys.Vec3 point) {
        return Double.isFinite(point.x) && Double.isFinite(point.y) && Double.isFinite(point.z);
    }

    private static boolean inCell(BlockHitResult hit, BlockPos cell) {
        var p = hit.getLocation();
        double epsilon = 1e-7;
        return p.x >= cell.getX() - epsilon && p.x <= cell.getX() + 1 + epsilon
                && p.y >= cell.getY() - epsilon && p.y <= cell.getY() + 1 + epsilon
                && p.z >= cell.getZ() - epsilon && p.z <= cell.getZ() + 1 + epsilon;
    }
    private LanternSelection() {}
}
