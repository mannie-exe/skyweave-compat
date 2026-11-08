package design.inherent.skyweavecompat.terrain;

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
        var object = ProfileJson.object(text);
        var old = object.getAsJsonObject("edges");
        if (old != null && old.has("bandWidth")) {
            // Validate the complete old record before translating its explicit disable.
            var legacy = ProfileJson.record(text, LegacyShape.class);
            var initial = ShapeProfile.INITIAL.edges();
            var edge = legacy.edges();
            var replacement = new ShapeProfile.Edges(edge.chance() > 0 && edge.amplitude() > 0
                    && edge.affectedFraction() > 0, initial.minimumSmoothFraction(), initial.maximumSmoothFraction(),
                    edge.bandWidth(), initial.disintegratingChance(), initial.disintegratingScale(), edge.wavelength(),
                    initial.fragmentDepthRatio(), initial.density(), initial.falloffExponent(), initial.undersideAccent());
            return new ShapeProfile(legacy.enabled(), legacy.strengthVariation(), legacy.outline(), replacement, legacy.fractures());
        }
        return ProfileJson.record(text, ShapeProfile.class);
    }
    private record LegacyShape(boolean enabled, double strengthVariation, ShapeProfile.Outline outline,
            LegacyEdges edges, ShapeProfile.Fractures fractures) {}
    private record LegacyEdges(double chance, double affectedFraction, int bandWidth, int amplitude, int wavelength) {
        private LegacyEdges {
            if (!Double.isFinite(chance) || chance < 0 || chance > 1 || !Double.isFinite(affectedFraction)
                    || affectedFraction < 0 || affectedFraction > 1 || bandWidth < 8 || bandWidth > 96
                    || amplitude < 0 || amplitude > 48 || amplitude > bandWidth || wavelength < 8 || wavelength > 128)
                throw new IllegalArgumentException("Invalid legacy perimeter settings");
        }
    }
    public static String normalize(String text) { return JSON.toJson(parse(text)) + "\n"; }
    public static void load(Path directory) {
        var path = directory.resolve(FILE);
        try {
            if (!Files.exists(path)) Files.writeString(path, JSON.toJson(ShapeProfile.INITIAL) + "\n", StandardOpenOption.CREATE_NEW);
            profile = parse(Files.readString(path));
        } catch (IOException | RuntimeException error) {
            throw new IllegalStateException("Cannot load " + path + ": " + error.getMessage(), error);
        }
    }
    private ShapeConfiguration() {}
}
