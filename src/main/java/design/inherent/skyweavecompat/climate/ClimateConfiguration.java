package design.inherent.skyweavecompat.climate;

import design.inherent.skyweavecompat.configuration.ConfigFile;
import com.google.gson.GsonBuilder;
import design.inherent.skyweavecompat.configuration.ProfileJson;
import java.io.IOException;
import java.nio.file.*;

/** Immutable startup policy; runtime, editor and saved-world identity share validation. */
public final class ClimateConfiguration {
    public static final String FILE = "skyweave_compat-climate.json";
    private static final com.google.gson.Gson JSON = new GsonBuilder().setPrettyPrinting().create();
    private static volatile ClimateProfile profile = ClimateProfile.DEFAULT;
    private ClimateConfiguration() {}
    public static ClimateProfile profile() { return profile; }
    public static ClimateProfile parse(String text) { return ProfileJson.record(text, ClimateProfile.class); }
    public static String normalize(String text) { return JSON.toJson(parse(text)) + "\n"; }
    public static void load(Path directory) {
        var path = directory.resolve(FILE);
        try {
            if (!Files.exists(path)) Files.writeString(path, JSON.toJson(ClimateProfile.DEFAULT) + "\n", StandardOpenOption.CREATE_NEW);
            profile = parse(ConfigFile.read(path));
        } catch (IOException | RuntimeException error) {
            throw new IllegalStateException("Cannot load " + path + ": " + error.getMessage(), error);
        }
    }
}
