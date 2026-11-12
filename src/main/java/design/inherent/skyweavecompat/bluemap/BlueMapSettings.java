package design.inherent.skyweavecompat.bluemap;

import design.inherent.skyweavecompat.configuration.SettingSpec;
import design.inherent.skyweavecompat.configuration.RecordSettings;
import design.inherent.skyweavecompat.configuration.ConfigFile;
import design.inherent.skyweavecompat.configuration.ProfileJson;
import com.google.gson.*;
import java.io.IOException;
import java.nio.file.*;
import java.util.HashSet;
import java.util.Set;

/** Explicit map selection and bounded browser resources; not world-generation settings. */
public record BlueMapSettings(
        @SettingSpec(maximumEntries=128, maximumLength=256, unique=true, nonBlank=true) Set<String> maps,
        @SettingSpec(minimum="1000", maximum="8000", unit="blocks") int detailDistance,
        @SettingSpec(minimum="1", maximum="48", unit="tiles") int tileRadius,
        @SettingSpec(minimum="1", maximum="8", unit="requests") int concurrentRequests,
        @SettingSpec(minimum="32", maximum="1024", unit="MiB") int geometryMiB) {
    public static final String FILE = "skyweave_compat-bluemap.json";
    public static final BlueMapSettings DEFAULT = new BlueMapSettings(Set.of(), 4000, 31, 4, 256);

    public BlueMapSettings {
        RecordSettings.validate(BlueMapSettings.class, maps, detailDistance, tileRadius, concurrentRequests, geometryMiB);
        maps = Set.copyOf(maps);
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
        var ids = value.getAsJsonArray("maps").asList().stream().map(id -> {
            if (!id.isJsonPrimitive() || !id.getAsJsonPrimitive().isString())
                throw new IllegalArgumentException("BlueMap map IDs must be strings");
            return id.getAsString();
        }).toList();
        RecordSettings.validateField(BlueMapSettings.class, "maps", ids);
        for (var key : numbers) {
            if (!value.get(key).isJsonPrimitive() || !value.getAsJsonPrimitive(key).isNumber())
                throw new IllegalArgumentException("Expected an integer for " + key);
        }
        return new BlueMapSettings(new HashSet<>(ids), value.get("detailDistance").getAsBigDecimal().intValueExact(),
                value.get("tileRadius").getAsBigDecimal().intValueExact(),
                value.get("concurrentRequests").getAsBigDecimal().intValueExact(),
                value.get("geometryMiB").getAsBigDecimal().intValueExact());
    }
}
