package design.inherent.skyweavecompat.terrain;

import com.google.gson.*;
import java.io.IOException;
import java.nio.file.*;
import java.util.Set;

public final class OceanConfiguration {
    public static final String FILE = "skyweave_compat-oceans.json";
    private static final Gson JSON = new GsonBuilder().setPrettyPrinting().create();
    private static volatile OceanProfile profile = OceanProfile.INITIAL;
    private OceanConfiguration() {}
    public static OceanProfile profile() { return profile; }
    private static final Set<String> LEGACY_FIELDS = Set.of("enabled", "coastalChance", "perimeterChance",
            "perimeterMinRadius", "perimeterMaxRadius", "outletWidth", "outletDepth", "sealThickness");

    /** Only the exact released schema may acquire new defaults; malformed partial profiles still fail. */
    public static String normalize(String text) {
        var object = design.inherent.skyweavecompat.configuration.ProfileJson.object(text);
        if (object.keySet().equals(LEGACY_FIELDS)) {
            var resolved = JSON.toJsonTree(OceanProfile.INITIAL).getAsJsonObject();
            object.entrySet().forEach(entry -> resolved.add(entry.getKey(), entry.getValue()));
            text = JSON.toJson(resolved) + "\n";
        }
        design.inherent.skyweavecompat.configuration.ProfileJson.record(text, OceanProfile.class);
        return text;
    }
    public static OceanProfile parse(String text) {
        return design.inherent.skyweavecompat.configuration.ProfileJson.record(normalize(text), OceanProfile.class);
    }
    public static void load(Path directory) {
        var path = directory.resolve(FILE);
        try {
            if (!Files.exists(path)) Files.writeString(path, JSON.toJson(OceanProfile.INITIAL) + "\n", StandardOpenOption.CREATE_NEW);
            profile = parse(Files.readString(path));
        } catch (IOException | RuntimeException error) {
            throw new IllegalStateException("Cannot load " + path + ": " + error.getMessage(), error);
        }
    }
}
