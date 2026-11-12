package design.inherent.skyweavecompat.mixin;

import design.inherent.skyweavecompat.terrain.ParentLandBody;
import org.sathrek.sky_archipelago.worldgen.generator.field.internal.IslandDensityEvaluator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(value = IslandDensityEvaluator.PreparedDensity.class, remap = false)
public abstract class PreparedDensityMixin implements ParentLandBody {
    @Unique private Frame skyweave$parentLandBody;
    @Override public Frame skyweave$parentLandBody() { return skyweave$parentLandBody; }
    @Override public void skyweave$parentLandBody(Frame frame) {
        if (frame == null || skyweave$parentLandBody != null)
            throw new IllegalStateException("Parent land body must bind once before publication");
        skyweave$parentLandBody = frame;
    }
}
