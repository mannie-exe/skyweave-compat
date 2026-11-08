package design.inherent.skyweavecompat.client;

import design.inherent.skyweavecompat.climate.IslandClimate;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/** Small, loaded-block-only exposure check for shader cave heuristics beneath floating terrain. */
public final class IslandSkyExposure {
    private static java.lang.ref.WeakReference<ClientLevel> previousLevel = new java.lang.ref.WeakReference<>(null);
    private static BlockPos previousPosition;
    private static long previousTick;
    private static int exposure;
    private IslandSkyExposure() {}

    public static int openSky() {
        var client = Minecraft.getInstance();
        var level = client.level;
        if (level == null || !IslandClimate.applies(level)) {
            previousLevel.clear(); previousPosition = null; return 0;
        }
        var position = client.gameRenderer.getMainCamera().getBlockPosition();
        if (level == previousLevel.get() && position.equals(previousPosition) && level.getGameTime() - previousTick < 2) return exposure;
        previousLevel = new java.lang.ref.WeakReference<>(level); previousPosition = position.immutable(); previousTick = level.getGameTime();
        return exposure = exposed(level, position) ? 1 : 0;
    }

    private static boolean exposed(ClientLevel level, BlockPos position) {
        if (position.getY() < level.getMinBuildHeight() || position.getY() >= level.getMaxBuildHeight()) return true;
        if (!level.hasChunkAt(position) || !level.getFluidState(position).isEmpty()) return false;
        if (level.canSeeSky(position)) return true;
        var cursor = new BlockPos.MutableBlockPos();
        // An island underside has no nearby floor. A genuine cave retains its floor
        // and its ordinary shader darkness, regardless of absolute world elevation.
        for (int step = 0; step <= 128 && position.getY() - step >= level.getMinBuildHeight(); step++) {
            cursor.set(position.getX(), position.getY() - step, position.getZ());
            if (!level.getBlockState(cursor).getCollisionShape(level, cursor).isEmpty()
                    || !level.getFluidState(cursor).isEmpty()) return false;
        }
        int open = 0;
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            boolean clear = true;
            for (int step = 1; step <= 24; step++) {
                cursor.setWithOffset(position, direction.getStepX() * step, 0, direction.getStepZ() * step);
                if (!level.hasChunkAt(cursor) || !level.getBlockState(cursor).getCollisionShape(level, cursor).isEmpty()) {
                    clear = false; break;
                }
            }
            if (clear && ++open >= 2) return true;
        }
        return false;
    }
}
