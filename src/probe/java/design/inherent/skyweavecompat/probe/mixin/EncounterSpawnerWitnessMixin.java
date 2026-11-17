package design.inherent.skyweavecompat.probe.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import design.inherent.skyweavecompat.probe.EncounterProbe;
import java.util.function.Function;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.*;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Read-only observation of the actual native loop, inert outside the selected fixture. */
@Mixin(BaseSpawner.class)
public abstract class EncounterSpawnerWitnessMixin {
    @WrapOperation(method="serverTick",at=@At(value="INVOKE",target="Lnet/minecraft/world/entity/SpawnPlacements;checkSpawnRules(Lnet/minecraft/world/entity/EntityType;Lnet/minecraft/world/level/ServerLevelAccessor;Lnet/minecraft/world/entity/MobSpawnType;Lnet/minecraft/core/BlockPos;Lnet/minecraft/util/RandomSource;)Z"),require=1)
    private boolean rule(EntityType<?> type,ServerLevelAccessor level,MobSpawnType reason,BlockPos pos,RandomSource random,Operation<Boolean> original) {
        if(EncounterProbe.observing)EncounterProbe.rules.add(BuiltInRegistries.ENTITY_TYPE.getKey(type).toString());
        return original.call(type,level,reason,pos,random);
    }
    @WrapOperation(method="serverTick",at=@At(value="INVOKE",target="Lnet/minecraft/world/entity/EntityType;loadEntityRecursive(Lnet/minecraft/nbt/CompoundTag;Lnet/minecraft/world/level/Level;Ljava/util/function/Function;)Lnet/minecraft/world/entity/Entity;"),require=1)
    private Entity load(CompoundTag tag,Level level,Function<Entity,Entity> position,Operation<Entity> original) {
        if(EncounterProbe.observing)EncounterProbe.loads.add(tag.copy());return original.call(tag,level,position);
    }
    @WrapOperation(method="serverTick",at=@At(value="INVOKE",target="Lnet/minecraft/world/entity/EntityType;getSpawnAABB(DDD)Lnet/minecraft/world/phys/AABB;"),require=1)
    private AABB box(EntityType<?> type,double x,double y,double z,Operation<AABB> original) {
        var box=original.call(type,x,y,z);if(EncounterProbe.observing)EncounterProbe.boxes.add(box);return box;
    }
}
