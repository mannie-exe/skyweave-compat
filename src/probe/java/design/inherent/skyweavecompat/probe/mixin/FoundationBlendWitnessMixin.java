package design.inherent.skyweavecompat.probe.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import design.inherent.skyweavecompat.probe.FoundationBlendWrites;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

@Pseudo
@Mixin(targets="org.sathrek.sky_archipelago.worldgen.generator.surface.SkyIslandSurfacePass",remap=false)
public abstract class FoundationBlendWitnessMixin {
    @WrapOperation(method={"blendStructureBaseFootprint","blendMonumentBaseFootprint"},at=@At(value="INVOKE",
            target="Lorg/sathrek/sky_archipelago/worldgen/generator/surface/SkyIslandSurfacePass;setSurfaceBlock(Lnet/minecraft/world/level/chunk/ChunkAccess;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;JI)V"),require=2)
    private static void observe(ChunkAccess chunk,BlockPos pos,BlockState state,long seed,int deepslate,Operation<Void> original) {
        FoundationBlendWrites.record(pos,state);original.call(chunk,pos,state,seed,deepslate);
    }
}
