package design.inherent.skyweavecompat.probe.mixin;

import com.AdamNew.dynamicwaters.HydrologyManager;
import design.inherent.skyweavecompat.probe.WorldScopeProbe;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value=HydrologyManager.class,remap=false,priority=1500)
abstract class WorldScopeCarveWitness {
    @Inject(method="carve",at=@At("HEAD"),require=1)
    private static void observe(double x,double z,int y,int sea,ChunkGenerator generator,LevelHeightAccessor bounds,RandomState random,boolean whole,
            CallbackInfoReturnable<HydrologyManager.RiverCarving> callback) {WorldScopeProbe.envelope(generator,bounds);}
}
