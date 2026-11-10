package design.inherent.skyweavecompat.wayfinder.sable;

import design.inherent.skyweavecompat.wayfinder.SightRay;
import design.inherent.skyweavecompat.wayfinder.WayfinderFrames;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.SubLevel;
import java.util.Iterator;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

/** Conservative loaded-construct envelopes, never projected/storage block reads. Sable 2.0.6. */
public final class SableWayfinderFrames implements WayfinderFrames {
    private final SubLevelContainer container;
    private final Iterator<? extends SubLevel> ships;
    private final SightRay ray;
    private double first = Double.POSITIVE_INFINITY;

    public SableWayfinderFrames(Level level, SightRay ray) {
        this.ray = ray;
        container = SubLevelContainer.getContainer(level);
        if (container == null) throw new IllegalStateException("Sable level container is unavailable");
        ships = container.getAllSubLevels().iterator();
        if (storage(BlockPos.containing(ray.x(),ray.y(),ray.z()))) first = 0;
    }

    @Override public boolean advance(int budget) {
        for (int work = 0; work < budget && ships.hasNext(); work++) {
            var ship = ships.next();
            if (ship.isRemoved()) continue;
            var box = ship.boundingBox();
            if (!(box.minX() < box.maxX() && box.minY() < box.maxY() && box.minZ() < box.maxZ())) continue;
            var intersection = ray.intersect(box.minX(),box.minY(),box.minZ(),box.maxX(),box.maxY(),box.maxZ());
            if (intersection != null) first = Math.min(first,intersection.enter());
        }
        return !ships.hasNext();
    }
    @Override public double firstDistance() { return first; }
    @Override public boolean storage(BlockPos position) { return container.inBounds(position); }
    @Override public boolean storage(net.minecraft.world.level.ChunkPos position) { return container.inBounds(position); }
}
