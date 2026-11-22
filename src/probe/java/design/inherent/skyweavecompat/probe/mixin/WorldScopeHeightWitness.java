package design.inherent.skyweavecompat.probe.mixin;

import com.AdamNew.dynamicwaters.terrain.CoarseHeightField;
import design.inherent.skyweavecompat.probe.WorldScopeProbe;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import org.spongepowered.asm.mixin.Mixin;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;

@Mixin(value=CoarseHeightField.class,remap=false,priority=1500)
abstract class WorldScopeHeightWitness {
    @WrapMethod(method="compute")
    private static short[] observe(int x,int z,ChunkGenerator generator,RandomState random,Operation<short[]> original) {
        WorldScopeProbe.height(generator);return original.call(x,z,generator,random);
    }
}
