package design.inherent.skyweavecompat.terrain;

import com.google.gson.*;
import com.mojang.serialization.JsonOps;
import design.inherent.skyweavecompat.configuration.JsonEdits;
import design.inherent.skyweavecompat.configuration.JsonSettings;
import java.io.IOException;
import java.nio.file.*;
import org.sathrek.sky_archipelago.config.settings.RiverSettings;

/** Loaded only by the admitted terrain integration; native defaults and codecs own hydrology. */
public final class RiverConfiguration {
    public static final String FILE = "skyweave_compat-rivers.json";
    private static final Gson JSON = new GsonBuilder().setPrettyPrinting().create();
    private static final JsonElement DEFAULTS = defaults();
    private static JsonElement defaults() {
        var nativeDefaults = RiverSettings.newWorldDefaults();
        var encoded = RiverSettings.CODEC.encodeStart(JsonOps.INSTANCE, nativeDefaults).getOrThrow().getAsJsonObject();
        encoded.addProperty("enabled", false);
        encoded.addProperty("lakes_enabled", false);
        // The native codec elides these defaults; the editor must still expose them.
        encoded.addProperty("standalone_lake_chance", nativeDefaults.standaloneLakeChance());
        var exclusions = new JsonArray();
        nativeDefaults.excludedBiomes().forEach(biome -> exclusions.add(biome.toString()));
        encoded.add("excluded_biomes", exclusions);
        // Codec map iteration is not a presentation contract. Preserve native
        // values while giving the file, sidebar and inline form the same order.
        for (String group : java.util.List.of("anchors", "satellites"))
            encoded.add(group, ordered(encoded.getAsJsonObject(group), "frequency", "minimum_radius",
                    "minimum_width", "maximum_width", "maximum_tributaries", "minimum_lake_radius", "maximum_lake_radius"));
        return ordered(encoded, "enabled", "lakes_enabled", "fluid_block", "minimum_depth", "maximum_depth",
                "meander_strength", "headwater_lake_chance", "standalone_lake_chance", "temperate_biome",
                "cold_biome", "ecological_bank_width", "anchors", "satellites", "excluded_biomes");
    }
    private static JsonObject ordered(JsonObject source, String... keys) {
        if (!source.keySet().equals(java.util.Set.of(keys)))
            throw new IllegalStateException("Native river fields changed; review the configuration form");
        var result = new JsonObject();
        for (String key : keys) result.add(key, source.get(key));
        return result;
    }
    private static volatile RiverSettings settings = RiverSettings.newWorldDefaults();

    public static RiverSettings settings() { return settings; }
    public static JsonSettings.Definition definition() {
        return new JsonSettings.Definition(FILE, "rivers", JSON.toJson(DEFAULTS) + "\n", RiverConfiguration::parse);
    }
    public static RiverSettings parse(String text) {
        try {
            var value = JsonEdits.object(text);
            shape(DEFAULTS, value, "rivers");
            return RiverSettings.CODEC.parse(JsonOps.INSTANCE, value).getOrThrow();
        } catch (IOException invalid) { throw new IllegalArgumentException(invalid.getMessage(), invalid); }
    }
    private static void shape(JsonElement expected, JsonElement value, String path) {
        if (expected.isJsonObject()) {
            if (!value.isJsonObject() || !expected.getAsJsonObject().keySet().equals(value.getAsJsonObject().keySet()))
                throw new IllegalArgumentException("Unexpected or missing settings at " + path);
            for (var entry : expected.getAsJsonObject().entrySet())
                shape(entry.getValue(), value.getAsJsonObject().get(entry.getKey()), path + "." + entry.getKey());
        } else if (expected.isJsonArray()) {
            if (!value.isJsonArray() || value.getAsJsonArray().asList().stream().anyMatch(item ->
                    !item.isJsonPrimitive() || !item.getAsJsonPrimitive().isString()))
                throw new IllegalArgumentException("Expected text entries at " + path);
        } else {
            if (!value.isJsonPrimitive()) throw new IllegalArgumentException("Expected a scalar at " + path);
            var wanted = expected.getAsJsonPrimitive();
            var actual = value.getAsJsonPrimitive();
            if (wanted.isBoolean() != actual.isBoolean() || wanted.isNumber() != actual.isNumber()
                    || wanted.isString() != actual.isString())
                throw new IllegalArgumentException("Incorrect setting type at " + path);
            if (wanted.isNumber() && wanted.getAsNumber() instanceof Integer) try {
                actual.getAsBigDecimal().intValueExact();
            } catch (ArithmeticException invalid) {
                throw new IllegalArgumentException("Expected an integer at " + path, invalid);
            }
        }
    }
    public static void load(Path directory) {
        var path = directory.resolve(FILE);
        try {
            Files.createDirectories(directory);
            if (!Files.exists(path)) Files.writeString(path, definition().defaults(), StandardOpenOption.CREATE_NEW);
            settings = parse(Files.readString(path));
            JsonSettings.register(definition());
        } catch (IOException | RuntimeException error) {
            throw new IllegalStateException("Cannot load " + path + ": " + error.getMessage(), error);
        }
    }
    private RiverConfiguration() {}
}
