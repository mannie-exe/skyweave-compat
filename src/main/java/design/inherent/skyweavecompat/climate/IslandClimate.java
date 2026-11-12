package design.inherent.skyweavecompat.climate;

import design.inherent.skyweavecompat.terrain.GenerationProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.WorldGenLevel;

/** Temperature altitude only: block placement, light, fluid and collision coordinates stay unchanged. */
public final class IslandClimate {
    private static final ResourceLocation DIMENSION_TYPE = ResourceLocation.parse(GenerationProfile.ID);
    /** Temperature-only coordinate; nested native helpers must not apply the altitude map twice. */
    private static final class ReferencePosition extends BlockPos {
        ReferencePosition(BlockPos source, int y) { super(source.getX(),y,source.getZ()); }
    }
    private IslandClimate() {}
    static boolean referencePosition(BlockPos position) { return position instanceof ReferencePosition; }
    static BlockPos atReference(BlockPos position, int y) {
        return referencePosition(position) ? position : new ReferencePosition(position,y);
    }

    public static boolean applies(LevelReader reader) {
        Level level = reader instanceof Level value ? value : reader instanceof WorldGenLevel world ? world.getLevel() : null;
        return level != null && level.dimensionTypeRegistration().is(DIMENSION_TYPE);
    }

    public static BlockPos temperaturePosition(LevelReader level, BlockPos position) {
        if (referencePosition(position) || !applies(level)) return position;
        var reference = ClimateRuntime.referenceY(level,position);
        // Explicit compatibility fallback while remote tracked metadata is unavailable.
        return atReference(position,reference == null ? 64 : reference);
    }

    public static BlockPos temperaturePosition(BlockPos position) {
        if (referencePosition(position)) return position;
        var level = SurfaceClimateScope.level();
        if (level != null) return temperaturePosition(level,position);
        // Compatibility-only call sites without an admitted world retain the old fallback.
        return atReference(position,64);
    }
}
