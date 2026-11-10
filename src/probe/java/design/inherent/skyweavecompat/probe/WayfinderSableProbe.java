package design.inherent.skyweavecompat.probe;

import design.inherent.skyweavecompat.wayfinder.SightRay;
import design.inherent.skyweavecompat.wayfinder.WayfinderFrames;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.block.Blocks;

/** Real assembled construct; this class is never resolved by the minimum fixture. */
final class WayfinderSableProbe {
    private static ServerSubLevel ship;
    static void start(MinecraftServer server) {
        assemble(server,20);
    }
    private static void assemble(MinecraftServer server, int x) {
        var level = server.overworld(); var position = new BlockPos(x,102,0);
        var blocks = List.of(position.below(),position,position.above(),position.above(2));
        for (var block : blocks) level.setBlockAndUpdate(block,Blocks.STONE.defaultBlockState());
        ship = SubLevelAssemblyHelper.assembleBlocks(level,position,blocks,BoundingBox3i.from(blocks));
        ship.forceUpdateGlobalBounds();
    }
    static void verify(MinecraftServer server) {
        var level = server.overworld();
        var frames = WayfinderFrames.open(level,new SightRay(.5,102.62,.5,1,0,0));
        if (frames == null || !frames.advance(256) || !(frames.firstDistance() > 0 && frames.firstDistance() < 40)
                || !frames.storage(ship.getPlot().getCenterBlock()))
            throw new AssertionError("Actual construct/storage frames: first="+(frames == null ? null : frames.firstDistance())
                    +", box="+ship.boundingBox()+", storage="+(frames != null && frames.storage(ship.getPlot().getCenterBlock()))
                    +", loaded="+SubLevelContainer.getContainer(level).getAllSubLevels().size());
    }
    static void moveBehindTarget(MinecraftServer server) {
        close(server); assemble(server,80);
    }
    static void close(MinecraftServer server) {
        if (ship != null) {
            SubLevelContainer.getContainer(server.overworld()).removeSubLevel(ship,SubLevelRemovalReason.REMOVED);
            ship = null;
        }
    }
}
