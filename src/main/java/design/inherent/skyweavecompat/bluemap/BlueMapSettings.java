package design.inherent.skyweavecompat.bluemap;

import design.inherent.skyweavecompat.configuration.ConfigFile;
import design.inherent.skyweavecompat.configuration.ProfileJson;
import com.google.gson.*;
import java.io.IOException;
import java.nio.file.*;
import java.util.HashSet;
import java.util.Set;

/** Explicit map selection and bounded browser resources; not world-generation settings. */
public record BlueMapSettings(Set<String> maps, int detailDistance, int tileRadius,
        int concurrentRequests, int geometryMiB) {
    public static final String FILE = "skyweave_compat-bluemap.json";
    public static final BlueMapSettings DEFAULT = new BlueMapSettings(Set.of(), 4000, 31, 4, 256);

    public BlueMapSettings {
        maps = Set.copyOf(maps);
        if (maps.size() > 128 || maps.stream().anyMatch(id -> id.isBlank() || id.length() > 256)
                || detailDistance < 1000 || detailDistance > 8000 || tileRadius < 1 || tileRadius > 48
                || concurrentRequests < 1 || concurrentRequests > 8 || geometryMiB < 32 || geometryMiB > 1024)
            throw new IllegalArgumentException("Invalid BlueMap adapter bounds");
    }

    public static BlueMapSettings load(Path directory) throws IOException {
        var file = directory.resolve(FILE);
        if (!Files.exists(file)) {
            Files.createDirectories(directory);
            Files.writeString(file, new GsonBuilder().setPrettyPrinting().create().toJson(DEFAULT) + "\n",
                    StandardOpenOption.CREATE_NEW);
        }
        return parse(ConfigFile.read(file));
    }

    public static BlueMapSettings parse(String text) {
        var value = ProfileJson.object(text);
        var numbers = Set.of("detailDistance", "tileRadius", "concurrentRequests", "geometryMiB");
        var fields = new HashSet<>(numbers); fields.add("maps");
        if (!value.keySet().equals(fields)) throw new IllegalArgumentException("Expected exactly " + fields);
        var maps = new HashSet<String>();
        for (var id : value.getAsJsonArray("maps")) {
            if (!id.isJsonPrimitive() || !id.getAsJsonPrimitive().isString())
                throw new IllegalArgumentException("BlueMap map IDs must be strings");
            maps.add(id.getAsString());
        }
        for (var key : numbers) {
            if (!value.get(key).isJsonPrimitive() || !value.getAsJsonPrimitive(key).isNumber())
                throw new IllegalArgumentException("Expected an integer for " + key);
        }
        return new BlueMapSettings(maps, value.get("detailDistance").getAsBigDecimal().intValueExact(),
                value.get("tileRadius").getAsBigDecimal().intValueExact(),
                value.get("concurrentRequests").getAsBigDecimal().intValueExact(),
                value.get("geometryMiB").getAsBigDecimal().intValueExact());
    }
}
