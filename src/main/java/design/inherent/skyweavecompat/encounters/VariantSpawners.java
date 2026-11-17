package design.inherent.skyweavecompat.encounters;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.BaseSpawner;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;

public final class VariantSpawners {
    public record Choice(EntityType<?> type,CompoundTag tag){}
    public static boolean applies(ServerLevel level,BlockPos pos,BaseSpawner spawner){
        if(EncounterConfiguration.profile().spawners().mode()!=EncounterProfile.Mode.PER_EMISSION)return false;
        var chunk=level.getChunkSource().getChunkNow(Math.floorDiv(pos.getX(),16),Math.floorDiv(pos.getZ(),16));
        return chunk!=null&&chunk.getBlockEntity(pos) instanceof SpawnerBlockEntity block&&block.getSpawner()==spawner;
    }
    public static Choice choose(ServerLevel level,BlockPos pos,CompoundTag baseline){
        String source=baseline.getString("id");
        if(!SpawnerSelection.base(source)||baseline.contains("Passengers"))return null;
        var settings=EncounterConfiguration.profile().spawners();
        if(settings.replacementPercent()==0||level.isOutsideBuildHeight(pos))return null;
        // Preserve native seeded3D biome zoom. Its one selected noise cell is read only
        // from existing chunks, so a missing neighboring corner cannot request terrain.
        var biome=level.getBiomeManager().withDifferentSource((qx,qy,qz)->{
            var chunk=level.getChunkSource().getChunkNow(Math.floorDiv(qx,4),Math.floorDiv(qz,4));
            return chunk==null?null:chunk.getNoiseBiome(qx,qy,qz);
        }).getBiome(pos);
        if(biome==null)return null;
        var variant=SpawnerSelection.select(settings.rules(),source,VariantsPolicy::enabled,
                id->biome.is(TagKey.create(Registries.BIOME,ResourceLocation.parse(id))));
        if(variant==null||!EncounterChance.roll(level.getRandom(),settings.replacementPercent()))return null;
        var type=BuiltInRegistries.ENTITY_TYPE.getOptional(ResourceLocation.parse(variant.target)).orElseThrow();
        var copy=baseline.copy();copy.putString("id",variant.target);
        return new Choice(type,copy);
    }
    private VariantSpawners(){}
}
