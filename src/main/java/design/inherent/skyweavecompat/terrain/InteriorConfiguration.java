package design.inherent.skyweavecompat.terrain;

import com.google.gson.*;
import java.nio.file.*;
import java.io.IOException;
import java.util.Arrays;
import java.util.stream.Collectors;

public final class InteriorConfiguration {
    public static final String FILE = "skyweave_compat-interiors.json";
    private static final Gson JSON = new GsonBuilder().setPrettyPrinting().create();
    private static volatile InteriorProfile profile = InteriorProfile.INITIAL;
    private InteriorConfiguration() {}
    public static InteriorProfile profile() { return profile; }

    public static InteriorProfile parse(String text) {
        var object = design.inherent.skyweavecompat.configuration.ProfileJson.object(text);
        if (!object.has("skylights")) {
            var legacyFields = Arrays.stream(InteriorProfile.class.getRecordComponents())
                    .map(component -> component.getName()).filter(name -> !name.equals("skylights")).collect(Collectors.toSet());
            if (!object.keySet().equals(legacyFields)) throw new IllegalArgumentException("Incomplete legacy interior profile");
            object.add("skylights", JSON.toJsonTree(InteriorProfile.Skylights.INITIAL));
        }
        return design.inherent.skyweavecompat.configuration.ProfileJson.record(object.toString(), InteriorProfile.class);
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
