package design.inherent.skyweavecompat.mixin;

import com.yungnickyoung.minecraft.yungsapi.world.structure.YungJigsawStructure;
import design.inherent.skyweavecompat.terrain.structure.StructureFamily;
import design.inherent.skyweavecompat.terrain.structure.StructurePlacementScope;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.TerrainAdjustment;
import org.sathrek.sky_archipelago.config.SkyIslandSettings;
import org.sathrek.sky_archipelago.worldgen.generator.field.IslandField;
import org.sathrek.sky_archipelago.worldgen.generator.structure.v2.common.model.HostIsland;
import org.sathrek.sky_archipelago.worldgen.generator.structure.v2.common.placement.HostFootprintSupportValidator;
import org.sathrek.sky_archipelago.worldgen.structure.ResolvedStructureSupportPlane;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Buried temple floors require existing island material, not proximity to its surface height. */
@Mixin(value = HostFootprintSupportValidator.class, remap = false)
public abstract class YungBuriedSupportMixin {
    @Inject(method = "evaluate(Lorg/sathrek/sky_archipelago/worldgen/generator/structure/v2/common/model/HostIsland;Lorg/sathrek/sky_archipelago/worldgen/structure/ResolvedStructureSupportPlane;Lorg/sathrek/sky_archipelago/worldgen/generator/field/IslandField;Lorg/sathrek/sky_archipelago/config/SkyIslandSettings;DILnet/minecraft/world/level/levelgen/structure/StructureStart;)Lorg/sathrek/sky_archipelago/worldgen/generator/structure/v2/common/placement/HostFootprintSupportValidator$Result;",
            at = @At("RETURN"), cancellable = true)
    private void existingBuriedFloor(HostIsland host, ResolvedStructureSupportPlane plane, IslandField field,
            SkyIslandSettings settings, double requiredRatio, int maxTopVariation, StructureStart start,
            CallbackInfoReturnable<HostFootprintSupportValidator.Result> result) {
        var original = result.getReturnValue();
        var scope = StructurePlacementScope.current();
        if (original.accepted() || original.topMismatchSamples() == 0 || scope == null
                || scope.family() != StructureFamily.DESERT_TEMPLES && scope.family() != StructureFamily.JUNGLE_TEMPLES
                || host == null || host.preview().terrainPlan() == null || plane == null || start == null
                || !(start.getStructure() instanceof YungJigsawStructure structure)
                || structure.terrainAdaptation() != TerrainAdjustment.NONE
                || structure.enhancedTerrainAdaptation.getKernelRadius() != 0) return;
        int buried = 0;
        for (var sample : plane.sampleSupport(settings.structureSupport().supportSampleGridSize())) {
            var column = field.sampleColumn(sample.x(), sample.z(), settings);
            if (!column.exists() || sample.groundY() >= column.topY() - Math.max(0, maxTopVariation)
                    || !host.preview().terrainPlan().descriptor().equals(field.sampleDominantIslandDescriptor(sample.x(), sample.z(), settings))) continue;
            // A single actual solid span must support the floor and its footing. Never fill air,
            // bridge a HOLLOW gap or admit another descriptor merely because its top is nearby.
            if (field.sampleSolidSegments(sample.x(), sample.z(), settings).stream()
                    .anyMatch(segment -> segment.contains(sample.groundY()) && segment.contains(sample.groundY() - 4))) buried++;
        }
        if (buried == 0) return;
        int grounded = original.sameHostGroundedSamples() + buried;
        result.setReturnValue(new HostFootprintSupportValidator.Result(
                grounded >= Math.max(1, (int)Math.ceil(original.totalSamples() * original.requiredRatio())),
                grounded, original.totalSamples(), original.foreignHostSamples(), original.missingSamples(),
                original.topMismatchSamples() - buried, original.requiredRatio(), original.heightDiagnostics() + ",existingBuriedFootings=" + buried));
    }
}
