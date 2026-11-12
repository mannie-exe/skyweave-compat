package design.inherent.skyweavecompat.probe;

import com.google.gson.JsonObject;
import design.inherent.skyweavecompat.Feature;
import design.inherent.skyweavecompat.OptionalIntegrations;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;

/** Actual upstream group insertion and aging in a bounded authored pool; not natural river frequency. */
final class FloeClimateProbe {
    private FloeClimateProbe() {}
    private static void check(boolean valid, String message) { if (!valid) throw new AssertionError(message); }

    @SuppressWarnings("unchecked")
    static JsonObject run(ServerLevel level) throws Exception {
        boolean adapted = OptionalIntegrations.enabled(Feature.CLIMATE);
        var registry = level.registryAccess().registryOrThrow(Registries.BIOME);
        var warm = registry.getHolderOrThrow(Biomes.BIRCH_FOREST);
        var cold = registry.getHolderOrThrow(Biomes.SNOWY_PLAINS);
        var center = new BlockPos(8, 508, 8);
        var chunk = level.getChunk(center);
        for (int x=2; x<=14; x++) for (int z=2; z<=14; z++) {
            level.setBlock(new BlockPos(x,507,z), Blocks.STONE.defaultBlockState(), 2);
            level.setBlock(new BlockPos(x,508,z), Blocks.WATER.defaultBlockState(), 2);
            for (int y=509; y<level.getMaxBuildHeight(); y++)
                level.setBlock(new BlockPos(x,y,z), Blocks.AIR.defaultBlockState(), 2);
        }
        var sampler = level.getChunkSource().randomState().sampler();
        chunk.fillBiomesFromNoise((x,y,z,s)->warm, sampler);
        check(level.getBiome(center).is(Biomes.BIRCH_FOREST), "Warm water fixture did not retain its actual biome");
        check(warm.value().coldEnoughToSnow(center), "High water fixture does not reproduce native altitude cooling");
        var spawner = Class.forName("com.AdamNew.dynamicwaters.events.IceFloeSpawner");
        var group = spawner.getDeclaredMethod("setGroupAfloat", ServerLevel.class, int.class, int.class, int.class, RandomSource.class);
        group.setAccessible(true);
        var type = (Class<? extends Entity>)Class.forName("com.AdamNew.dynamicwaters.entity.IceFloe");
        var area = new AABB(0,500,0,16,520,16);
        check(level.getEntitiesOfClass(type,area).isEmpty(), "Floe fixture is not initially empty");
        int loaded = level.getChunkSource().getLoadedChunksCount();
        group.invoke(null,level,8,508,8,RandomSource.create(42));
        int warmCount = level.getEntitiesOfClass(type,area).size();
        check(adapted ? warmCount==0 : warmCount>0, "Unexpected warm-water insertion count: "+warmCount+" adapted="+adapted);
        for (var entity:level.getEntitiesOfClass(type,area)) entity.discard();
        chunk.fillBiomesFromNoise((x,y,z,s)->cold, sampler);
        check(level.getBiome(center).is(Biomes.SNOWY_PLAINS), "Cold water fixture did not retain its actual biome");
        group.invoke(null,level,8,508,8,RandomSource.create(42));
        var floes = level.getEntitiesOfClass(type,area);
        check(!floes.isEmpty(), "Actual native cold-water group insertion failed");
        var entity = floes.getFirst();
        var warmField = type.getDeclaredField("warm"); warmField.setAccessible(true);
        var age = type.getDeclaredField("age"); age.setAccessible(true);
        var lifetime = type.getDeclaredField("lifetime"); lifetime.setAccessible(true);
        lifetime.setInt(entity,100000);
        // Match the actual periodic climate boundary without waiting for unrelated server ticks.
        entity.tickCount=Math.floorMod(-entity.getId(),20);
        warmField.setBoolean(entity,false); age.setInt(entity,0); entity.tick();
        check(!entity.isRemoved() && !warmField.getBoolean(entity) && age.getInt(entity)==1,
                "Native cold-water lifetime behavior changed");
        chunk.fillBiomesFromNoise((x,y,z,s)->warm, sampler);
        entity.tickCount=Math.floorMod(-entity.getId(),20);
        entity.tick();
        check(!entity.isRemoved() && warmField.getBoolean(entity)==adapted,
                "Native floe did not reclassify its actual warm water");
        check(age.getInt(entity)==(adapted?11:2), "Native warm aging multiplier was not preserved");
        check(level.getChunkSource().getLoadedChunksCount()==loaded, "Floe climate checks loaded chunks");
        var result=new JsonObject(); result.addProperty("adapted",adapted);
        result.addProperty("poolY",508); result.addProperty("warmGroupCount",warmCount);
        result.addProperty("coldGroupCount",floes.size()); result.addProperty("coldAge",1);
        result.addProperty("ageAfterWarmTransition",age.getInt(entity));
        result.addProperty("warmAfterTransition",warmField.getBoolean(entity));
        result.addProperty("doesNotLoadChunks",true);
        result.addProperty("scope","Native setGroupAfloat and two real IceFloe ticks; authored biomes/pool. Does not qualify spawnNear natural node selection or long-run drift.");
        for(var floe:floes) floe.discard();
        return result;
    }
}
