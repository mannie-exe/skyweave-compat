package design.inherent.skyweavecompat.mixin;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import design.inherent.skyweavecompat.terrain.*;
import java.util.*;
import org.sathrek.sky_archipelago.config.SkyIslandSettings;
import org.sathrek.sky_archipelago.worldgen.generator.field.IslandField;
import org.sathrek.sky_archipelago.worldgen.generator.field.model.*;
import org.sathrek.sky_archipelago.worldgen.generator.field.cache.ClusterCacheKey;
import org.sathrek.sky_archipelago.worldgen.generator.field.cache.IslandFieldCaches;
import org.sathrek.sky_archipelago.worldgen.generator.field.internal.IslandClusterSampler;
import org.sathrek.sky_archipelago.worldgen.generator.field.internal.IslandDescriptorFactory;
import org.sathrek.sky_archipelago.worldgen.generator.field.internal.IslandNoise;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Candidates are cached independently from decisions, so rejection never recurses. */
@Mixin(value = IslandField.class, remap = false)
public abstract class ClusterPlacementMixin implements ClusterField {
    @Shadow @Final private long layoutSeed;
    @Shadow @Final private IslandNoise noise;
    @Shadow @Final private IslandClusterSampler clusterSampler;
    @Shadow @Final private BiomeTerrainSampler biomeTerrainSampler;
    @Shadow @Final private IslandFieldCaches caches;
    @Shadow private ClusterDescriptor sampleCluster(int x, int z, SkyIslandSettings settings) { throw new AssertionError(); }
    @Shadow private List<IslandTerrainPlan> buildClusterTerrainPlans(ClusterDescriptor cluster, SkyIslandSettings settings) { throw new AssertionError(); }
    @Shadow @Final private IslandDescriptorFactory descriptorFactory;
    @Shadow @Final private org.sathrek.sky_archipelago.worldgen.generator.field.internal.IslandShapeSampler shapeSampler;
    @Shadow private IslandTerrainPlan terrainPlanForDescriptor(IslandDescriptor island, SkyIslandSettings settings) { throw new AssertionError(); }

    @Unique private List<IslandDescriptor> skyweave$descriptors(ClusterDescriptor cluster, SkyIslandSettings settings) {
        return buildClusterTerrainPlans(cluster, settings).stream().map(IslandTerrainPlan::descriptor).toList();
    }

    @Inject(method = "createClusterTerrainPlans", at = @At("RETURN"), cancellable = true)
    private void spires(ClusterDescriptor cluster, SkyIslandSettings settings,
            CallbackInfoReturnable<List<IslandTerrainPlan>> result) {
        if (skyweave$layoutProfile() == null || cluster.archetype().waterBased() && !OceanConfiguration.profile().spireCompanions()
                || !settings.advanced().clusterCompanionIslandsEnabled() || cluster.spireCount() == 0) return;
        var plans = new ArrayList<>(result.getReturnValue());
        for (int i = 0; i < cluster.spireCount(); i++) {
            var spire = terrainPlanForDescriptor(descriptorFactory.createSpireDescriptor(cluster, settings, i), settings);
            if (!cluster.archetype().waterBased() || skyweave$clearOceanSpire(plans.getFirst(), spire)) plans.add(spire);
        }
        result.setReturnValue(List.copyOf(plans));
    }

    @Unique private boolean skyweave$clearOceanSpire(IslandTerrainPlan ocean, IslandTerrainPlan spire) {
        int gap = OceanConfiguration.profile().spireClearance();
        var a = ocean.bounds(); var b = spire.bounds();
        int minX = Math.max(a.minX(), b.minX() - gap), maxX = Math.min(a.maxX(), b.maxX() + gap);
        int minZ = Math.max(a.minZ(), b.minZ() - gap), maxZ = Math.min(a.maxZ(), b.maxZ() + gap);
        // Integer footprint exhaustion is bounded by the small spire's support envelope.
        // Sampling a coarse grid could miss narrow native reef/rim remnants.
        for (int x = minX; x <= maxX; x++) for (int z = minZ; z <= maxZ; z++) {
            var horizontal = shapeSampler.sampleHorizontal(ocean, x, z);
            if (horizontal.inInfluence() && (((PerimeterColumn)(Object)horizontal).skyweave$fragment() != null
                    || shapeSampler.sampleWaterArchetypeColumnProfile(ocean.descriptor(), horizontal).exists()))
                return false;
        }
        return true;
    }

    @Unique private final Cache<ClusterCacheKey, Optional<ClusterDescriptor>> skyweave$candidates = CacheBuilder.newBuilder().maximumSize(8192).build();
    @Unique private final Cache<ClusterCacheKey, Boolean> skyweave$decisions = CacheBuilder.newBuilder().maximumSize(8192).build();

    @Unique private GenerationProfile skyweave$layoutProfile() { return ((ProfiledTerrain)this).skyweave$profile(); }

    @Unique private ClusterDescriptor skyweave$candidate(int x, int z, SkyIslandSettings settings) {
        var key = caches.clusterKey(x, z, settings);
        var cached = skyweave$candidates.getIfPresent(key);
        if (cached != null) return cached.orElse(null);
        ClusterDescriptor candidate = null;
        var profile = skyweave$layoutProfile();
        if (noise.sample01(x, z, 101) < profile.activation()) {
            var sampled = clusterSampler.sampleClusterDescriptor(x, z, layoutSeed, profile.spacing(), settings, 337, false);
            // Admission follows the displaced anchor and its surface datum, not the old cell center.
            var biome = biomeTerrainSampler.sample(sampled.centerX(), sampled.centerY() + profile.anchorPlateau(), sampled.centerZ());
            if (biome.oceanIslandsEligible()) {
                if (OceanConfiguration.profile().admits(sampled.seed())) {
                    var archetype = clusterSampler.selectAnchorArchetype(x, z, settings, true);
                    if (archetype != null) candidate = sampled.withArchetype(archetype);
                }
            } else candidate = sampled;
        }
        skyweave$candidates.put(key, Optional.ofNullable(candidate));
        return candidate;
    }

    @Unique private ClusterLayout.Candidate skyweave$supports(ClusterDescriptor cluster, SkyIslandSettings settings) {
        return new ClusterLayout.Candidate(new ClusterLayout.Cell(cluster.cellX(), cluster.cellZ()),
                IslandNoise.mix(layoutSeed + 809, cluster.cellX(), cluster.cellZ()),
                buildClusterTerrainPlans(cluster, settings).stream().map(plan -> {
                    var b = plan.bounds();
                    var island = plan.descriptor();
                    return new ClusterLayout.Support(island.centerX(), island.centerZ(),
                            Math.max(island.centerX() - b.minX(), b.maxX() - island.centerX()),
                            Math.max(island.centerZ() - b.minZ(), b.maxZ() - island.centerZ()));
                }).toList());
    }

    @Inject(method = "sampleCluster", at = @At("HEAD"), cancellable = true)
    private void accepted(int x, int z, SkyIslandSettings settings, CallbackInfoReturnable<ClusterDescriptor> result) {
        var profile = skyweave$layoutProfile();
        if (profile == null) return;
        var candidate = skyweave$candidate(x, z, settings);
        if (candidate == null) { result.setReturnValue(null); return; }
        var key = caches.clusterKey(x, z, settings);
        var accepted = skyweave$decisions.getIfPresent(key);
        if (accepted == null) {
            accepted = ClusterLayout.accepts(skyweave$supports(candidate, settings), profile, (cx, cz) -> {
                var other = skyweave$candidate(cx, cz, settings);
                return other == null ? null : skyweave$supports(other, settings);
            });
            skyweave$decisions.put(key, accepted);
        }
        result.setReturnValue(accepted ? candidate : null);
    }

    @Override public List<IslandDescriptor> skyweave$members(int x, int z, SkyIslandSettings settings) {
        var cluster = sampleCluster(x, z, settings);
        return cluster == null ? List.of() : skyweave$descriptors(cluster, settings);
    }

    @Override public ClusterLayout.Cell skyweave$owner(IslandDescriptor descriptor, SkyIslandSettings settings) {
        var profile = skyweave$layoutProfile();
        int cx = Math.floorDiv(descriptor.centerX(), profile.spacing());
        int cz = Math.floorDiv(descriptor.centerZ(), profile.spacing());
        int range = profile.lookupCells();
        for (int dx = -range; dx <= range; dx++) for (int dz = -range; dz <= range; dz++) {
            if (skyweave$members(cx + dx, cz + dz, settings).contains(descriptor)) return new ClusterLayout.Cell(cx + dx, cz + dz);
        }
        throw new IllegalStateException("Island has no accepted cluster owner");
    }

    @Inject(method = "collectIslandPreviews", at = @At("HEAD"), cancellable = true)
    private void previews(int x, int z, int radius, SkyIslandSettings settings, boolean intersecting,
            CallbackInfoReturnable<List<IslandPreview>> result) {
        var profile = skyweave$layoutProfile();
        if (profile == null) return;
        int range = profile.lookupCells(), spacing = profile.spacing();
        var previews = new ArrayList<IslandPreview>();
        for (int cx = Math.floorDiv(x - radius, spacing) - range; cx <= Math.floorDiv(x + radius, spacing) + range; cx++) {
            for (int cz = Math.floorDiv(z - radius, spacing) - range; cz <= Math.floorDiv(z + radius, spacing) + range; cz++) {
                var cluster = sampleCluster(cx, cz, settings);
                if (cluster == null) continue;
                for (var plan : buildClusterTerrainPlans(cluster, settings)) {
                    var island = plan.descriptor();
                    int reach = plan.horizontalReach();
                    long dx = (long)island.centerX() - x, dz = (long)island.centerZ() - z;
                    if (intersecting) {
                        var bounds = plan.bounds();
                        dx = Math.max(0L, Math.max((long)bounds.minX() - x, (long)x - bounds.maxX()));
                        dz = Math.max(0L, Math.max((long)bounds.minZ() - z, (long)z - bounds.maxZ()));
                    }
                    if (dx * dx + dz * dz <= (long)radius * radius) previews.add(new IslandPreview(island.archetype(),
                            island.family(), cluster.heightBand(), island.centerX(), island.centerY(), island.centerZ(),
                            island.maxRadius(), island.plateauHeight(), island.hangDepth(), reach, plan));
                }
            }
        }
        previews.sort(Comparator.comparingInt(IslandPreview::x).thenComparingInt(IslandPreview::z)
                .thenComparingInt(IslandPreview::radius).thenComparingInt(IslandPreview::y));
        result.setReturnValue(previews);
    }
}
