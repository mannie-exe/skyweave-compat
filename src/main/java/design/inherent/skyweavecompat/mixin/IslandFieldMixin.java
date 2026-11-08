package design.inherent.skyweavecompat.mixin;

import org.sathrek.sky_archipelago.worldgen.generator.field.model.ColumnData;
import org.sathrek.sky_archipelago.worldgen.generator.field.model.IslandDescriptor;
import org.sathrek.sky_archipelago.worldgen.generator.field.model.IslandFamily;
import design.inherent.skyweavecompat.terrain.*;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.sathrek.sky_archipelago.worldgen.generator.field.model.IslandTerrainPlan;
import java.util.List;
import java.util.ArrayList;
import design.inherent.skyweavecompat.placement.VillageAnchors;
import design.inherent.skyweavecompat.placement.VillagePlan;
import design.inherent.skyweavecompat.terrain.ProfiledTerrain;
import org.sathrek.sky_archipelago.config.SkyIslandSettings;
import org.sathrek.sky_archipelago.worldgen.generator.field.IslandField;
import org.sathrek.sky_archipelago.worldgen.generator.field.internal.IslandClusterSampler;
import org.sathrek.sky_archipelago.worldgen.generator.field.internal.IslandDescriptorFactory;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = IslandField.class, remap = false)
public abstract class IslandFieldMixin implements ProfiledTerrain, GeologicalField, VillageAnchors, VeinField, InteriorField, StyledTerrain {
    @Shadow @Final private IslandClusterSampler clusterSampler;
    @Shadow @Final private IslandDescriptorFactory descriptorFactory;
    @Shadow @Final private org.sathrek.sky_archipelago.worldgen.generator.field.internal.IslandShapeSampler shapeSampler;
    @Shadow @Final private org.sathrek.sky_archipelago.worldgen.generator.field.internal.IslandTerrainPlanFactory terrainPlanFactory;
    @Shadow @Final private org.sathrek.sky_archipelago.worldgen.generator.field.internal.IslandColumnResolver columnResolver;
    @Shadow @Final private org.sathrek.sky_archipelago.worldgen.generator.field.internal.IslandDensityEvaluator densityEvaluator;
    @Shadow @Final private org.sathrek.sky_archipelago.worldgen.generator.field.model.BiomeTerrainSampler biomeTerrainSampler;
    @Shadow private IslandTerrainPlan terrainPlanForDescriptor(IslandDescriptor host, SkyIslandSettings settings) { throw new AssertionError(); }
    @Unique private volatile GenerationProfile skyweave$profile;
    @Unique private IslandStyles skyweave$styles;
    @Override public IslandStyles skyweave$styles() { return skyweave$styles; }
    @Override public void skyweave$styles(IslandStyles styles) { skyweave$styles = styles; }
    @Unique private volatile IslandVeins skyweave$veins;
    @Unique private boolean skyweave$veinsConfigured;
    @Unique private boolean skyweave$interiorsConfigured;
    @Shadow abstract ColumnData sampleColumnData(int x, int z, SkyIslandSettings settings);
    @Shadow public abstract org.sathrek.sky_archipelago.worldgen.generator.terrain.SkyIslandChunkTerrainSnapshot
            sampleChunkTerrainSnapshot(net.minecraft.world.level.ChunkPos chunk, SkyIslandSettings settings);
    @Shadow private ColumnData resolveBaseColumnData(int x, int z, SkyIslandSettings settings) { throw new AssertionError(); }
    @Override public ColumnData skyweave$planningColumn(int x, int z, SkyIslandSettings settings, TerrainStage stage) {
        if (stage == TerrainStage.COMPLETE) throw new IllegalArgumentException("Planning must select an earlier terrain stage");
        return stage.query(() -> resolveBaseColumnData(x, z, settings));
    }
    @Override public synchronized void skyweave$configureInteriors(net.minecraft.world.level.levelgen.RandomState random,
            InteriorProfile profile) {
        if (skyweave$interiorsConfigured) return;
        ((InteriorResolver)(Object)columnResolver).skyweave$interiors(new IslandInteriors(random, profile, skyweave$styles));
        skyweave$interiorsConfigured = true;
    }
    @Override public List<org.sathrek.sky_archipelago.worldgen.generator.field.TerrainColumn> skyweave$exteriorAt(
            int x, int z, SkyIslandSettings settings) {
        return skyweave$planningColumn(x, z, settings, TerrainStage.EXTERIOR).segments();
    }
    @Override public org.sathrek.sky_archipelago.worldgen.generator.field.TerrainColumn skyweave$exteriorOf(
            IslandDescriptor host, int x, int z, SkyIslandSettings settings) {
        var empty = org.sathrek.sky_archipelago.worldgen.generator.field.TerrainColumn.EMPTY;
        if (skyweave$profile == null || host == null) return empty;
        var plan = terrainPlanForDescriptor(host, settings);
        if (!plan.mightInfluence(x, z)) return empty;
        var horizontal = shapeSampler.sampleHorizontal(plan, x, z);
        if (!horizontal.inInfluence()) return empty;
        var prepared = densityEvaluator.prepareDensity(host, horizontal, x, z, settings,
                biomeTerrainSampler.sample(x, host.centerY(), z).terrainProfile());
        var wet = prepared.waterArchetype();
        return wet.exists() ? new org.sathrek.sky_archipelago.worldgen.generator.field.TerrainColumn(
                wet.underbellyBottomY(), wet.solidSurfaceY()) : prepared.landBody();
    }

    // Completed point queries share fill's native interpolation, shoreline sealing and cleanup.
    // Snapshot construction uses the earlier raw/uncleaned paths, not this method.
    @Inject(method = "sampleColumnData", at = @At("HEAD"), cancellable = true)
    private void completedColumn(int x, int z, SkyIslandSettings settings, CallbackInfoReturnable<ColumnData> result) {
        if (skyweave$profile != null) result.setReturnValue(sampleChunkTerrainSnapshot(
                new net.minecraft.world.level.ChunkPos(Math.floorDiv(x, 16), Math.floorDiv(z, 16)), settings).columnDataAt(x, z));
    }
    @Inject(method = "sampleColumn", at = @At("HEAD"), cancellable = true)
    private void actualTopInterval(int x, int z, SkyIslandSettings settings,
            CallbackInfoReturnable<org.sathrek.sky_archipelago.worldgen.generator.field.TerrainColumn> result) {
        if (skyweave$profile == null) return;
        var segments = sampleColumnData(x, z, settings).segments();
        result.setReturnValue(segments.isEmpty()
                ? org.sathrek.sky_archipelago.worldgen.generator.field.TerrainColumn.EMPTY : segments.getFirst());
    }

    @Inject(method = "sampleDensity", at = @At("RETURN"), cancellable = true)
    private void actualDensity(int x, int y, int z, SkyIslandSettings settings, CallbackInfoReturnable<Double> result) {
        if (skyweave$profile == null || result.getReturnValue() <= 0) return;
        if (sampleColumnData(x, z, settings).segments().stream().noneMatch(segment -> segment.contains(y)))
            result.setReturnValue(0.0);
    }
    @Override public synchronized void skyweave$configureVeins(net.minecraft.world.level.levelgen.RandomState random,
            net.minecraft.world.level.levelgen.NoiseGeneratorSettings settings) {
        if (skyweave$veinsConfigured) return;
        skyweave$veins = settings.oreVeinsEnabled() ? new IslandVeins(random, settings) : null;
        skyweave$veinsConfigured = true;
    }
    @Override public IslandVeins skyweave$veins() { return skyweave$veins; }

    @Override public synchronized void skyweave$configure(GenerationProfile profile) {
        if (skyweave$profile != null && skyweave$profile.equals(profile)) return;
        if (skyweave$profile != null) {
            throw new IllegalStateException("Cannot change a populated island field's generation identity");
        }
        ((ProfiledTerrain)(Object)clusterSampler).skyweave$configure(profile);
        ((ProfiledTerrain)(Object)descriptorFactory).skyweave$configure(profile);
        ((ProfiledTerrain)(Object)terrainPlanFactory).skyweave$configure(profile);
        ((ProfiledTerrain)(Object)shapeSampler).skyweave$configure(profile);
        skyweave$styles = new IslandStyles(ShapeConfiguration.profile(), biomeTerrainSampler);
        ((StyledTerrain)(Object)shapeSampler).skyweave$styles(skyweave$styles);
        ((ProfiledTerrain)(Object)columnResolver).skyweave$configure(profile);
        skyweave$profile = profile;
    }
    @Override public GenerationProfile skyweave$profile() { return skyweave$profile; }

    @Shadow public abstract IslandDescriptor sampleDominantIslandDescriptor(int x, int z, SkyIslandSettings settings);
    @Shadow private List<IslandTerrainPlan> terrainPlansForCell(int x, int z, SkyIslandSettings settings) { throw new AssertionError(); }

    @Override public List<VillagePlan.Anchor> skyweave$anchorsNear(int x, int z, SkyIslandSettings settings) {
        if (skyweave$profile == null) return List.of();
        int cx = Math.floorDiv(x, skyweave$profile.spacing()), cz = Math.floorDiv(z, skyweave$profile.spacing());
        var anchors = new ArrayList<VillagePlan.Anchor>();
        // Maximum configured village reach includes layout reservation and native SKY retries.
        int range = skyweave$profile.lookupCells(skyweave$profile.anchorMaxRadius() + 2048 + 320);
        for (int dx = -range; dx <= range; dx++) for (int dz = -range; dz <= range; dz++) {
            var descriptors = terrainPlansForCell(cx + dx, cz + dz, settings);
            if (descriptors.isEmpty()) continue;
            var anchor = descriptors.getFirst().descriptor();
            anchors.add(new VillagePlan.Anchor(anchor.seed(), anchor.centerX(), anchor.centerZ(), anchor.maxRadius(),
                    anchor.centerY() + anchor.plateauHeight()));
        }
        return List.copyOf(anchors);
    }

    @Override public GeologicalFrame skyweave$frameAt(int x, int z, SkyIslandSettings settings) {
        if (skyweave$profile == null) return null;
        var owner = sampleDominantIslandDescriptor(x, z, settings);
        if (owner == null) return null;
        var anchor = owner;
        if (owner.family() != IslandFamily.ANCHOR_PLATEAU) {
            var membership = (ClusterField)this;
            var cell = membership.skyweave$owner(owner, settings);
            anchor = membership.skyweave$members(cell.x(), cell.z(), settings).getFirst();
        }
        return IslandGeometry.geologicalFrame(anchor);
    }

    @WrapOperation(method = "*", at = @At(value = "INVOKE", target =
            "Lorg/sathrek/sky_archipelago/worldgen/generator/field/internal/IslandSearchReach;cellRadius(ILorg/sathrek/sky_archipelago/config/SkyIslandSettings;)I"), require = 3)
    private int searchRange(int spacing, SkyIslandSettings settings, Operation<Integer> original) {
        return skyweave$profile == null ? original.call(spacing, settings) : skyweave$profile.lookupCells();
    }

    @Inject(method = "clusterMightIntersect", at = @At("HEAD"), cancellable = true)
    private void possibleCluster(int cellX, int cellZ, int spacing, SkyIslandSettings settings,
            int minX, int minZ, int maxX, int maxZ, CallbackInfoReturnable<Boolean> result) {
        if (skyweave$profile == null) return;
        long x = (long)cellX * spacing + spacing / 2;
        long z = (long)cellZ * spacing + spacing / 2;
        long extent = (long)skyweave$profile.jitter() + skyweave$profile.clusterReach();
        result.setReturnValue(x + extent >= minX && x - extent <= maxX
                && z + extent >= minZ && z - extent <= maxZ);
    }

    @Inject(method = "effectiveClusterSpacing", at = @At("HEAD"), cancellable = true)
    private void spacing(SkyIslandSettings settings, CallbackInfoReturnable<Integer> result) {
        if (skyweave$profile != null) result.setReturnValue(skyweave$profile.spacing());
    }
    @Inject(method = "scanBottom", at = @At("HEAD"), cancellable = true)
    private void bottom(SkyIslandSettings settings, CallbackInfoReturnable<Integer> result) {
        if (skyweave$profile != null) result.setReturnValue(GenerationProfile.MIN_Y);
    }
    @Inject(method = "scanTop", at = @At("HEAD"), cancellable = true)
    private void top(SkyIslandSettings settings, CallbackInfoReturnable<Integer> result) {
        if (skyweave$profile != null) result.setReturnValue(GenerationProfile.MAX_Y - 1);
    }
}
