package design.inherent.skyweavecompat.probe;

import org.sathrek.sky_archipelago.worldgen.generator.field.model.IslandFamily;
import com.google.gson.JsonObject;
import design.inherent.skyweavecompat.climate.IslandClimate;
import design.inherent.skyweavecompat.terrain.GeologicalGenerator;
import design.inherent.skyweavecompat.terrain.GeologicalField;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.BlockStateConfiguration;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.structures.RuinedPortalStructure;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import org.sathrek.sky_archipelago.worldgen.generator.core.SkyIslandChunkGenerator;
import org.sathrek.sky_archipelago.worldgen.generator.field.IslandField;
import java.util.Comparator;
import java.util.Optional;

/** Exercise feature-internal altitude rules in a disposable native world. */
final class FeatureProbe {
    private FeatureProbe() {}

    static JsonObject run(ServerLevel level) {
        var generator = (SkyIslandChunkGenerator)level.getChunkSource().getGenerator();
        var random = level.getChunkSource().randomState();
        var field = ((GeologicalGenerator)(Object)generator).skyweave$field(random);
        var settings = generator.resolvedSkyIslandSettings();
        var result = new JsonObject();

        // Native icebergs reset their input Y to sea level and can replace air.
        var origin = new BlockPos(8, 508, 8);
        boolean placed = Feature.ICEBERG.place(new FeaturePlaceContext<>(Optional.empty(), level, generator,
                RandomSource.create(42), origin, new BlockStateConfiguration(Blocks.PACKED_ICE.defaultBlockState())));
        int ice = 0;
        var pos = new BlockPos.MutableBlockPos();
        for (int x = -8; x <= 24; x++) for (int z = -8; z <= 24; z++)
            for (int y = level.getMinBuildHeight(); y < level.getMinBuildHeight() + 48; y++)
                if (level.getBlockState(pos.set(x, y, z)).is(Blocks.PACKED_ICE)) ice++;
        result.addProperty("icebergFeatureReturnedSuccess", placed);
        result.addProperty("iceAtBuildFloor", ice);

        var portal = (RuinedPortalStructure)level.registryAccess().registryOrThrow(Registries.STRUCTURE)
                .get(ResourceLocation.withDefaultNamespace("ruined_portal"));
        var anchors = field.collectIslandPreviewsInRadius(0, 0, 24000, settings).stream()
                .filter(p -> p.family() == IslandFamily.ANCHOR_PLATEAU)
                .sorted(Comparator.comparingDouble(p -> Math.hypot(p.x(), p.z()))).toList();
        var geodeAnchor = anchors.getFirst();
        BlockPos geodeOrigin = null;
        int deepest = 0;
        for (int dx = -4; dx <= 4; dx++) for (int dz = -4; dz <= 4; dz++) {
            int x = geodeAnchor.x() + dx * geodeAnchor.radius() / 6, z = geodeAnchor.z() + dz * geodeAnchor.radius() / 6;
            var column = field.sampleColumn(x, z, settings);
            if (column.thickness() <= deepest) continue;
            var frame = ((GeologicalField)(Object)field).skyweave$originFrameAt(x, z, settings);
            if (frame == null) continue;
            int y = frame.toWorld(0);
            if (y < column.bottomY() + 24 || y > column.topY() - 24) continue;
            deepest = column.thickness(); geodeOrigin = new BlockPos(x, y, z);
        }
        if (geodeOrigin == null) throw new AssertionError("Missing deep geode fixture");
        var geode = level.registryAccess().registryOrThrow(Registries.CONFIGURED_FEATURE)
                .get(ResourceLocation.withDefaultNamespace("amethyst_geode"));
        boolean geodePlaced = geode.place(level, generator, RandomSource.create(42), geodeOrigin);
        int amethyst = 0, budding = 0;
        for (int dx = -20; dx <= 20; dx++) for (int dz = -20; dz <= 20; dz++) for (int dy = -20; dy <= 20; dy++) {
            var state = level.getBlockState(geodeOrigin.offset(dx, dy, dz));
            if (state.is(Blocks.AMETHYST_BLOCK)) amethyst++;
            if (state.is(Blocks.BUDDING_AMETHYST)) budding++;
        }
        var geodeRow = new JsonObject(); geodeRow.addProperty("placed", geodePlaced);
        geodeRow.addProperty("y", geodeOrigin.getY()); geodeRow.addProperty("amethyst", amethyst);
        geodeRow.addProperty("buddingAmethyst", budding);
        geodeRow.addProperty("evidence", "Direct native configured feature in deep island rock; not natural frequency");
        result.add("geode", geodeRow);
        if (!geodePlaced || amethyst == 0 || budding == 0) throw new AssertionError("Native geode did not produce amethyst");
        var portalCandidates = new com.google.gson.JsonArray();
        for (var anchor : anchors) {
            var context = new Structure.GenerationContext(level.registryAccess(), generator, generator.getBiomeSource(), random,
                    level.getServer().getStructureManager(), level.getSeed(), new ChunkPos(anchor.x() >> 4, anchor.z() >> 4), level, b -> true);
            var stub = portal.findGenerationPoint(context);
            if (stub.isEmpty()) continue;
            var point = stub.get().position();
            var candidate = new JsonObject(); candidate.addProperty("x", point.getX()); candidate.addProperty("y", point.getY()); candidate.addProperty("z", point.getZ());
            portalCandidates.add(candidate);
            if (point.getY() < level.getMinBuildHeight() || point.getY() >= level.getMaxBuildHeight()) continue;
            var biome = generator.getBiomeSource().getNoiseBiome(point.getX() >> 2, point.getY() >> 2,
                    point.getZ() >> 2, random.sampler());
            candidate.addProperty("biome", biome.getRegisteredName()); candidate.addProperty("nativeCold", biome.value().coldEnoughToSnow(point));
            boolean nativeCold = biome.value().coldEnoughToSnow(point);
            boolean referenceCold = biome.value().coldEnoughToSnow(IslandClimate.temperaturePosition(point));
            if (!referenceCold && !nativeCold) continue;
            String key = referenceCold ? "coldPortal" : "temperatePortal";
            if (result.has(key)) continue;
            var piece = stub.get().getPiecesBuilder().build().pieces().getFirst();
            var nbt = piece.createTag(StructurePieceSerializationContext.fromLevel(level));
            var row = new JsonObject();
            row.addProperty("biome", biome.getRegisteredName()); row.addProperty("y", point.getY());
            row.addProperty("nativeAltitudeCooling", nativeCold && !referenceCold);
            row.addProperty("cold", nbt.getCompound("Properties").getBoolean("cold"));
            row.addProperty("expectedCold", referenceCold);
            result.add(key, row);
            if (result.has("temperatePortal") && result.has("coldPortal")) break;
        }
        result.add("portalCandidates", portalCandidates);
        return result;
    }
}
