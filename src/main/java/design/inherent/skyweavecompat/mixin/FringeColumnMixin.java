package design.inherent.skyweavecompat.mixin;

import design.inherent.skyweavecompat.terrain.FragmentFringes;
import design.inherent.skyweavecompat.terrain.FringeColumn;
import org.sathrek.sky_archipelago.worldgen.generator.field.model.HorizontalSample;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(value = HorizontalSample.class, remap = false)
public abstract class FringeColumnMixin implements FringeColumn {
    @Unique private FragmentFringes.Fragment skyweave$fragment;
    @Override public FragmentFringes.Fragment skyweave$fragment() { return skyweave$fragment; }
    @Override public void skyweave$fragment(FragmentFringes.Fragment fragment) { skyweave$fragment = fragment; }
}
