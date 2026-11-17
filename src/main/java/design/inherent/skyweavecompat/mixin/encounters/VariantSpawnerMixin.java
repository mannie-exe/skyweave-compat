package design.inherent.skyweavecompat.mixin.encounters;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import design.inherent.skyweavecompat.encounters.SpawnerSelection;
import design.inherent.skyweavecompat.encounters.VariantSpawners;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.BaseSpawner;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.SpawnData;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.AABB;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** All attempt state is method-local; saved SpawnData and SpawnPotentials are never edited. */
@Mixin(BaseSpawner.class)
public abstract class VariantSpawnerMixin {
    @WrapOperation(method="serverTick",at=@At(value="INVOKE",target="Lnet/minecraft/world/level/BaseSpawner;getOrCreateNextSpawnData(Lnet/minecraft/world/level/Level;Lnet/minecraft/util/RandomSource;Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/SpawnData;"),require=1)
    private SpawnData baseline(BaseSpawner self,Level level,RandomSource random,BlockPos pos,Operation<SpawnData> original,
            @Share("baseline") LocalRef<SpawnData> baseline){
        var value=original.call(self,level,random,pos);baseline.set(value);return value;
    }
    @WrapOperation(method="serverTick",at=@At(value="INVOKE",target="Lnet/minecraft/world/level/SpawnData;getEntityToSpawn()Lnet/minecraft/nbt/CompoundTag;",ordinal=0),require=1)
    private CompoundTag reset(SpawnData ignored,Operation<CompoundTag> original,
            @Share("baseline") LocalRef<SpawnData> baseline,@Local LocalRef<SpawnData> data){
        data.set(baseline.get());return original.call(baseline.get());
    }
    @WrapOperation(method="serverTick",at=@At(value="INVOKE",target="Lnet/minecraft/world/entity/EntityType;getSpawnAABB(DDD)Lnet/minecraft/world/phys/AABB;"),require=1)
    private AABB choose(EntityType<?> type,double x,double y,double z,Operation<AABB> original,
            @Local(argsOnly=true) ServerLevel level,@Local(argsOnly=true) BlockPos owner,
            @Local LocalRef<CompoundTag> tag,@Local LocalRef<Optional<EntityType<?>>> entityType,
            @Local LocalRef<SpawnData> data){
        if(VariantSpawners.applies(level,owner,(BaseSpawner)(Object)this)){
            var choice=VariantSpawners.choose(level,BlockPos.containing(x,y,z),tag.get());
            if(choice!=null){
                tag.set(choice.tag());entityType.set(Optional.of(choice.type()));
                data.set(new SpawnData(choice.tag(),data.get().getCustomSpawnRules(),data.get().getEquipment()));
                return original.call(choice.type(),x,y,z);
            }
        }
        return original.call(type,x,y,z);
    }
    @WrapOperation(method="serverTick",at=@At(value="INVOKE",target="Lnet/minecraft/server/level/ServerLevel;getEntities(Lnet/minecraft/world/level/entity/EntityTypeTest;Lnet/minecraft/world/phys/AABB;Ljava/util/function/Predicate;)Ljava/util/List;"),require=1)
    private List<Entity> family(ServerLevel level,EntityTypeTest<Entity,Entity> type,AABB box,Predicate<Entity> predicate,
            Operation<List<Entity>> original,@Local(argsOnly=true) BlockPos owner,@Share("baseline") LocalRef<SpawnData> baseline){
        String source=baseline.get().getEntityToSpawn().getString("id");
        if(!SpawnerSelection.base(source)||baseline.get().getEntityToSpawn().contains("Passengers")
                ||!VariantSpawners.applies(level,owner,(BaseSpawner)(Object)this))return original.call(level,type,box,predicate);
        var family=new EntityTypeTest<Entity,Entity>(){
            public Entity tryCast(Entity entity){return SpawnerSelection.sameFamily(source,
                    BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString())?entity:null;}
            public Class<? extends Entity> getBaseClass(){return Entity.class;}
        };
        return original.call(level,family,box,predicate);
    }
}
