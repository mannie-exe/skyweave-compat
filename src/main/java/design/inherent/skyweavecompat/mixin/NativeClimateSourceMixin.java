package design.inherent.skyweavecompat.mixin;

import design.inherent.skyweavecompat.climate.*;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(MultiNoiseBiomeSource.class)
public abstract class NativeClimateSourceMixin implements StableClimateSource {
    @Unique private volatile NativeClimateIndex skyweave$index;
    @Invoker("parameters") protected abstract Climate.ParameterList<Holder<Biome>> skyweave$parameters();
    @Override public Holder<Biome> skyweave$stableBiome(Climate.TargetPoint point) {
        var index=skyweave$index;
        if(index==null)synchronized(this) {
            index=skyweave$index;
            if(index==null)skyweave$index=index=new NativeClimateIndex(skyweave$parameters());
        }
        return index.select(point);
    }
}
