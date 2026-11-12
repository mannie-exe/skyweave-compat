package design.inherent.skyweavecompat.terrain.structure;

import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.*;
import org.sathrek.sky_archipelago.worldgen.benchmark.StructureBenchmarkRecorder;

/** One carving call's immutable shallow native-source exclusion, never a terrain writer. */
public final class GroundVillageCarving {
    private record Scope(ChunkAccess chunk,Set<BlockPos> cells) {}
    private static final ThreadLocal<Scope> ACTIVE=new ThreadLocal<>();
    private GroundVillageCarving() {}
    public static void run(ChunkGenerator generator,WorldGenLevel level,ChunkAccess chunk,Runnable original) {
        var previous=ACTIVE.get();var cells=GroundVillageTerrain.carvingProtection(generator,level,chunk);
        if(cells.isEmpty())ACTIVE.remove();else ACTIVE.set(new Scope(chunk,cells));
        try {original.run();}finally {if(previous==null)ACTIVE.remove();else ACTIVE.set(previous);}
    }
    public static boolean protects(ChunkAccess chunk,BlockPos position) {
        var scope=ACTIVE.get();
        if(scope==null || scope.chunk()!=chunk || !scope.cells().contains(position))return false;
        StructureBenchmarkRecorder.increment("skyweaveVillageCarverRefusedCells");return true;
    }
}
