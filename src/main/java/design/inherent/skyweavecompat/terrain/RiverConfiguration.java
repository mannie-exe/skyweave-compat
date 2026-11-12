package design.inherent.skyweavecompat.terrain;

import design.inherent.skyweavecompat.configuration.ConfigFile;
import com.google.gson.*;
import com.mojang.serialization.JsonOps;
import design.inherent.skyweavecompat.configuration.JsonEdits;
import design.inherent.skyweavecompat.configuration.JsonSettings;
import design.inherent.skyweavecompat.configuration.SettingConstraints;
import design.inherent.skyweavecompat.configuration.SettingDefinition;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import com.mojang.serialization.Codec;
import net.minecraft.resources.ResourceLocation;
import org.sathrek.sky_archipelago.config.settings.SourceFluidBlock;
import java.io.IOException;
import java.nio.file.*;
import org.sathrek.sky_archipelago.config.settings.RiverSettings;

/** Loaded only by the admitted terrain integration; native defaults and codecs own hydrology. */
public final class RiverConfiguration {
    public static final String FILE = "skyweave_compat-rivers.json";
    private static final Gson JSON = new GsonBuilder().setPrettyPrinting().create();
    private static final JsonElement DEFAULTS = defaults();
    private static final List<SettingDefinition> FIELDS = fields();
    private static final Map<String, SettingDefinition> BY_KEY = FIELDS.stream().collect(java.util.stream.Collectors.toUnmodifiableMap(SettingDefinition::key, field -> field));
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
        return new JsonSettings.Definition(FILE, "rivers", JSON.toJson(DEFAULTS) + "\n", RiverConfiguration::parse,
                java.util.function.UnaryOperator.identity(), FIELDS);
    }
    public static RiverSettings parse(String text) {
        try {
            var value = JsonEdits.object(text);
            shape(DEFAULTS, value, "");
            return RiverSettings.CODEC.parse(JsonOps.INSTANCE, value).getOrThrow();
        } catch (IOException invalid) { throw new IllegalArgumentException(invalid.getMessage(), invalid); }
    }
    private static void shape(JsonElement expected, JsonElement value, String path) {
        if (expected.isJsonObject()) {
            if (!value.isJsonObject() || !expected.getAsJsonObject().keySet().equals(value.getAsJsonObject().keySet()))
                throw new IllegalArgumentException("Unexpected or missing settings at " + path);
            for (var entry : expected.getAsJsonObject().entrySet())
                shape(entry.getValue(), value.getAsJsonObject().get(entry.getKey()), path.isEmpty() ? entry.getKey() : path + "." + entry.getKey());
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
            if (wanted.isNumber() && BY_KEY.get(path).kind() == SettingDefinition.Kind.INTEGER) try {
                actual.getAsBigDecimal().intValueExact();
            } catch (ArithmeticException invalid) {
                throw new IllegalArgumentException("Expected an integer at " + path, invalid);
            }
        }
    }
    /** Pinned codec presentation; the native codec remains the full-candidate authority. */
    private static List<SettingDefinition> fields() {
        var fields = new java.util.ArrayList<SettingDefinition>();
        fields.add(scalar("enabled", boolean.class, SettingConstraints.NONE));
        fields.add(scalar("lakes_enabled", boolean.class, SettingConstraints.NONE));
        fields.add(text("fluid_block", SourceFluidBlock.CODEC));
        fields.add(number("minimum_depth", int.class, 1, Integer.MAX_VALUE, "blocks", "depth"));
        fields.add(number("maximum_depth", int.class, 1, Integer.MAX_VALUE, "blocks", "depth"));
        fields.add(number("meander_strength", double.class, 0, 1, "factor", ""));
        fields.add(number("headwater_lake_chance", double.class, 0, 1, "probability", "lakes"));
        fields.add(number("standalone_lake_chance", double.class, 0, 1, "probability", "lakes"));
        fields.add(text("temperate_biome", ResourceLocation.CODEC));
        fields.add(text("cold_biome", ResourceLocation.CODEC));
        fields.add(number("ecological_bank_width", int.class, 0, Integer.MAX_VALUE, "blocks", ""));
        for (String family : List.of("anchors", "satellites")) {
            fields.add(number(family + ".frequency", double.class, 0, 1, "probability", ""));
            fields.add(number(family + ".minimum_radius", int.class, 8, Integer.MAX_VALUE, "blocks", ""));
            fields.add(number(family + ".minimum_width", int.class, 1, Integer.MAX_VALUE, "blocks", family + ".width"));
            fields.add(number(family + ".maximum_width", int.class, 1, Integer.MAX_VALUE, "blocks", family + ".width"));
            fields.add(number(family + ".maximum_tributaries", int.class, 0, 4, "", ""));
            fields.add(number(family + ".minimum_lake_radius", int.class, 2, Integer.MAX_VALUE, "blocks", family + ".lakeRadius"));
            fields.add(number(family + ".maximum_lake_radius", int.class, 2, Integer.MAX_VALUE, "blocks", family + ".lakeRadius"));
        }
        fields.add(new SettingDefinition("excluded_biomes", List.class,
                value("excluded_biomes").getAsJsonArray().asList().stream().map(JsonElement::getAsString).toList(),
                SettingConstraints.NONE, codec(ResourceLocation.CODEC.listOf())));
        return List.copyOf(fields);
    }
    private static JsonElement value(String key) {
        JsonElement value = DEFAULTS;
        for (String part : key.split("\\.")) value = value.getAsJsonObject().get(part);
        return value;
    }
    private static SettingDefinition scalar(String key, Class<?> type, SettingConstraints constraints) {
        var raw = value(key).getAsJsonPrimitive();
        final Object initial;
        if (type == boolean.class) initial = raw.getAsBoolean();
        else if (type == int.class) initial = raw.getAsInt();
        else initial = raw.getAsDouble();
        return new SettingDefinition(key, type, initial, constraints);
    }
    private static SettingDefinition number(String key, Class<?> type, Number minimum, Number maximum, String unit, String group) {
        return scalar(key, type, new SettingConstraints(new BigDecimal(minimum.toString()), new BigDecimal(maximum.toString()), false,
                type == int.class ? BigDecimal.ONE : new BigDecimal(".01"), unit, group, -1, -1, false, ""));
    }
    private static SettingDefinition text(String key, Codec<?> codec) {
        return new SettingDefinition(key, String.class, value(key).getAsString(), SettingConstraints.NONE, codec(codec));
    }
    private static Consumer<Object> codec(Codec<?> codec) {
        return value -> codec.parse(JsonOps.INSTANCE, JSON.toJsonTree(value)).getOrThrow();
    }

    public static void load(Path directory) {
        var path = directory.resolve(FILE);
        try {
            Files.createDirectories(directory);
            if (!Files.exists(path)) Files.writeString(path, definition().defaults(), StandardOpenOption.CREATE_NEW);
            settings = parse(ConfigFile.read(path));
            JsonSettings.register(definition());
        } catch (IOException | RuntimeException error) {
            throw new IllegalStateException("Cannot load " + path + ": " + error.getMessage(), error);
        }
    }
    private RiverConfiguration() {}
}
