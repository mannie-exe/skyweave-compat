package design.inherent.skyweavecompat.mixin;

import design.inherent.skyweavecompat.terrain.GranularEdges;
import design.inherent.skyweavecompat.terrain.PerimeterColumn;
import org.sathrek.sky_archipelago.worldgen.generator.field.model.HorizontalSample;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(value = HorizontalSample.class, remap = false)
public abstract class PerimeterColumnMixin implements PerimeterColumn {
    @Unique private GranularEdges.Fragment skyweave$fragment;
    @Override public GranularEdges.Fragment skyweave$fragment() { return skyweave$fragment; }
    @Override public void skyweave$fragment(GranularEdges.Fragment fragment) { skyweave$fragment = fragment; }
}
