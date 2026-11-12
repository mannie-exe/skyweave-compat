package design.inherent.skyweavecompat.terrain.city;

import design.inherent.skyweavecompat.terrain.*;
import java.util.*;
import java.util.function.Supplier;
import net.minecraft.core.QuartPos;
import net.minecraft.resources.ResourceLocation;
import org.sathrek.sky_archipelago.worldgen.generator.core.SkyIslandDeepDarkBiomeProjector;
import org.sathrek.sky_archipelago.worldgen.generator.field.TerrainColumn;
import org.sathrek.sky_archipelago.worldgen.generator.field.model.IslandDescriptor;
import org.sathrek.sky_archipelago.worldgen.generator.structure.v2.common.PlacementRequest;
import org.sathrek.sky_archipelago.worldgen.generator.structure.v2.underground.UndergroundCanonicalSitePlanner;

/** One native city placement attempt; no world-global flag or retained world references. */
public final class CityPlacementScope {
    public record Site(UndergroundCanonicalSitePlanner.Site nativeSite, IslandDescriptor host,
            boolean hollow, int floorY, int ceilingY) {}
    private static final ResourceLocation CITY = ResourceLocation.withDefaultNamespace("ancient_city");
    private static final ThreadLocal<Context> ACTIVE = new ThreadLocal<>();
    private static final class Context {
        final PlacementRequest request;
        final Map<Long, TerrainColumn> exterior = new HashMap<>();
        final Map<Long, List<TerrainColumn>> solid = new HashMap<>();
        Site site;
        Context(PlacementRequest request) { this.request = request; }
    }
    private CityPlacementScope() {}
    public static <T> T run(PlacementRequest request, Supplier<T> action) {
        var previous = ACTIVE.get();
        boolean applies = CITY.equals(request.structureId()) && request.generationContext() != null
                && request.generationContext().generator() != null
                && ((GeologicalGenerator)(Object)request.generationContext().generator()).skyweave$isEnabled()
                && ((ProfiledTerrain)(Object)request.islandField()).skyweave$profile() != null;
        if (CITY.equals(request.structureId())) org.sathrek.sky_archipelago.worldgen.benchmark.StructureBenchmarkRecorder
                .increment("skyweaveCityScope." + (applies ? "active" : "ordinary"));
        if (applies) ACTIVE.set(new Context(request)); else ACTIVE.remove();
        try { return action.get(); }
        finally { if (previous == null) ACTIVE.remove(); else ACTIVE.set(previous); }
    }
    public static boolean active() { return ACTIVE.get() != null; }
    public static Site site() { var context = ACTIVE.get(); return context == null ? null : context.site; }
    public static PlacementRequest request() { return ACTIVE.get().request; }
    public static boolean isStructure(Object structure) { return active() && request().structure() == structure; }

    public static void select(UndergroundCanonicalSitePlanner.Site site) {
        var context = ACTIVE.get();
        context.site = null; context.exterior.clear(); context.solid.clear();
        var request = context.request;
        var host = site.host().terrainPlan() == null ? null : site.host().terrainPlan().descriptor();
        if (host == null || host.archetype().waterBased()) return;
        int x = site.generationChunk().getMiddleBlockX(), z = site.generationChunk().getMiddleBlockZ();
        var outer = ((InteriorField)(Object)request.islandField()).skyweave$exteriorOf(host, x, z, request.settings());
        if (!outer.exists()) return;
        var styles=((StyledTerrain)(Object)request.islandField()).skyweave$styles();
        boolean hollow = IslandInteriors.kind(host, InteriorConfiguration.profile(),
                styles==null?IslandStyles.Kind.NATIVE:styles.style(host).kind()) == IslandInteriors.Kind.HOLLOW;
        int preferred = IslandGeometry.geologicalFrame(host).toWorld(-27);
        if (!hollow) {
            int min = outer.bottomY() + 16, max = outer.topY() - 72;
            if (min <= max) context.site = new Site(site, host, false, Math.clamp(preferred, min, max), outer.topY());
            return;
        }
        var segments = request.islandField().sampleSolidSegments(x, z, request.settings());
        int bestHeight = 0;
        for (int i = 0; i + 1 < segments.size(); i++) {
            var roof = segments.get(i); var floor = segments.get(i + 1);
            int height = roof.bottomY() - floor.topY() - 1;
            if (height < 48 || roof.thickness() < InteriorConfiguration.profile().shellThickness()
                    || !outer.contains(floor.topY()) || !outer.contains(roof.bottomY())) continue;
            if (height > bestHeight) {
                bestHeight = height;
                context.site = new Site(site, host, true, floor.topY() + 1, roof.bottomY() - 1);
            }
        }
    }
    public static TerrainColumn exterior(int x, int z) {
        var context = ACTIVE.get();
        return context.exterior.computeIfAbsent(key(x,z), ignored -> ((InteriorField)(Object)context.request.islandField())
                .skyweave$exteriorOf(context.site.host(), x, z, context.request.settings()));
    }
    public static List<TerrainColumn> solid(int x, int z) {
        var context = ACTIVE.get();
        return context.solid.computeIfAbsent(key(x,z), ignored -> context.request.islandField()
                .sampleSolidSegments(x, z, context.request.settings()));
    }
    public static boolean validBiome(int x, int y, int z) {
        var request = request(); var context = request.generationContext();
        try (var ignored = SkyIslandDeepDarkBiomeProjector.activateForStructureGeneration(
                context.generator(), context.randomState(), request.chunk(), request.structure())) {
            var biome = context.biomeSource().getNoiseBiome(QuartPos.fromBlock(x), QuartPos.fromBlock(y),
                    QuartPos.fromBlock(z), context.randomState().sampler());
            return request.structure().biomes().contains(biome);
        }
    }
    private static long key(int x, int z) { return (long)x << 32 ^ (z & 0xffffffffL); }
}
