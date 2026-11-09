package design.inherent.skyweavecompat.placement;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import design.inherent.skyweavecompat.mixin.PlacementSaltAccessor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderSet;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import design.inherent.skyweavecompat.terrain.IslandSearch;
import design.inherent.skyweavecompat.terrain.ProfiledTerrain;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import org.sathrek.sky_archipelago.config.SkyIslandSettings;
import org.sathrek.sky_archipelago.worldgen.generator.field.IslandField;
import java.util.*;

/** Runtime structure-state view. Registered datapacks and unrelated generators remain unchanged. */
public final class ArchipelagoVillagePlacement extends RandomSpreadStructurePlacement {
    private final RandomSpreadStructurePlacement original;
    private final IslandField field;
    private final SkyIslandSettings settings;
    private final long worldSeed;
    private final NeighborRule.Settings policy;
    private final BiomeSource biomes;
    private final RandomState random;
    private final HolderSet<Biome> allowedBiomes;
    private final Cache<VillagePlan.Anchor, List<VillagePlan.Site>> plans = CacheBuilder.newBuilder().maximumSize(512).build();

    public ArchipelagoVillagePlacement(RandomSpreadStructurePlacement original, IslandField field,
            SkyIslandSettings settings, long worldSeed, NeighborRule.Settings policy,
            BiomeSource biomes, RandomState random, HolderSet<Biome> allowedBiomes) {
        super(4, 2, original.spreadType(), ((PlacementSaltAccessor)original).skyweave$salt());
        this.original = original; this.field = field; this.settings = settings; this.worldSeed = worldSeed; this.policy = policy;
        this.biomes = biomes; this.random = random; this.allowedBiomes = allowedBiomes;
    }

    public VillagePlan.Anchor owner(VillagePlan.Site site) {
        return owner(site.x(), site.z());
    }

    public VillagePlan.Anchor owner(int x, int z) {
        return VillagePlan.owner(x, z, ((VillageAnchors)(Object)field).skyweave$anchorsNear(x, z, settings));
    }

    /** The generation contract's conservative owner domain; no chunks or completed terrain. */
    public IslandSearch<VillagePlan.Site> searchOwners(int x, int z, int radius, long padding) {
        var profile = ((ProfiledTerrain)(Object)field).skyweave$profile();
        int halo = Math.toIntExact(profile.lookupCells() + Math.ceilDiv(policy.maximumGap() + 128L + padding, profile.spacing()));
        return new IslandSearch<>(x, z, radius, profile.spacing(), halo,
                Comparator.comparingInt(VillagePlan.Site::chunkX).thenComparingInt(VillagePlan.Site::chunkZ));
    }

    public List<VillagePlan.Site> searchSites(int cellX, int cellZ, int x, int z, int radius, long padding) {
        var anchor = ((VillageAnchors)(Object)field).skyweave$anchorInCell(cellX, cellZ, settings);
        if (anchor == null) return List.of();
        // VillagePlan.select admits centers only within this reach. Include locate offsets and rounding.
        long reach = radius + (long)anchor.radius() + policy.maximumGap() + 128L + padding;
        long dx = (long)anchor.x() - x, dz = (long)anchor.z() - z;
        return dx * dx + dz * dz > reach * reach ? List.of() : sites(anchor);
    }

    public List<VillagePlan.Site> sites(VillagePlan.Anchor anchor) {
        if (VillageDistribution.forAnchor(anchor.seed()) == 0) return List.of();
        var cached = plans.getIfPresent(anchor);
        if (cached != null) return cached;
        int reach = anchor.radius() + policy.maximumGap() + 128;
        int minX = Math.floorDiv(Math.floorDiv(anchor.x() - reach, 16), spacing());
        int maxX = Math.floorDiv(Math.floorDiv(anchor.x() + reach, 16), spacing());
        int minZ = Math.floorDiv(Math.floorDiv(anchor.z() - reach, 16), spacing());
        int maxZ = Math.floorDiv(Math.floorDiv(anchor.z() + reach, 16), spacing());
        var candidates = new ArrayList<VillagePlan.Site>();
        int surfaceY = anchor.surfaceY();
        for (int x = minX; x <= maxX; x++) for (int z = minZ; z <= maxZ; z++) {
            var chunk = getPotentialStructureChunk(worldSeed, x * spacing(), z * spacing());
            var site = new VillagePlan.Site(chunk.x, chunk.z);
            if (!staysWithAnchor(anchor, site)) continue;
            var footprint = new NeighborRule.Footprint(site.x() - 72, site.z() - 72, site.x() + 72, site.z() + 72);
            if (policy.enabled() && NeighborRule.coarseDecision(footprint, policy, new IslandTerrain(field, settings, anchor))
                    != NeighborRule.Decision.ACCEPTED) continue;
            // Cheap eligibility screen. Native Jigsaw still checks the actual randomized start-piece center.
            if (allowedBiomes.contains(biomes.getNoiseBiome(site.x() >> 2, surfaceY >> 2, site.z() >> 2, random.sampler()))) {
                candidates.add(site);
            }
        }
        var selected = VillagePlan.select(anchor, candidates, this::owner, policy.minimumGap(), policy.maximumGap());
        plans.put(anchor, selected);
        return selected;
    }

    private boolean staysWithAnchor(VillagePlan.Anchor anchor, VillagePlan.Site site) {
        // Native SKY retries can move two chunks. Reserve another 160 blocks for the Jigsaw layout.
        // Nearest-anchor regions are convex, so all four corners keep the entire reserved square in one region.
        for (int dx : new int[]{-12, 12}) for (int dz : new int[]{-12, 12}) {
            if (!anchor.equals(owner(new VillagePlan.Site(site.chunkX() + dx, site.chunkZ() + dz)))) return false;
        }
        return true;
    }

    @Override protected boolean isPlacementChunk(ChunkGeneratorStructureState state, int x, int z) {
        if (!super.isPlacementChunk(state, x, z)) return false;
        var site = new VillagePlan.Site(x, z);
        var anchor = owner(site);
        return anchor != null && sites(anchor).contains(site);
    }
    @Override public boolean applyAdditionalChunkRestrictions(int x, int z, long seed) {
        return original.applyAdditionalChunkRestrictions(x, z, seed);
    }
    @Override public boolean applyInteractionsWithOtherStructures(ChunkGeneratorStructureState state, int x, int z) {
        return original.applyInteractionsWithOtherStructures(state, x, z);
    }
    @Override public BlockPos getLocatePos(ChunkPos chunk) { return original.getLocatePos(chunk); }
}
