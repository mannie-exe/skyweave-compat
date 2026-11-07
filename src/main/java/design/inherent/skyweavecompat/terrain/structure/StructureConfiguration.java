package design.inherent.skyweavecompat.terrain.structure;

import com.google.gson.*;
import design.inherent.skyweavecompat.configuration.ProfileJson;
import java.io.IOException;
import java.nio.file.*;
import java.util.Set;
import java.util.TreeMap;

/** One validated profile for runtime loading and optimistic in-game editing. */
public final class StructureConfiguration {
    public static final String FILE = "skyweave_compat-structures.json";
    private static final Gson JSON = new GsonBuilder().setPrettyPrinting().create();
    private static volatile StructurePolicy policy = StructurePolicy.DEFAULT;
    private StructureConfiguration() {}
    public static StructurePolicy policy() { return policy; }

    public static StructurePolicy parse(String text) {
        var root = ProfileJson.object(text);
        keys(root, Set.of("frequencyMultiplier", "outpostVillageSeparationChunks", "families"));
        if (!root.get("families").isJsonObject()) throw new IllegalArgumentException("Expected structure families");
        var rules = new TreeMap<String, StructurePolicy.Rule>();
        for (var entry : root.getAsJsonObject("families").entrySet()) {
            if (!entry.getValue().isJsonObject()) throw new IllegalArgumentException("Expected rule for " + entry.getKey());
            var value = entry.getValue().getAsJsonObject();
            keys(value, Set.of("targetStartsPer1024Chunks", "maximumPerCluster"));
            rules.put(entry.getKey(), new StructurePolicy.Rule(number(value, "targetStartsPer1024Chunks").getAsDouble(),
                    number(value, "maximumPerCluster").getAsBigDecimal().intValueExact()));
        }
        return new StructurePolicy(number(root, "frequencyMultiplier").getAsDouble(),
                number(root, "outpostVillageSeparationChunks").getAsBigDecimal().intValueExact(), rules);
    }

    private static void keys(JsonObject object, Set<String> expected) {
        if (!object.keySet().equals(expected)) throw new IllegalArgumentException("Expected exactly: " + new java.util.TreeSet<>(expected));
    }
    private static JsonPrimitive number(JsonObject object, String key) {
        var value = object.get(key);
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) throw new IllegalArgumentException("Expected number for " + key);
        return value.getAsJsonPrimitive();
    }

    public static void load(Path directory) {
        var path = directory.resolve(FILE);
        try {
            Files.createDirectories(directory);
            if (!Files.exists(path)) Files.writeString(path, JSON.toJson(StructurePolicy.DEFAULT) + "\n", StandardOpenOption.CREATE_NEW);
            policy = parse(Files.readString(path));
        } catch (IOException | RuntimeException error) {
            throw new IllegalStateException("Cannot load " + path + ": " + error.getMessage(), error);
        }
    }
}
