package design.inherent.skyweavecompat.clientprobe;

import com.google.gson.JsonObject;
import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.api.physics.constraint.FixedConstraintConfiguration;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import java.util.ArrayList;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/** Marked integrated fixture only; reproduces ordinary picking after platform assembly. */
final class ClientAssemblyProbe {
    private static UUID id;
    static JsonObject observe(JsonObject command) throws Exception {
        var mc = Minecraft.getInstance();
        var server = mc.getSingleplayerServer();
        if (mc.level == null || mc.player == null || server == null) throw new IllegalStateException("Integrated fixture required");
        if (command.has("prepare") && command.get("prepare").getAsBoolean()) {
            if (id != null) throw new IllegalStateException("Platform already assembled");
            var playerId = mc.player.getUUID();
            id = server.submit(() -> {
                var level = server.overworld(); var origin = new BlockPos(24,120,0);
                var blocks = new ArrayList<BlockPos>();
                for (int x=-4;x<=4;x++) for (int z=-4;z<=4;z++) {
                    var pos = origin.offset(x,0,z); blocks.add(pos); level.setBlock(pos, Blocks.OAK_PLANKS.defaultBlockState(), 2);
                }
                var body = SubLevelAssemblyHelper.assembleBlocks(level,origin,blocks,BoundingBox3i.from(blocks));
                body.logicalPose().orientation().rotationY(Math.PI/3); body.updateBoundingBox();
                var center = body.getPlot().getCenterBlock();
                var world = body.logicalPose().transformPosition(center.getCenter());
                SubLevelContainer.getContainer(level).physicsSystem().getPipeline().addConstraint(body,null,
                        new FixedConstraintConfiguration(new Vector3d(center.getX()+.5,center.getY()+.5,center.getZ()+.5),
                                new Vector3d(world.x,world.y,world.z),new Quaterniond(body.logicalPose().orientation()).conjugate()));
                var player = server.getPlayerList().getPlayer(playerId);
                player.teleportTo(level,world.x,world.y+3,world.z,0,90);
                return body.getUniqueId();
            }).get(15, TimeUnit.SECONDS);
        }
        var report = new JsonObject(); report.addProperty("id", id == null ? "" : id.toString());
        var body = SubLevelContainer.getContainer(mc.level).getAllSubLevels().stream()
                .filter(s -> s.getUniqueId().equals(id)).findFirst().orElse(null);
        report.addProperty("tracked", body != null);
        if (body != null) {
            var center = body.getPlot().getCenterBlock();
            var world = body.logicalPose().transformPosition(center.getCenter());
            var hit = mc.level.clip(new ClipContext(world.add(0,3,0),world.add(0,-3,0),ClipContext.Block.OUTLINE,ClipContext.Fluid.NONE,mc.player));
            report.addProperty("hit", hit.getType().name()); report.addProperty("block", hit.getBlockPos().toShortString());
            report.addProperty("world", world.toString()); report.addProperty("plot", center.toShortString());
            report.addProperty("playerPick", mc.player.pick(8,1,false).getType().name());
        }
        return report;
    }
    private ClientAssemblyProbe() {}
}
