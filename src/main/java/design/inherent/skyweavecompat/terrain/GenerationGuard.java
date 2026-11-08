package design.inherent.skyweavecompat.terrain;

import com.google.gson.*;
import design.inherent.skyweavecompat.NeighborConfig;
import design.inherent.skyweavecompat.OptionalIntegrations;
import design.inherent.skyweavecompat.Feature;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.resources.ResourceLocation;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.RegistryOps;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.storage.LevelResource;
import org.sathrek.sky_archipelago.config.SkyIslandGeneratorSettings;
import org.sathrek.sky_archipelago.worldgen.generator.core.SkyIslandChunkGenerator;
import java.io.IOException;
import java.nio.file.Files;
import java.util.TreeSet;

public final class GenerationGuard {
    private GenerationGuard() {}
    public static void verify(MinecraftServer server) {
        var root = server.getWorldPath(LevelResource.ROOT);
        var stem = server.registryAccess().registryOrThrow(Registries.LEVEL_STEM).getOrThrow(LevelStem.OVERWORLD);
        if (!(stem.generator() instanceof SkyIslandChunkGenerator sky)
                || !((GeologicalGenerator)(Object)sky).skyweave$isEnabled()) {
            if (Files.exists(root.resolve(GenerationIdentity.FILE))) {
                throw new IllegalStateException("This world requires its recorded Skyweave generator; refusing an incompatible replacement.");
            }
            return;
        }
        if (stem.type().value().minY() != GenerationProfile.MIN_Y
                || stem.type().value().height() != GenerationProfile.MAX_Y - GenerationProfile.MIN_Y
                || sky.getMinY() != GenerationProfile.MIN_Y
                || sky.getGenDepth() != GenerationProfile.MAX_Y - GenerationProfile.MIN_Y) {
            throw new IllegalStateException("Skyweave dimension and noise settings must both use [-128, 768)");
        }
        var ops = RegistryOps.create(JsonOps.INSTANCE, server.registryAccess());
        var identity = new JsonObject();
        identity.addProperty("algorithm", "islands_v1-skyarch-1.4-4");
        identity.addProperty("largeVeins", "native_reference_depth_v1");
        identity.addProperty("seed", server.getWorldData().worldGenOptions().seed());
        identity.add("villagePolicy", new Gson().toJsonTree(NeighborConfig.settings()));
        var villages = server.registryAccess().registryOrThrow(Registries.STRUCTURE_SET).get(ResourceLocation.parse("skyvillages:skyvillage"));
        if (OptionalIntegrations.skyVillages() && villages != null) {
            identity.add("villageSource", StructureSet.DIRECT_CODEC.encodeStart(ops, villages).getOrThrow());
        }
        identity.add("profile", new Gson().toJsonTree(GenerationConfiguration.profile()));
        identity.add("shapes", new Gson().toJsonTree(ShapeConfiguration.profile()));
        identity.add("interiors", new Gson().toJsonTree(InteriorConfiguration.profile()));
        identity.addProperty("hydrology", OptionalIntegrations.enabled(Feature.DYNAMIC_WATERS)
                ? design.inherent.skyweavecompat.water.WaterIdentity.ALGORITHM : "native_skyarch_1.4");
        if (OptionalIntegrations.enabled(Feature.DYNAMIC_WATERS))
            identity.add("dynamicWaters", design.inherent.skyweavecompat.water.WaterIdentity.inputs());
        identity.add("oceans", new Gson().toJsonTree(OceanConfiguration.profile()));
        if (OptionalIntegrations.enabled(Feature.STRUCTURE_DISTRIBUTION)) {
            identity.addProperty("structureDistribution", "cluster_family_v1");
            identity.add("structures", new Gson().toJsonTree(design.inherent.skyweavecompat.terrain.structure.StructureConfiguration.policy()));
            if (OptionalIntegrations.enabled(Feature.YUNG_STRUCTURES)) {
                var replacements = ((design.inherent.skyweavecompat.terrain.structure.YungWorldPolicy)(Object)sky).skyweave$yungReplacements();
                if (!replacements.isEmpty()) identity.add("yungReplacements", new Gson().toJsonTree(replacements));
            }
        }
        var mudBiomes = new JsonArray();
        server.registryAccess().registryOrThrow(Registries.BIOME).getTag(BiomeMaterials.MUD).ifPresent(tag ->
                tag.stream().map(biome -> biome.getRegisteredName()).sorted().forEach(mudBiomes::add));
        identity.add("mudSurfaceBiomes", mudBiomes);
        identity.addProperty("geologicalSurfaceReference", GeologicalFrame.REFERENCE_SURFACE);
        identity.addProperty("canyonOrigins", "local_surface");
        identity.addProperty("ancientCityPlacement", "interior_union_v1");
        identity.addProperty("ancientCityClearance", "bounded_beard_box_v1");
        identity.addProperty("deepDarkBand", "native_exterior_lower45_v1");
        identity.addProperty("physicalCaveBiomes", "native_projection_v1");
        identity.add("ancientCities", new Gson().toJsonTree(design.inherent.skyweavecompat.terrain.city.CityConfiguration.policy()));
        identity.addProperty("spawnLanding", "generated_surface");
        identity.addProperty("climate", "biome_temperature_at_reference_surface");
        var disabled = new JsonArray();
        for (var feature : java.util.List.of(Feature.ORE_PLACEMENT, Feature.CARVERS, Feature.CAVE_BIOMES,
                Feature.ANCIENT_CITIES, Feature.SPAWN, Feature.STRUCTURE_LOCATION, Feature.STRUCTURE_DISTRIBUTION,
                Feature.LITHOSTITCHED_STRUCTURES, Feature.YUNG_STRUCTURES, Feature.YUNG_MINESHAFTS, Feature.YUNG_WITCH_HUTS, Feature.CLIMATE,
                Feature.VILLAGE_CLEARANCE, Feature.VILLAGE_PLACEMENT)) {
            if (!OptionalIntegrations.configured(feature)) disabled.add(feature.key);
        }
        // Default behavior retains the existing identity byte-for-byte. Different generation choices
        // receive a distinct identity and cannot silently alter an existing save.
        if (!disabled.isEmpty()) identity.add("disabledAdaptations", disabled);
        identity.add("biomes", net.minecraft.world.level.biome.BiomeSource.CODEC.encodeStart(ops, sky.getBiomeSource()).getOrThrow());
        identity.add("islands", SkyIslandGeneratorSettings.CODEC.encodeStart(ops,
                SkyIslandGeneratorSettings.fromRuntimeSettings(sky.resolvedSkyIslandSettings())).getOrThrow());
        identity.add("noise", NoiseGeneratorSettings.DIRECT_CODEC.encodeStart(ops, sky.generatorSettings().value()).getOrThrow());
        identity.add("dimension", DimensionType.DIRECT_CODEC.encodeStart(ops, stem.type().value()).getOrThrow());
        try {
            GenerationIdentity.verify(root, new GsonBuilder().setPrettyPrinting().create().toJson(canonical(identity)) + "\n");
        } catch (IOException exception) {
            throw new IllegalStateException("Could not verify Skyweave world generation identity", exception);
        }
    }

    private static JsonElement canonical(JsonElement value) {
        if (value.isJsonObject()) {
            var sorted = new JsonObject();
            for (String key : new TreeSet<>(value.getAsJsonObject().keySet())) sorted.add(key, canonical(value.getAsJsonObject().get(key)));
            return sorted;
        }
        if (value.isJsonArray()) {
            var array = new JsonArray(); for (var entry : value.getAsJsonArray()) array.add(canonical(entry)); return array;
        }
        return value;
    }
}
