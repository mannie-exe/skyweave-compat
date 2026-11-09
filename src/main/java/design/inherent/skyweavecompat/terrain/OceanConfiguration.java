package design.inherent.skyweavecompat.terrain;

import com.google.gson.*;
import java.io.IOException;
import java.nio.file.*;

public final class OceanConfiguration {
    public static final String FILE = "skyweave_compat-oceans.json";
    private static final Gson JSON = new GsonBuilder().setPrettyPrinting().create();
    private static volatile OceanProfile profile = OceanProfile.INITIAL;
    private OceanConfiguration() {}
    public static OceanProfile profile() { return profile; }
    /** Current schema only; unsupported profiles fail without guessing new values. */
    public static String normalize(String text) {
        return JSON.toJson(design.inherent.skyweavecompat.configuration.ProfileJson.record(text, OceanProfile.class)) + "\n";
    }
    public static OceanProfile parse(String text) {
        return design.inherent.skyweavecompat.configuration.ProfileJson.record(text, OceanProfile.class);
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
