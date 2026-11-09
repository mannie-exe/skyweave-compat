package design.inherent.skyweavecompat.terrain;

import com.google.gson.*;
import java.nio.file.*;
import java.io.IOException;

public final class InteriorConfiguration {
    public static final String FILE = "skyweave_compat-interiors.json";
    private static final Gson JSON = new GsonBuilder().setPrettyPrinting().create();
    private static volatile InteriorProfile profile = InteriorProfile.INITIAL;
    private InteriorConfiguration() {}
    public static InteriorProfile profile() { return profile; }

    public static InteriorProfile parse(String text) {
        return design.inherent.skyweavecompat.configuration.ProfileJson.record(text, InteriorProfile.class);
    }

    public static String normalize(String text) { return JSON.toJson(parse(text)) + "\n"; }

    public static void load(Path directory) {
        Path path = directory.resolve(FILE);
        try {
            if (!Files.exists(path)) Files.writeString(path, JSON.toJson(InteriorProfile.INITIAL) + "\n", StandardOpenOption.CREATE_NEW);
            profile = parse(Files.readString(path));
        } catch (IOException | RuntimeException failure) {
            throw new IllegalStateException("Cannot load " + path + ": " + failure.getMessage(), failure);
        }
    }
}
