package design.inherent.skyweavecompat.terrain;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.ServerLevelAccessor;
import org.sathrek.sky_archipelago.worldgen.generator.core.SkyIslandChunkGenerator;

/** Natural habitat altitude, separate from thermal altitude and physical collision/light positions. */
public final class IslandHabitat {
    private IslandHabitat() {}
    private static IslandContext.Body owner(LevelAccessor access, BlockPos pos) {
        if (!(access instanceof ServerLevelAccessor server)
                || !(server.getLevel().getChunkSource().getGenerator() instanceof SkyIslandChunkGenerator generator)
                || !((GeologicalGenerator)(Object)generator).skyweave$isEnabled()) return null;
        var field = (InteriorField)(Object)((GeologicalGenerator)(Object)generator)
                .skyweave$field(server.getLevel().getChunkSource().randomState());
        return field.skyweave$contextAt(pos.getX(),pos.getZ(),generator.resolvedSkyIslandSettings()).owner(pos.getY(),0);
    }
    public static int roof(LevelAccessor access, BlockPos pos, int fallback) {
        if (!LocalWater.applies(access)) return fallback;
        var owner = owner(access,pos);
        return owner == null ? access.getMinBuildHeight()-128 : owner.exterior().topY();
    }
    public static int surfaceAltitude(LevelAccessor access, BlockPos pos) {
        if (!LocalWater.applies(access)) return pos.getY();
        var owner = owner(access,pos);
        return owner == null ? Integer.MAX_VALUE : 64+pos.getY()-owner.climateDatum();
    }
    public static int undergroundAltitude(LevelAccessor access, BlockPos pos) {
        if (!LocalWater.applies(access)) return pos.getY();
        var owner = owner(access,pos);
        return owner == null || owner.exterior().thickness() < 2 ? Integer.MAX_VALUE
                : new GeologicalFrame(owner.exterior().bottomY(),owner.exterior().topY()+1).toReference(pos.getY());
    }
}
