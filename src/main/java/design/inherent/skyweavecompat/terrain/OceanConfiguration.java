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
    private record ReleasedProfile(boolean enabled, double admissionChance, double lagoonWeight,
            double reefRingWeight, double deadOceanShelfWeight, double brokenReefWeight,
            double coastalChance, double perimeterChance, int perimeterMinRadius, int perimeterMaxRadius,
            int outletWidth, int outletDepth, int sealThickness, boolean spireCompanions, int spireClearance) {
        ReleasedProfile {
            if (perimeterMinRadius < 16 || perimeterMaxRadius > 128 || perimeterMinRadius > perimeterMaxRadius
                    || outletWidth < 1 || outletWidth > 16) throw new IllegalArgumentException("Invalid released ocean dimensions");
        }
        private OceanProfile current() {
            return new OceanProfile(enabled, admissionChance, lagoonWeight, reefRingWeight, deadOceanShelfWeight,
                    brokenReefWeight, coastalChance, perimeterChance, OceanProfile.INITIAL.coveOpeningDegrees(),
                    OceanProfile.INITIAL.coveShoulderDegrees(), outletDepth, sealThickness, spireCompanions, spireClearance);
        }
    }

    /** Only the exact released schema may acquire new defaults; malformed partial profiles still fail. */
    public static String normalize(String text) {
        var object = design.inherent.skyweavecompat.configuration.ProfileJson.object(text);
        if (object.keySet().equals(LEGACY_FIELDS)) {
            var resolved = JSON.toJsonTree(new ReleasedProfile(true, .2, 1, 0, 0, 1, .25, .15, 40, 64, 2, 2, 4, true, 16)).getAsJsonObject();
            object.entrySet().forEach(entry -> resolved.add(entry.getKey(), entry.getValue()));
            object = resolved;
        }
        var releasedFields = java.util.Arrays.stream(ReleasedProfile.class.getRecordComponents())
                .map(java.lang.reflect.RecordComponent::getName).collect(java.util.stream.Collectors.toSet());
        if (object.keySet().equals(releasedFields))
            text = JSON.toJson(design.inherent.skyweavecompat.configuration.ProfileJson.record(object.toString(), ReleasedProfile.class).current()) + "\n";
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
