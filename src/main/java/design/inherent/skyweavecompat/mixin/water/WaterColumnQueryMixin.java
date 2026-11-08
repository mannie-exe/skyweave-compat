package design.inherent.skyweavecompat.mixin.water;

import com.llamalad7.mixinextras.sugar.Local;
import design.inherent.skyweavecompat.terrain.ProfiledTerrain;
import org.sathrek.sky_archipelago.config.SkyIslandSettings;
import org.sathrek.sky_archipelago.worldgen.generator.field.IslandField;
import org.sathrek.sky_archipelago.worldgen.generator.river.RiverColumnSample;
import org.sathrek.sky_archipelago.worldgen.generator.terrain.SkyIslandTerrainPass;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** Native planner flags are off, but height/base-column queries must retain adapted water. */
@Mixin(value = SkyIslandTerrainPass.class, remap = false)
public abstract class WaterColumnQueryMixin {
    @ModifyArg(method = {"baseHeightAt", "baseColumnAt"}, index = 7,
            at = @At(value = "INVOKE", target = "Lorg/sathrek/sky_archipelago/worldgen/generator/terrain/SkyIslandColumnMaterialPlan;create(Ljava/util/List;IILorg/sathrek/sky_archipelago/config/SkyIslandSettings;IIJLorg/sathrek/sky_archipelago/worldgen/generator/river/RiverColumnSample;Lorg/sathrek/sky_archipelago/worldgen/generator/field/WaterArchetypeColumnProfile;)Lorg/sathrek/sky_archipelago/worldgen/generator/terrain/SkyIslandColumnMaterialPlan;"), require = 2)
    private static RiverColumnSample water(RiverColumnSample original, @Local(argsOnly = true) IslandField field,
            @Local(argsOnly = true) SkyIslandSettings settings, @Local(argsOnly = true, ordinal = 0) int x,
            @Local(argsOnly = true, ordinal = 1) int z) {
        return ((ProfiledTerrain)(Object)field).skyweave$profile() == null ? original : field.sampleHydrologyColumn(x, z, settings);
    }
}
