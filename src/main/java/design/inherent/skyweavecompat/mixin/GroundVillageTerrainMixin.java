package design.inherent.skyweavecompat.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import design.inherent.skyweavecompat.terrain.structure.GroundVillageTerrain;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChunkGenerator.class)
public abstract class GroundVillageTerrainMixin {
    @WrapMethod(method="applyBiomeDecoration")
    private void skyweave$ground(WorldGenLevel level,ChunkAccess chunk,StructureManager structures,Operation<Void> original) {
        GroundVillageTerrain.decorate((ChunkGenerator)(Object)this,level,chunk,structures,()->original.call(level,chunk,structures));
    }
    @Inject(method="applyBiomeDecoration",at=@At(value="INVOKE",target="Lnet/minecraft/SharedConstants;debugVoidTerrain(Lnet/minecraft/world/level/ChunkPos;)Z"))
    private void skyweave$prepareGround(WorldGenLevel level,ChunkAccess chunk,StructureManager structures,CallbackInfo callback) {
        GroundVillageTerrain.prepare();
    }
}
