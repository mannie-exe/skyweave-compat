package design.inherent.skyweavecompat.terrain.city;

import design.inherent.skyweavecompat.configuration.SettingSpec;
import design.inherent.skyweavecompat.configuration.RecordSettings;
import design.inherent.skyweavecompat.configuration.ConfigFile;
import com.google.gson.*;
import design.inherent.skyweavecompat.configuration.JsonEdits;
import java.io.IOException;
import java.nio.file.*;
import java.util.Set;

/** Ancient City policy; shared by the runtime loader and optimistic configuration editor. */
public final class CityConfiguration {
    public static final String FILE = "skyweave_compat-ancient-cities.json";
    public record Policy(
            @SettingSpec(minimum="0", maximum="1", unit="probability", step=".01") double minimumBuriedFraction) {
        public Policy {
            RecordSettings.validate(Policy.class, minimumBuriedFraction);
        }
    }
    public static final Policy DEFAULT = new Policy(.90);
    private static volatile Policy policy = DEFAULT;
    private CityConfiguration() {}
    public static Policy policy() { return policy; }
    public static Policy parse(String text) {
        JsonObject value;
        try { value = JsonEdits.object(text); }
        catch (IOException error) { throw new IllegalArgumentException("Invalid Ancient City JSON", error); }
        if (!value.keySet().equals(Set.of("minimumBuriedFraction"))
                || !value.get("minimumBuriedFraction").isJsonPrimitive()
                || !value.getAsJsonPrimitive("minimumBuriedFraction").isNumber())
            throw new IllegalArgumentException("Expected minimumBuriedFraction as a number");
        return new Policy(value.get("minimumBuriedFraction").getAsDouble());
    }
    public static void load(Path directory) {
        var path = directory.resolve(FILE);
        try {
            Files.createDirectories(directory);
            if (!Files.exists(path)) Files.writeString(path, new GsonBuilder().setPrettyPrinting().create().toJson(DEFAULT) + "\n",
                    StandardOpenOption.CREATE_NEW);
            policy = parse(ConfigFile.read(path));
        } catch (IOException | RuntimeException failure) {
            throw new IllegalStateException("Cannot load " + path + ": " + failure.getMessage(), failure);
        }
    }
}
