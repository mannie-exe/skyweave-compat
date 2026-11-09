package design.inherent.skyweavecompat.water;

import com.AdamNew.dynamicwaters.HydrologyManager;
import design.inherent.skyweavecompat.terrain.InteriorConfiguration;
import design.inherent.skyweavecompat.terrain.TerrainStage;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.levelgen.RandomState;
import org.sathrek.sky_archipelago.worldgen.generator.core.SkyIslandChunkGenerator;
import org.sathrek.sky_archipelago.worldgen.generator.field.TerrainColumn;
import org.sathrek.sky_archipelago.worldgen.generator.field.model.ColumnData;
import org.sathrek.sky_archipelago.worldgen.generator.field.model.ColumnProfile;
import org.sathrek.sky_archipelago.worldgen.generator.river.RiverColumnPlan;

/** Translates upstream watershed cuts into the existing solid/fluid column contract. */
public final class IslandWaterColumns {
    private final SkyIslandChunkGenerator generator;
    private final RandomState random;
    private final LevelHeightAccessor envelope;
    private record Protection(long host, int x, int z, int bottom, int top) {}
    private final com.google.common.cache.Cache<Protection, Integer> beds =
            com.google.common.cache.CacheBuilder.newBuilder().maximumSize(8192).build();

    public IslandWaterColumns(SkyIslandChunkGenerator generator, RandomState random) {
        var rivers = generator.resolvedSkyIslandSettings().rivers();
        if (rivers.enabled() || rivers.lakesEnabled())
            throw new IllegalStateException("Disable native rivers and lakes in skyweave_compat-rivers.json before enabling Dynamic Waters island hydrology");
        if (!com.AdamNew.dynamicwaters.Config.vanillaNormalRivers())
            throw new IllegalStateException("Skyweave island hydrology requires Generation.normalRiverMode=VANILLA in dynamicwaters-common.toml; NONE also rewrites climate ridges");
        if (com.AdamNew.dynamicwaters.Config.MOUNTAIN_RIVER_TUNNELS.get())
            throw new IllegalStateException("Skyweave island hydrology does not support mountainRiverTunnels; disable that option or this adaptation");
        this.generator = generator;
        this.random = random;
        envelope = LevelHeightAccessor.create(generator.getMinY(), generator.getGenDepth());
    }

    /** Ask drainage against the exterior before subtracting cavities; never call completed columns here. */
    public int protectedFloor(org.sathrek.sky_archipelago.worldgen.generator.field.model.IslandDescriptor host,
            int x, int z, TerrainColumn exterior) {
        if (host.archetype().waterBased()) return Integer.MIN_VALUE;
        var key = new Protection(host.seed(), x, z, exterior.bottomY(), exterior.topY());
        try {
            return beds.get(key, () -> TerrainStage.EXTERIOR.query(() -> {
                var field = ((design.inherent.skyweavecompat.terrain.GeologicalGenerator)(Object)generator).skyweave$field(random);
                var boundary = (design.inherent.skyweavecompat.terrain.InteriorField)(Object)field;
                var raw = boundary.skyweave$planningColumn(x, z, generator.resolvedSkyIslandSettings(), TerrainStage.EXTERIOR);
                // A union's water belongs to its selected exterior, never another overlapping contributor.
                if (raw.dominantDescriptor() == null || raw.dominantDescriptor().seed() != host.seed()
                        || boundary.skyweave$isFringe(host, x, z, generator.resolvedSkyIslandSettings())) return Integer.MIN_VALUE;
                var cut = HydrologyManager.calculateRiverCarving(x, z, exterior.topY(), generator.getSeaLevel(), generator, envelope, random);
                return fitToSupport(cut, exterior, envelope.getMaxBuildHeight())
                        ? cut.targetY - InteriorConfiguration.profile().shellThickness() + 1 : Integer.MIN_VALUE;
            }));
        } catch (java.util.concurrent.ExecutionException error) {
            throw new IllegalStateException("Cannot plan protected island water bed", error.getCause());
        }
    }

    public ColumnData apply(int x, int z, ColumnData raw) {
        if (TerrainStage.current() != TerrainStage.COMPLETE || raw == null || raw.segments().isEmpty()
                || raw.dominantDescriptor() == null || raw.dominantDescriptor().archetype().waterBased()) return raw;
        var top = raw.segments().getFirst();
        var cut = HydrologyManager.calculateRiverCarving(x, z, top.topY(), generator.getSeaLevel(), generator, envelope, random);
        if (!supported(cut, top, envelope.getMaxBuildHeight())) return raw;
        // Preserve the shell above a cavity and reject unsupported routes instead of
        // constructing a foundation down through empty sky. The watershed owns the cut.
        RiverColumnPlan river;
        if (cut.waterLevel > cut.targetY && (cut.isLake || cut.waterLevel <= top.topY() + 2)) {
            river = cut.isLake
                    ? RiverColumnPlan.lake(top.topY(), cut.targetY, cut.waterLevel,
                            cut.distanceFromCenter, cut.channelSlope, 0, 0)
                    : RiverColumnPlan.channel(top.topY(), cut.targetY, cut.waterLevel,
                            // DW owns channel banks/discharge. The native sealer
                            // would otherwise replace exposed channels with dams.
                            cut.distanceFromCenter, cut.channelSlope, 0, 0, true);
        } else {
            river = RiverColumnPlan.bank(top.topY(), cut.targetY, true, 0,
                    cut.distanceFromCenter, cut.channelSlope, 0, 0);
        }
        var segments = new ArrayList<>(raw.segments());
        segments.set(0, new TerrainColumn(top.bottomY(), river.solidSurfaceY()));
        var p = raw.profile();
        var profile = new ColumnProfile(p.bottomY(), river.solidSurfaceY(), p.topCap(), p.cliffBand(),
                p.undersideFringe(), p.vegetationHangZone(), p.family());
        return new ColumnData(List.copyOf(segments), profile, raw.dominantDescriptor(), raw.waterArchetype(),
                river, raw.overlayExposedSegments());
    }

    public static boolean supported(HydrologyManager.RiverCarving cut, TerrainColumn top, int maximumY) {
        return cut.modifies && (cut.isMountainRiver || cut.isLake) && !cut.isPillar
                && (cut.cavernRoofY == -999 || cut.cavernRoofY == maximumY)
                && cut.targetY >= minimumBed(top)
                && cut.targetY <= top.topY() && cut.waterLevel < maximumY;
    }

    /** A native no-op also needs inert heights: post-carver repair reads waterLevel when modifies is false. */
    public static HydrologyManager.RiverCarving unchanged(int originalY, int maximumY) {
        var cut = new HydrologyManager.RiverCarving();
        cut.targetY = originalY;
        cut.waterLevel = originalY;
        cut.sealFloorY = originalY;
        cut.cavernRoofY = maximumY;
        return cut;
    }

    /** Keep an existing supported lake floor instead of excavating through its retained cavity roof. */
    public static boolean fitToSupport(HydrologyManager.RiverCarving cut, TerrainColumn top, int maximumY) {
        if (cut.modifies && cut.isLake && minimumBed(top) <= top.topY()) {
            cut.targetY = Math.max(minimumBed(top), Math.min(cut.targetY, top.topY()));
            // A constrained bed must still leave room for the proposed lake.
            if (cut.targetY >= cut.waterLevel) return false;
        }
        return supported(cut, top, maximumY);
    }

    private static int minimumBed(TerrainColumn top) {
        // TerrainColumn endpoints are inclusive: bottom=410, top=421 already contains twelve blocks.
        return top.bottomY() + InteriorConfiguration.profile().shellThickness() - 1;
    }
}
