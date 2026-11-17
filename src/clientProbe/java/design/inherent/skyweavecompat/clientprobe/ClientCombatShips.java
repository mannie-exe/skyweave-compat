package design.inherent.skyweavecompat.clientprobe;

import com.google.gson.JsonObject;
import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import java.util.ArrayList;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.level.block.Blocks;
import org.joml.Vector3d;

/** One freely falling native platform. Reached only in the marked full-pack combat scene. */
final class ClientCombatShips {
    private static UUID id;
    private static Vector3d first;
    static void prepare(ServerPlayer player, int targetId) {
        var level = player.serverLevel(); var origin = new BlockPos(24, 120, 0);
        var blocks = new ArrayList<BlockPos>();
        for (int x = -4; x <= 4; x++) for (int z = -4; z <= 4; z++) {
            var pos = origin.offset(x, 0, z); blocks.add(pos); level.setBlock(pos, Blocks.OAK_PLANKS.defaultBlockState(), 2);
        }
        var body = SubLevelAssemblyHelper.assembleBlocks(level, origin, blocks, BoundingBox3i.from(blocks));
        body.logicalPose().orientation().rotationY(Math.PI / 6); body.updateBoundingBox(); id = body.getUniqueId();
        var center = body.getPlot().getCenterBlock();
        var actor = body.logicalPose().transformPosition(new Vector3d(center.getX() + .5, center.getY() + 1.2, center.getZ() - .75));
        var mob = body.logicalPose().transformPosition(new Vector3d(center.getX() + .5, center.getY() + 1.2, center.getZ() + 1.75));
        player.teleportTo(level, actor.x, actor.y, actor.z, 0, 14);
        var target = (Cow) level.getEntity(targetId); target.setNoGravity(false); target.teleportTo(mob.x, mob.y, mob.z);
    }
    static JsonObject observe() {
        var mc = Minecraft.getInstance(); var result = new JsonObject();
        var body = SubLevelContainer.getContainer(mc.level).getAllSubLevels().stream().filter(s -> s.getUniqueId().equals(id)).findFirst().orElse(null);
        result.addProperty("tracked", body != null); result.addProperty("playerOnGround", mc.player.onGround());
        result.addProperty("playerPosition", mc.player.position().toString());
        if (body != null) {
            var position = body.logicalPose().transformPosition(body.getPlot().getCenterBlock().getCenter());
            if (first == null) first = new Vector3d(position.x, position.y, position.z);
            result.addProperty("movedDistance", first.distance(position.x, position.y, position.z)); result.addProperty("worldPosition", position.toString());
        }
        return result;
    }
    private ClientCombatShips() {}
}
