package design.inherent.skyweavecompat.terrain;

import org.sathrek.sky_archipelago.worldgen.generator.surface.SurfaceBlockSelector.SurfaceLayer;

import design.inherent.skyweavecompat.mixin.SurfacePaletteAccessor;
import org.sathrek.sky_archipelago.worldgen.generator.field.IslandShapeArchetype;
import org.sathrek.sky_archipelago.worldgen.generator.field.model.IslandDescriptor;
import net.minecraft.world.level.block.state.BlockState;

/** Material role of the native winning surface contributor, independent of water membership. */
public record SurfaceProvenance(IslandDescriptor owner, Role role) {
    public enum Role { LAND, LAGOON_SHORE, REEF }
    public static SurfaceProvenance fringe(IslandDescriptor owner) {
        return new SurfaceProvenance(owner, owner.archetype() == IslandShapeArchetype.LAGOON ? Role.LAGOON_SHORE
                : owner.archetype() == IslandShapeArchetype.BROKEN_OCEAN_REEF ? Role.REEF : Role.LAND);
    }
    public BlockState material(int x, int z, int depth, BlockState nativeMaterial) {
        if (role == Role.LAND || depth < 0 || depth > 4) return nativeMaterial;
        return palette(x, z).exposedStateAtDepth(depth);
    }
    public SurfaceLayer palette(int x, int z) {
        var palette = role == Role.REEF ? SurfacePaletteAccessor.skyweave$reef(x, z)
                : SurfacePaletteAccessor.skyweave$shore(false);
        return palette.toSurfaceLayer();
    }
}
