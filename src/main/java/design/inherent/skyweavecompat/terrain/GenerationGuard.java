package design.inherent.skyweavecompat.terrain;

import com.google.gson.*;
import com.mojang.logging.LogUtils;
import design.inherent.skyweavecompat.NeighborConfig;
import design.inherent.skyweavecompat.OptionalIntegrations;
import design.inherent.skyweavecompat.Feature;
import design.inherent.skyweavecompat.configuration.ProfileJson;
import design.inherent.skyweavecompat.presentation.PackIdentity;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.resources.ResourceLocation;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.RegistryOps;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import java.util.*;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLEnvironment;
import org.sathrek.sky_archipelago.config.SkyIslandGeneratorSettings;
import org.sathrek.sky_archipelago.worldgen.generator.core.SkyIslandChunkGenerator;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.function.Predicate;
import org.slf4j.Logger;

public final class GenerationGuard {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Map<MinecraftServer,Map<ResourceKey<Level>,String>> VERIFIED = Collections.synchronizedMap(new WeakHashMap<>());
    private static final ResourceLocation TYPE = ResourceLocation.parse(GenerationProfile.ID);
    private GenerationGuard() {}

    /** Only this dimension's verified policy may bind its retained terrain inputs. */
    public static String fingerprint(ServerLevel level) {
        var value = VERIFIED.getOrDefault(level.getServer(), Map.of()).get(level.dimension());
        if (value == null) throw new IllegalStateException("Natural terrain requires a verified generation identity for " + level.dimension().location());
        return value;
    }

    public static boolean hasOwnedDimension(MinecraftServer server) {
        return server.registryAccess().registryOrThrow(Registries.LEVEL_STEM).stream().anyMatch(stem -> owned(stem));
    }

    public static boolean usesDevPolicy(MinecraftServer server) {
        return FMLEnvironment.dist.isClient() && !server.isDedicatedServer() && server.isSingleplayer()
                && PackIdentity.installed(ModList.get()).filter(pack -> pack.version().equals("DEV")).isPresent();
    }

    private static boolean owned(LevelStem stem) {
        return stem.generator() instanceof GeologicalGenerator generator && generator.skyweave$isEnabled();
    }

    static Path dimensionPath(Path world, ResourceKey<Level> dimension) {
        var id = dimension.location();
        if (Set.of(".", "..").contains(id.getNamespace()) || Arrays.stream(id.getPath().split("/", -1))
                .anyMatch(part -> part.isEmpty() || part.equals(".") || part.equals(".."))) {
            throw new IllegalStateException("Noncanonical dimension storage ID: " + id);
        }
        var root = world.toAbsolutePath().normalize();
        var storage = DimensionType.getStorageFolder(dimension, root).normalize();
        if (!storage.startsWith(root)) throw new IllegalStateException("Dimension storage escapes the world directory: " + id);
        return storage;
    }

    public static Path markerPath(ServerLevel level) {
        return dimensionPath(level.getServer().getWorldPath(LevelResource.ROOT), level.dimension())
                .resolve(GenerationIdentity.FILE);
    }

    public static void verify(MinecraftServer server) {
        VERIFIED.remove(server);
        var root = server.getWorldPath(LevelResource.ROOT);
        var stems = server.registryAccess().registryOrThrow(Registries.LEVEL_STEM).entrySet().stream()
                .sorted(Comparator.comparing(entry -> entry.getKey().location())).toList();
        // Validate every pairing before writing any marker or allowing native level generation.
        for (var entry : stems) {
            var stem = entry.getValue(); boolean adapted = owned(stem);
            if (adapted != stem.type().is(TYPE)) {
                throw new IllegalStateException("Unsupported generator/dimension type pairing in " + entry.getKey().location()
                        + ": generator " + stem.generator().getClass().getName() + ", dimension type " + stem.type().getRegisteredName()
                        + ". Use the Skyweave owned generator and " + TYPE + " together, or a foreign generator with its own type.");
            }
            if (adapted && (stem.type().value().minY() != GenerationProfile.MIN_Y
                    || stem.type().value().height() != GenerationProfile.MAX_Y - GenerationProfile.MIN_Y
                    || stem.generator().getMinY() != GenerationProfile.MIN_Y
                    || stem.generator().getGenDepth() != GenerationProfile.MAX_Y - GenerationProfile.MIN_Y)) {
                throw new IllegalStateException("Skyweave dimension and noise settings must both use [-128, 768) in " + entry.getKey().location());
            }
            var dimension = ResourceKey.create(Registries.DIMENSION, entry.getKey().location());
            var storage = adapted ? dimensionPath(root, dimension) : DimensionType.getStorageFolder(dimension, root).toAbsolutePath().normalize();
            // Foreign IDs retain native policy. Only inspect our marker inside this save; never follow a traversal outside it.
            if (!adapted && storage.startsWith(root.toAbsolutePath().normalize()) && Files.exists(storage.resolve(GenerationIdentity.FILE))) {
                throw new IllegalStateException("Dimension " + dimension.location()
                        + " requires its recorded Skyweave generator; refusing an incompatible replacement.");
            }
        }
        var ops = RegistryOps.create(JsonOps.INSTANCE, server.registryAccess());
        Predicate<JsonObject> devNativeLayout = usesDevPolicy(server) ? saved -> validNativeLayout(ops, saved) : null;
        var admissions = new LinkedHashMap<ResourceKey<Level>, GenerationIdentity.Admission>();
        for (var entry : stems) {
            var stem = entry.getValue();
            if (!owned(stem)) continue;
            var dimension = ResourceKey.create(Registries.DIMENSION, entry.getKey().location());
            String text = identity(server, stem, (SkyIslandChunkGenerator)stem.generator(), ops);
            try {
                admissions.put(dimension, GenerationIdentity.prepare(dimensionPath(root, dimension), text, devNativeLayout));
            } catch (IllegalStateException refused) {
                throw new IllegalStateException("Dimension " + dimension.location() + ": " + refused.getMessage(), refused);
            } catch (IOException exception) {
                throw new IllegalStateException("Could not verify Skyweave generation identity for " + dimension.location(), exception);
            }
        }
        var verified = new HashMap<ResourceKey<Level>, String>();
        for (var entry : admissions.entrySet()) {
            var dimension = entry.getKey();
            var admission = entry.getValue();
            try {
                admission.write();
            } catch (IOException exception) {
                throw new IllegalStateException("Could not record Skyweave generation identity for " + dimension.location(), exception);
            }
            String fingerprint = fingerprint(admission.identity());
            if (admission.advances()) {
                LOGGER.info("Advanced Skyweave DEV generation identity for {} from {} to {}",
                        dimension.location(), fingerprint(admission.recorded()), fingerprint);
            }
            verified.put(dimension, fingerprint);
        }
        VERIFIED.put(server, Map.copyOf(verified));
    }

    private static String fingerprint(String identity) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(identity.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new AssertionError(impossible);
        }
    }

    private static boolean validNativeLayout(RegistryOps<JsonElement> ops, JsonObject saved) {
        try {
            ProfileJson.generation(saved.get("profile").toString());
        } catch (IllegalArgumentException invalid) {
            return false;
        }
        var noise = NoiseGeneratorSettings.DIRECT_CODEC.parse(ops, saved.get("noise")).result();
        return noise.filter(settings -> settings.noiseSettings().minY() == GenerationProfile.MIN_Y
                        && settings.noiseSettings().height() == GenerationProfile.MAX_Y - GenerationProfile.MIN_Y).isPresent()
                && BiomeSource.CODEC.parse(ops, saved.get("biomes")).result().isPresent()
                && SkyIslandGeneratorSettings.CODEC.parse(ops, saved.get("islands")).result().isPresent();
    }

    private static String identity(MinecraftServer server, LevelStem stem, SkyIslandChunkGenerator sky, RegistryOps<JsonElement> ops) {
        var identity = new JsonObject();
        identity.addProperty("algorithm", "islands_v1-skyarch-1.4-18");
        identity.addProperty("groundVillageSupport", "qualified_bearing_native_beardifier_v3");
        if(OptionalIntegrations.enabled(Feature.GROUND_SUPPORT))
            identity.add("villageComposition",design.inherent.skyweavecompat.terrain.structure.VillageComposition.generationInputs());
        identity.addProperty("largeVeins", "native_reference_depth_v1");
        identity.addProperty("seed", server.getWorldData().worldGenOptions().seed());
        identity.add("villagePolicy", new Gson().toJsonTree(NeighborConfig.settings()));
        var villages = server.registryAccess().registryOrThrow(Registries.STRUCTURE_SET).get(ResourceLocation.parse("skyvillages:skyvillage"));
        if (OptionalIntegrations.skyVillages() && villages != null) {
            identity.add("villageSource", StructureSet.DIRECT_CODEC.encodeStart(ops, villages).getOrThrow());
        }
        // Placement-off worlds use shared native data, not the pack's owned runtime band.
        // Its effective definition must therefore refuse an old unscoped absolute-band save.
        if (!OptionalIntegrations.enabled(Feature.VILLAGE_PLACEMENT)
                && design.inherent.skyweavecompat.placement.VillageHeightPolicy.packBand()) {
            var structure = server.registryAccess().registryOrThrow(Registries.STRUCTURE).get(ResourceLocation.parse("skyvillages:skyvillage"));
            if (structure != null) identity.add("inactiveFloatingVillageDefinition",
                    net.minecraft.world.level.levelgen.structure.Structure.DIRECT_CODEC.encodeStart(ops, structure).getOrThrow());
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
            identity.addProperty("structureDistribution", "cluster_family_supported_villages_v2");
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
        identity.addProperty("deepDarkBand", "contributor_native_depth_v2");
        identity.addProperty("physicalCaveBiomes", "height_aware_multinoise_depth_v2");
        identity.add("climatePolicy", new Gson().toJsonTree(design.inherent.skyweavecompat.climate.ClimateConfiguration.profile()));
        identity.add("ancientCities", new Gson().toJsonTree(design.inherent.skyweavecompat.terrain.city.CityConfiguration.policy()));
        identity.addProperty("spawnLanding", "generated_surface");
        identity.addProperty("climate", "height_aware_contributor_datum_v1");
        var disabled = new JsonArray();
        for (var feature : java.util.List.of(Feature.ORE_PLACEMENT, Feature.CARVERS, Feature.CAVE_BIOMES,
                Feature.ANCIENT_CITIES, Feature.ALTITUDE_HABITATS, Feature.WATER_SPAWNS, Feature.SPAWN, Feature.STRUCTURE_LOCATION, Feature.STRUCTURE_DISTRIBUTION, Feature.GROUND_SUPPORT,
                Feature.LITHOSTITCHED_STRUCTURES, Feature.YUNG_STRUCTURES, Feature.YUNG_MINESHAFTS, Feature.YUNG_WITCH_HUTS, Feature.CLIMATE,
                Feature.VILLAGE_CLEARANCE, Feature.VILLAGE_PLACEMENT)) {
            if (!OptionalIntegrations.configured(feature)) disabled.add(feature.key);
        }
        // Record independently disabled generation choices; existing saves must retain their policy.
        if (!disabled.isEmpty()) identity.add("disabledAdaptations", disabled);
        identity.add("biomes", net.minecraft.world.level.biome.BiomeSource.CODEC.encodeStart(ops, sky.getBiomeSource()).getOrThrow());
        identity.add("islands", SkyIslandGeneratorSettings.CODEC.encodeStart(ops,
                SkyIslandGeneratorSettings.fromRuntimeSettings(sky.resolvedSkyIslandSettings())).getOrThrow());
        identity.add("noise", NoiseGeneratorSettings.DIRECT_CODEC.encodeStart(ops, sky.generatorSettings().value()).getOrThrow());
        identity.add("dimension", DimensionType.DIRECT_CODEC.encodeStart(ops, stem.type().value()).getOrThrow());
        return new GsonBuilder().setPrettyPrinting().create().toJson(canonical(identity)) + "\n";
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
