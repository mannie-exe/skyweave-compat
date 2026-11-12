package design.inherent.skyweavecompat.terrain;

import design.inherent.skyweavecompat.configuration.ConfigFile;
import com.google.gson.GsonBuilder;
import design.inherent.skyweavecompat.configuration.ProfileJson;
import java.io.IOException;
import java.nio.file.*;

/** Immutable startup profile shared by sampling, metadata, configuration editing and world identity. */
public final class ShapeConfiguration {
    public static final String FILE = "skyweave_compat-shapes.json";
    private static final com.google.gson.Gson JSON = new GsonBuilder().setPrettyPrinting().create();
    private static volatile ShapeProfile profile = ShapeProfile.INITIAL;
    public static ShapeProfile profile() { return profile; }
    public static ShapeProfile parse(String text) {
        return ProfileJson.record(text, ShapeProfile.class);
    }
    public static String normalize(String text) { return JSON.toJson(parse(text)) + "\n"; }
    public static void load(Path directory) {
        var path = directory.resolve(FILE);
        try {
            if (!Files.exists(path)) Files.writeString(path, JSON.toJson(ShapeProfile.INITIAL) + "\n", StandardOpenOption.CREATE_NEW);
            profile = parse(ConfigFile.read(path));
        } catch (IOException | RuntimeException error) {
            throw new IllegalStateException("Cannot load " + path + ": " + error.getMessage(), error);
        }
    }
    private ShapeConfiguration() {}
}
