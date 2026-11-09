package design.inherent.skyweavecompat.mixin;

import org.sathrek.sky_archipelago.worldgen.generator.surface.SurfaceBlockSelector;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Native material selectors only; no synthetic water-column membership. */
@Mixin(value = SurfaceBlockSelector.class, remap = false)
public interface SurfacePaletteAccessor {
    @Invoker("waterArchetypeShoreProfile")
    static SurfaceBlockSelector.SurfaceProfile skyweave$shore(boolean rocky) { throw new AssertionError(); }
    @Invoker("dryReefProfile")
    static SurfaceBlockSelector.SurfaceProfile skyweave$reef(int x, int z) { throw new AssertionError(); }
}
