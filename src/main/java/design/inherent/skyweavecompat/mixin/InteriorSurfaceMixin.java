package design.inherent.skyweavecompat.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import design.inherent.skyweavecompat.terrain.IslandSurfaceScope;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import org.sathrek.sky_archipelago.config.SkyIslandSettings;
import org.sathrek.sky_archipelago.worldgen.generator.field.IslandField;
import org.sathrek.sky_archipelago.worldgen.generator.surface.IslandSurfaceRules;
import org.sathrek.sky_archipelago.worldgen.generator.surface.SurfaceBlockSelector.SurfaceLayer;
import org.sathrek.sky_archipelago.worldgen.generator.field.TerrainColumn;
import org.sathrek.sky_archipelago.worldgen.generator.terrain.SkyIslandChunkTerrainSnapshot;
import org.sathrek.sky_archipelago.worldgen.generator.surface.SkyIslandSurfacePass;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = SkyIslandSurfacePass.class, remap = false)
public abstract class InteriorSurfaceMixin {
    @WrapOperation(method = {"buildSurface", "paintOverlaySurface(Lnet/minecraft/world/level/WorldGenLevel;Lnet/minecraft/world/level/chunk/ChunkAccess;IILorg/sathrek/sky_archipelago/worldgen/generator/field/TerrainColumn;Lnet/minecraft/core/BlockPos$MutableBlockPos;Lorg/sathrek/sky_archipelago/worldgen/generator/field/WaterArchetypeColumnProfile;JIZLorg/sathrek/sky_archipelago/worldgen/generator/terrain/SkyIslandChunkTerrainSnapshot;Lorg/sathrek/sky_archipelago/worldgen/generator/surface/IslandSurfaceRules;)I"}, at = @At(value = "INVOKE", target =
            "Lorg/sathrek/sky_archipelago/worldgen/generator/surface/CliffSoil;paint(Lnet/minecraft/world/level/chunk/ChunkAccess;IILorg/sathrek/sky_archipelago/worldgen/generator/field/TerrainColumn;Lorg/sathrek/sky_archipelago/worldgen/generator/surface/SurfaceBlockSelector$SurfaceLayer;JLorg/sathrek/sky_archipelago/worldgen/generator/terrain/SkyIslandChunkTerrainSnapshot;)I"), require = 2, allow = 2)
    private static int fringeSoil(ChunkAccess chunk, int x, int z,
            TerrainColumn roof,
            SurfaceLayer layer, long seed,
            SkyIslandChunkTerrainSnapshot snapshot, Operation<Integer> original) {
        return original.call(chunk, x, z, roof, IslandSurfaceScope.soilLayer(x, z, roof.topY(), layer), seed, snapshot);
    }

    @WrapMethod(method = "buildSurface")
    private static void context(WorldGenRegion level, StructureManager structures, ChunkAccess chunk,
            SkyIslandSettings settings, IslandField field, SkyIslandSurfacePass.HostBiomeSampler hostBiomes,
            IslandSurfaceRules rules, Operation<Void> original) {
        IslandSurfaceScope.run(field, settings,
                () -> original.call(level, structures, chunk, settings, field, hostBiomes, rules));
    }

    // Do not intercept structure foundation writes: those intentionally fill air.
    @WrapOperation(method = {"buildSurface", "paintOverlaySurface(Lnet/minecraft/world/level/WorldGenLevel;Lnet/minecraft/world/level/chunk/ChunkAccess;IILorg/sathrek/sky_archipelago/worldgen/generator/field/TerrainColumn;Lnet/minecraft/core/BlockPos$MutableBlockPos;Lorg/sathrek/sky_archipelago/worldgen/generator/field/WaterArchetypeColumnProfile;JIZLorg/sathrek/sky_archipelago/worldgen/generator/terrain/SkyIslandChunkTerrainSnapshot;Lorg/sathrek/sky_archipelago/worldgen/generator/surface/IslandSurfaceRules;)I", "rewriteExposedStoneSkin"},
            at = @At(value = "INVOKE", target = "Lorg/sathrek/sky_archipelago/worldgen/generator/surface/SkyIslandSurfacePass;setSurfaceBlock(Lnet/minecraft/world/level/chunk/ChunkAccess;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;JI)V"), require = 3)
    private static void preserveOpenings(ChunkAccess chunk, BlockPos pos, BlockState material,
            long seed, int deepslateY, Operation<Void> original) {
        var selected = IslandSurfaceScope.surface(chunk, pos, material);
        if (selected != null) original.call(chunk, pos, selected, seed, deepslateY);
    }
}
