package design.inherent.skyweavecompat.water;

import com.AdamNew.dynamicwaters.Config;
import com.AdamNew.dynamicwaters.HydrologyManager;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;

/** One world-space current impulse, only where a loaded ship envelope touches actual water. */
public final class IslandWaterShips {
    private static final int CONTACT_BUDGET = 4096;
    private IslandWaterShips() {}

    public static void tick(ServerLevel level) {
        var container = SubLevelContainer.getContainer(level);
        if (container == null) return;
        for (var candidate : container.getAllSubLevels()) {
            if (!(candidate instanceof ServerSubLevel ship) || ship.isRemoved()) continue;
            var flow = contactFlow(level, ship);
            if (flow.lengthSqr() <= .01) continue;
            double mass = ship.getMassTracker().getMass();
            if (!(mass > 0) || !Double.isFinite(mass)) continue;
            double speed = Config.SABLE_SHIP_SPEED.get() * 2;
            var delta = new Vector3d(flow.x * speed - ship.latestLinearVelocity.x, 0,
                    flow.z * speed - ship.latestLinearVelocity.z).mul(mass * .03);
            var handle = RigidBodyHandle.of(ship);
            // Rapier receives world-space linear impulses. Inverse-rotating this
            // delta would make a yawed ship drift in a different world direction.
            if (handle != null) handle.applyLinearImpulse(delta);
        }
    }

    public static Vec3 contactFlow(ServerLevel level, ServerSubLevel ship) {
        var box = ship.boundingBox();
        int minX = Mth.floor(box.minX() - .001), maxX = Mth.floor(box.maxX() + .001);
        int minZ = Mth.floor(box.minZ() - .001), maxZ = Mth.floor(box.maxZ() + .001);
        int minY = Math.max(level.getMinBuildHeight(), Mth.floor(box.minY() - .001));
        int maxY = Math.min(level.getMaxBuildHeight() - 1, Mth.floor(box.maxY() + .001));
        var pos = new BlockPos.MutableBlockPos();
        int remaining = CONTACT_BUDGET;
        // Bottom-up: ordinary floating hulls find contact before inspecting their
        // empty upper envelope. Never acquire chunks for a distant craft.
        for (int y = minY; y <= maxY; y++) for (int x = minX; x <= maxX; x++) for (int z = minZ; z <= maxZ; z++) {
            if (--remaining < 0) return Vec3.ZERO;
            var chunk = level.getChunkSource().getChunkNow(Math.floorDiv(x, 16), Math.floorDiv(z, 16));
            if (chunk == null) continue;
            pos.set(x, y, z);
            var fluid = chunk.getFluidState(pos);
            if (!fluid.is(FluidTags.WATER) || y + fluid.getHeight(level, pos) < box.minY() - .001) continue;
            var flow = HydrologyManager.getRiverFlow(x, y, z, level);
            if (flow.lengthSqr() > .01) return flow;
        }
        return Vec3.ZERO;
    }
}
