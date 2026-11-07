package design.inherent.skyweavecompat.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import design.inherent.skyweavecompat.terrain.InteriorField;
import design.inherent.skyweavecompat.terrain.structure.StructurePlacementScope;
import java.util.List;
import net.minecraft.world.level.ChunkPos;
import org.sathrek.sky_archipelago.config.SkyIslandSettings;
import org.sathrek.sky_archipelago.worldgen.generator.field.IslandField;
import org.sathrek.sky_archipelago.worldgen.generator.field.model.IslandPreview;
import org.sathrek.sky_archipelago.worldgen.generator.structure.v2.underground.UndergroundCanonicalSitePlanner;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = UndergroundCanonicalSitePlanner.class, remap = false)
public abstract class StructurePolicyUndergroundMixin {
    @WrapOperation(method = "plan", at = @At(value = "INVOKE", target =
            "Lorg/sathrek/sky_archipelago/worldgen/generator/field/IslandField;collectIslandPreviewsIntersectingRadius(IIILorg/sathrek/sky_archipelago/config/SkyIslandSettings;)Ljava/util/List;"))
    private List<IslandPreview> localHosts(IslandField field, int x, int z, int radius, SkyIslandSettings settings,
            Operation<List<IslandPreview>> original) {
        var scope = StructurePlacementScope.current();
        return scope == null ? original.call(field, x, z, radius, settings)
                : original.call(field, x, z, 0, settings).stream().filter(scope::owns).toList();
    }

    @Inject(method = "representativeChunks", at = @At("HEAD"), cancellable = true)
    private static void chosenChunk(IslandPreview host, int reach, CallbackInfoReturnable<List<ChunkPos>> result) {
        var scope = StructurePlacementScope.current();
        if (scope != null) result.setReturnValue(scope.owns(host) ? List.of(scope.destination()) : List.of());
    }

    @Inject(method = "plan(Lorg/sathrek/sky_archipelago/config/SkyIslandSettings;Lorg/sathrek/sky_archipelago/worldgen/generator/field/IslandField;Lnet/minecraft/world/level/ChunkPos;Lnet/minecraft/resources/ResourceLocation;Lorg/sathrek/sky_archipelago/worldgen/generator/structure/v2/underground/UndergroundStrategySelector$Strategy;IILorg/sathrek/sky_archipelago/worldgen/generator/structure/v2/underground/UndergroundRetryPolicy;Lorg/sathrek/sky_archipelago/worldgen/generator/structure/v2/common/StructurePlacementBudget;Lorg/sathrek/sky_archipelago/worldgen/generator/structure/v2/underground/UndergroundSearchContext;)Lorg/sathrek/sky_archipelago/worldgen/generator/structure/v2/underground/UndergroundCanonicalSitePlanner$Plan;",
            at = @At("RETURN"), cancellable = true)
    private void selectedUnderground(CallbackInfoReturnable<UndergroundCanonicalSitePlanner.Plan> result) {
        var scope = StructurePlacementScope.current();
        if (scope == null) return;
        var plan = result.getReturnValue();
        var sites = plan.sites().stream().filter(site -> scope.owns(site.host())).map(site -> {
            var destination = scope.destination();
            var column = ((InteriorField)(Object)scope.pass().islandField()).skyweave$exteriorOf(
                    site.host().terrainPlan().descriptor(), destination.getMiddleBlockX(), destination.getMiddleBlockZ(), scope.pass().settings());
            return new UndergroundCanonicalSitePlanner.Site(site.host(), destination, column);
        }).distinct().toList();
        result.setReturnValue(new UndergroundCanonicalSitePlanner.Plan(sites, plan.hostsScanned(), plan.eligibleHosts(), plan.searchRadius()));
    }
}
