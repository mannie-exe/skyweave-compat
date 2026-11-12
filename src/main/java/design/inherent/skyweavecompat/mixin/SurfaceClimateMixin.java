package design.inherent.skyweavecompat.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import design.inherent.skyweavecompat.climate.IslandClimate;
import design.inherent.skyweavecompat.climate.SurfaceClimateScope;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.chunk.ChunkAccess;
import org.sathrek.sky_archipelago.config.SkyIslandSettings;
import org.sathrek.sky_archipelago.worldgen.generator.field.IslandField;
import org.sathrek.sky_archipelago.worldgen.generator.surface.SkyIslandSurfacePass;
import org.sathrek.sky_archipelago.worldgen.generator.surface.IslandSurfaceRules;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(value = SkyIslandSurfacePass.class, remap = false)
public abstract class SurfaceClimateMixin {
    @WrapMethod(method = "buildSurface")
    private static void surface(WorldGenRegion level, StructureManager structures, ChunkAccess chunk,
            SkyIslandSettings settings, IslandField field, SkyIslandSurfacePass.HostBiomeSampler hostBiomes,
            IslandSurfaceRules rules, Operation<Void> original) {
        SurfaceClimateScope.run(level,
                () -> original.call(level, structures, chunk, settings, field, hostBiomes, rules));
    }
}
