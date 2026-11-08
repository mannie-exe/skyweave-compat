package design.inherent.skyweavecompat.probe;

import com.AdamNew.dynamicwaters.HydrologyManager;
import com.AdamNew.dynamicwaters.SableCompat;
import com.google.gson.JsonObject;
import dev.ryanhcode.sable.api.physics.PhysicsPipeline;
import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import org.joml.Vector3d;
import org.joml.Vector3dc;

/** Real assembled native body and upstream call, recording rather than applying diagnostic impulses. */
final class HydrologySableProbe {
    static JsonObject observe(ServerLevel level, BlockPos water) throws Exception {
        var position = water.above(12);
        if (!level.getBlockState(position).isAir()) throw new AssertionError("Dry craft fixture is obstructed");
        level.setBlockAndUpdate(position, Blocks.OAK_PLANKS.defaultBlockState());
        var ship = SubLevelAssemblyHelper.assembleBlocks(level, position, List.of(position), BoundingBox3i.from(List.of(position)));
        var center = new Vector3d(ship.logicalPose().position());
        var flow = HydrologyManager.getRiverFlow(center.x(), (int)center.y(), center.z(), level);
        if (flow.lengthSqr() <= .01) throw new AssertionError("Dry craft is outside upstream's metadata flow band: " + center);
        var dry = impulses(level, ship);
        if (!dry.isEmpty()) throw new AssertionError("Actual air-only craft received a current: " + dry);
        var result = new JsonObject(); result.addProperty("dryY", center.y());
        result.addProperty("waterY", water.getY()); result.addProperty("flow", flow.toString());
        result.addProperty("dryImpulseCalls", dry.size());
        result.addProperty("assembledInContainer", SubLevelContainer.getContainer(level).getAllSubLevels().contains(ship));
        ship.logicalPose().position().set(center.x, water.getY() + .25, center.z);
        ship.updateBoundingBox();
        var wet = impulses(level, ship);
        if (wet.size() != 1) throw new AssertionError("Wet craft did not receive exactly one current: " + wet);
        ship.logicalPose().orientation().rotationY(Math.PI / 2);
        ship.updateBoundingBox();
        var yawed = impulses(level, ship);
        if (yawed.size() != 1 || wet.getFirst().dot(yawed.getFirst()) / wet.getFirst().length() / yawed.getFirst().length() < .99)
            throw new AssertionError("Current rotated with craft yaw: " + wet + "/" + yawed);
        var local = ship.getPlot().getCenterBlock();
        var global = BlockPos.containing(ship.logicalPose().transformPosition(local.getCenter()));
        if (!SableCompat.getGlobalBlockPos(level, local).equals(global)) throw new AssertionError("Sable fluid position was not projected");
        result.addProperty("wetImpulseCalls", wet.size()); result.addProperty("wetImpulse", wet.getFirst().toString());
        result.addProperty("yawedImpulse", yawed.getFirst().toString()); result.addProperty("globalFluidPosition", global.toShortString());
        result.addProperty("wetDryAndYawVerified", true);
        SubLevelContainer.getContainer(level).removeSubLevel(ship, SubLevelRemovalReason.REMOVED);
        return result;
    }
    private static List<Vector3d> impulses(ServerLevel level, ServerSubLevel ship) throws Exception {
        var system = SubLevelContainer.getContainer(level).physicsSystem();
        var field = SubLevelPhysicsSystem.class.getDeclaredField("pipeline"); field.setAccessible(true);
        var pipeline = (PhysicsPipeline)field.get(system);
        var impulses = new ArrayList<Vector3d>();
        var recorder = Proxy.newProxyInstance(PhysicsPipeline.class.getClassLoader(), new Class<?>[]{PhysicsPipeline.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("applyLinearAndAngularImpulse") && args[0] == ship) {
                        impulses.add(new Vector3d((Vector3dc)args[1])); return null;
                    }
                    try { return method.invoke(pipeline, args); }
                    catch (java.lang.reflect.InvocationTargetException error) { throw error.getCause(); }
                });
        // Test-only synchronous substitution, restored before another native physics tick.
        try { field.set(system, recorder); SableCompat.tickPhysicsShips(level); }
        finally { field.set(system, pipeline); }
        return impulses;
    }
    private HydrologySableProbe() {}
}
