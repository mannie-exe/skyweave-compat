package linearreview;

import com.google.gson.*;
import com.mcwbridges.kikoz.objects.items.Bridge_Torch;
import design.inherent.skyweavecompat.macaw.LanternShapes;
import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import java.nio.file.*;
import java.util.*;

/** Native outline rays below the owning cell, before and after real assembly. */
@Mod("linear_review_probe")
public final class LanternProbe {
    private boolean done;
    public LanternProbe() { NeoForge.EVENT_BUS.addListener(this::tick); }
    private void tick(ServerTickEvent.Post event) {
        if (done) return;
        done = true;
        var server = event.getServer();
        var level = server.overworld();
        var report = new JsonObject();
        var rows = new JsonArray();
        long started = System.nanoTime();
        try {
            var block = BuiltInRegistries.BLOCK.get(ResourceLocation.parse("mcwbridges:bridge_lantern"));
            for (var light : Bridge_Torch.LightState.values()) for (var facing : Direction.Plane.HORIZONTAL) {
                var owner = new BlockPos(40 + rows.size() * 32, 120, 40);
                var state = block.defaultBlockState().setValue(Bridge_Torch.LIGHTSTATE, light).setValue(Bridge_Torch.FACING, facing);
                level.setBlock(owner, state, 2);
                var bounds = LanternShapes.outline(state).bounds();
                var target = new Vec3(owner.getX() + (bounds.minX + bounds.maxX) / 2,
                        owner.getY() + bounds.minY + .025, owner.getZ() + (bounds.minZ + bounds.maxZ) / 2);
                var from = target.add(-3, 0, 0);
                var to = target.add(3, 0, 0);
                require(level.clip(ray(from, to)).getBlockPos().equals(owner), "static overhang " + state);
                var blocks = new ArrayList<BlockPos>(); blocks.add(owner);
                for (int dx : new int[]{-2, 2}) for (int dz : new int[]{-2, 2}) {
                    var support = owner.offset(dx, -2, dz);
                    level.setBlock(support, Blocks.STONE.defaultBlockState(), 2); blocks.add(support);
                }
                var body = SubLevelAssemblyHelper.assembleBlocks(level, owner, blocks, BoundingBox3i.from(blocks));
                var localOwner = body.getPlot().getCenterBlock();
                require(level.getBlockState(localOwner).is(block), "assembled owner");
                var delta = localOwner.subtract(owner);
                var localFrom = from.add(delta.getX(), delta.getY(), delta.getZ());
                var localTo = to.add(delta.getX(), delta.getY(), delta.getZ());
                var row = new JsonObject(); row.addProperty("state", state.toString());
                for (double yaw : new double[]{0, Math.PI / 3}) {
                    body.logicalPose().orientation().rotationY(yaw);
                    body.updateBoundingBox();
                    var worldFrom = body.logicalPose().transformPosition(localFrom);
                    var worldTo = body.logicalPose().transformPosition(localTo);
                    for (int i = 0; i < 64; i++) {
                        var hit = level.clip(ray(worldFrom, worldTo));
                        require(hit.getType() == HitResult.Type.BLOCK && hit.getBlockPos().equals(localOwner),
                                "construct overhang " + yaw + "/" + state + ": " + hit.getType() + "/" + hit.getBlockPos() + " loc=" + hit.getLocation() + " owner=" + localOwner + " from=" + worldFrom + " to=" + worldTo + " local=" + level.clip(ray(localFrom, localTo)).getBlockPos() + " bounds=" + body.boundingBox());
                    }
                    var miss = level.clip(ray(worldFrom.add(0, -3, 0), worldTo.add(0, -3, 0)));
                    require(miss.getType() == HitResult.Type.MISS, "empty projected ray");
                }
                row.addProperty("translatedAndYawedRays", 128); rows.add(row);
                SubLevelContainer.getContainer(level).removeSubLevel(body, SubLevelRemovalReason.REMOVED);

            }
            report.add("states", rows);
            report.addProperty("elapsedMillis", (System.nanoTime() - started) / 1_000_000);
            report.addProperty("complete", true);
            Files.writeString(Path.of(System.getProperty("linear.review.output")), new GsonBuilder().setPrettyPrinting().create().toJson(report));
        } catch (Throwable error) {
            error.printStackTrace();
            try { Files.writeString(Path.of(System.getProperty("linear.review.output") + ".failure"), error.toString()); }
            catch (Exception failure) { failure.printStackTrace(); }
        }
        server.halt(false);
    }
    private static ClipContext ray(Vec3 from, Vec3 to) {
        return new ClipContext(from, to, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, (net.minecraft.world.entity.Entity)null);
    }
    private static void require(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
