package design.inherent.skyweavecompat.probe;

import com.google.gson.JsonObject;
import design.inherent.skyweavecompat.terrain.structure.StructureDistribution;
import design.inherent.skyweavecompat.terrain.IslandSurfaceScope;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.UpgradeData;
import org.sathrek.sky_archipelago.worldgen.generator.structure.StructurePlacementPassContext;
import org.sathrek.sky_archipelago.worldgen.generator.surface.SurfaceBlockSelector;

/** A private chunk exercises provenance caching against the actual field and biome selection. */
public final class FoundationMaterialProbe {
    private FoundationMaterialProbe() {}

    public static JsonObject inspect(ServerLevel level, StructureDistribution distribution) throws Exception {
        var member = StructureDistribution.class.getDeclaredField("environment");
        member.setAccessible(true);
        var pass = (StructurePlacementPassContext)member.get(distribution);
        var first = ordinaryPair(level, pass);
        var second = first.east();
        var chunk = new ProtoChunk(new ChunkPos(first), UpgradeData.EMPTY, level,
                level.registryAccess().registryOrThrow(Registries.BIOME), null);
        chunk.fillBiomesFromNoise((x, y, z, climate) -> level.getNoiseBiome(x, y, z), pass.randomState().sampler());
        chunk.setPersistedStatus(net.minecraft.world.level.chunk.status.ChunkStatus.BIOMES);
        for (var at : java.util.List.of(first, second)) {
            chunk.setBlockState(at, Blocks.SAND.defaultBlockState(), false);
            chunk.setBlockState(at.below(), Blocks.SANDSTONE.defaultBlockState(), false);
            chunk.setBlockState(at.below(2), Blocks.SANDSTONE.defaultBlockState(), false);
        }
        var nativeLayer = SurfaceBlockSelector.forBiome(level.getBiome(first), first.above(4));
        if (!nativeLayer.top().is(Blocks.GRASS_BLOCK))
            throw new AssertionError("Fixture no longer reproduces the generic ocean palette");
        var before = new AtomicReference<SurfaceBlockSelector.SurfaceLayer>();
        var after = new AtomicReference<SurfaceBlockSelector.SurfaceLayer>();
        IslandSurfaceScope.run(pass.islandField(), pass.settings(), () -> {
            IslandSurfaceScope.captureFoundationSkin(chunk);
            before.set(IslandSurfaceScope.foundation(chunk, level.getBiome(first), first.above(4), nativeLayer));
            // Simulate an earlier overlapping blend changing a column not yet requested.
            chunk.setBlockState(second, Blocks.GRASS_BLOCK.defaultBlockState(), false);
            chunk.setBlockState(second.below(), Blocks.DIRT.defaultBlockState(), false);
            after.set(IslandSurfaceScope.foundation(chunk, level.getBiome(second), second.above(4), nativeLayer));
        });
        if (!before.get().top().is(Blocks.SAND) || !after.get().top().is(Blocks.SAND)
                || !before.get().under().is(Blocks.SANDSTONE) || !after.get().under().is(Blocks.SANDSTONE))
            throw new AssertionError("An overlapping blend changed the captured natural skin: before=" + before.get()
                    + ", after=" + after.get() + ", currentHeight=" + chunk.getHeight(
                    net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE_WG, first.getX() & 15, first.getZ() & 15));
        // A separate surface call must discard the previous call's captured blocks.
        var fresh = new AtomicReference<SurfaceBlockSelector.SurfaceLayer>();
        IslandSurfaceScope.run(pass.islandField(), pass.settings(), () ->
                fresh.set(IslandSurfaceScope.foundation(chunk, level.getBiome(second), second.above(4), nativeLayer)));
        if (!fresh.get().top().is(Blocks.GRASS_BLOCK))
            throw new AssertionError("A previous surface call retained chunk palette state");
        if (!chunk.getBlockState(first).is(Blocks.SAND) || !chunk.getBlockState(second).is(Blocks.GRASS_BLOCK))
            throw new AssertionError("Material selection wrote terrain");
        for (var surface : java.util.List.of(Blocks.SNOW_BLOCK, Blocks.ICE, Blocks.PACKED_ICE,
                Blocks.BLUE_ICE, Blocks.CHEST, Blocks.WATER)) {
            chunk.setBlockState(first, surface.defaultBlockState(), false);
            var selected = new AtomicReference<SurfaceBlockSelector.SurfaceLayer>();
            IslandSurfaceScope.run(pass.islandField(), pass.settings(), () -> selected.set(
                    IslandSurfaceScope.foundation(chunk, level.getBiome(first), first.above(4), nativeLayer)));
            boolean protectedState = surface == Blocks.CHEST || surface == Blocks.WATER;
            var expected = protectedState ? nativeLayer.top() : surface.defaultBlockState();
            if (!selected.get().top().equals(expected) || !chunk.getBlockState(first).is(surface))
                throw new AssertionError("Natural/protected surface control failed for " + surface);
        }
        var result = new JsonObject();
        result.addProperty("nativeOceanFallback", "grass_block");
        result.addProperty("resolvedTop", "sand");
        result.addProperty("resolvedUnder", "sandstone");
        result.addProperty("overlappingWritesDoNotChangePalette", true);
        result.addProperty("surfaceCallDoesNotRetainChunkState", true);
        result.addProperty("materialSelectionIsReadOnly", true);
        result.addProperty("coldSurfaceControls", 4);
        result.addProperty("fluidAndBlockEntityPaletteControls", 2);
        result.addProperty("ordinaryColumn", first.toShortString());
        return result;
    }

    /** The wreck center can have an explicit wet palette; isolate the ordinary-skin branch. */
    private static BlockPos ordinaryPair(ServerLevel level, StructurePlacementPassContext pass) {
        for (int cx = -807; cx <= -805; cx++) for (int cz = 200; cz <= 201; cz++) {
            var snapshot = pass.islandField().sampleChunkTerrainSnapshot(new ChunkPos(cx, cz), pass.settings());
            for (int x = cx * 16; x < cx * 16 + 15; x++) for (int z = cz * 16; z < cz * 16 + 16; z++) {
                boolean ordinary = true;
                for (int dx = 0; dx <= 1; dx++) {
                    var column = snapshot.columnDataAt(x + dx, z);
                    var owner = column.dominantDescriptor();
                    if (!column.profile().exists() || column.waterArchetype().exists() || owner == null
                            || ((design.inherent.skyweavecompat.terrain.InteriorField)(Object)pass.islandField())
                                    .skyweave$isFringe(owner, x + dx, z, pass.settings())
                                && design.inherent.skyweavecompat.terrain.SurfaceProvenance.fringe(owner).role()
                                    != design.inherent.skyweavecompat.terrain.SurfaceProvenance.Role.LAND) {
                        ordinary = false;
                        break;
                    }
                    var pos = new BlockPos(x + dx, column.profile().topY(), z);
                    if (!SurfaceBlockSelector.forBiome(level.getBiome(pos), pos).top().is(Blocks.GRASS_BLOCK)) ordinary = false;
                }
                if (ordinary) return new BlockPos(x, snapshot.profileAt(x, z).topY(), z);
            }
        }
        throw new AssertionError("No ordinary pair remains in the recorded foundation neighborhood");
    }
}
