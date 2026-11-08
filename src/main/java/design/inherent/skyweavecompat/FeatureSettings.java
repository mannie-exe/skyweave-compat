package design.inherent.skyweavecompat;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.EnumMap;
import java.util.Map;
import java.util.Properties;

/** Immutable startup switches, deliberately independent of game and loader classes. */
public record FeatureSettings(Map<Feature, Boolean> values) {
    public static final String FILE = "skyweave_compat-features.properties";

    public FeatureSettings { values = Map.copyOf(values); }

    public boolean enabled(Feature feature) {
        return values.getOrDefault(feature, feature.defaultEnabled);
    }

    public static FeatureSettings read(Reader reader) throws IOException {
        var properties = new Properties() {
            @Override public synchronized Object put(Object key, Object value) {
                if (containsKey(key)) throw new IllegalArgumentException("Duplicate feature setting: " + key);
                return super.put(key, value);
            }
        };
        properties.load(reader);
        var values = new EnumMap<Feature, Boolean>(Feature.class);
        for (Feature feature : Feature.values()) {
            String value = (String) properties.remove(feature.key);
            if (value == null) continue;
            if (!value.equals("true") && !value.equals("false"))
                throw new IllegalArgumentException(feature.key + " must be true or false");
            values.put(feature, Boolean.parseBoolean(value));
        }
        if (!properties.isEmpty()) throw new IllegalArgumentException("Unknown feature settings: " + properties.keySet());
        return new FeatureSettings(values);
    }

    public static FeatureSettings load(Path configDirectory) throws IOException {
        Path path = configDirectory.resolve(FILE);
        if (!Files.exists(path)) {
            Files.createDirectories(configDirectory);
            try {
                String initial = defaults();
                // Retain an existing village-filter opt-out when first introducing startup switches.
                Path village = configDirectory.resolve("skyweave_compat-common.toml");
                if (Files.isRegularFile(village) && java.util.regex.Pattern.compile("(?m)^\\s*enabled\\s*=\\s*false\\s*(?:#.*)?$")
                        .matcher(Files.readString(village)).find())
                    initial = initial.replace("villages.clearance=true", "villages.clearance=false");
                Files.writeString(path, initial, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
            } catch (java.nio.file.FileAlreadyExistsException race) {
                // Another loader invocation supplied the authoritative file.
            }
        }
        try (var reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) { return read(reader); }
    }

    public static String defaults() {
        var text = new StringBuilder("# Skyweave: Compatibility Patches adaptations. Changes require a full game/server restart.\n"
                + "# Missing dependencies leave an adaptation inactive. See docs/configuration.md.\n"
                + "# Disable terrain.preset to use only independent fixes. No world preset is selected automatically.\n");
        for (Feature feature : Feature.values())
            text.append(feature.key).append('=').append(feature.defaultEnabled).append('\n');
        return text.toString();
    }
}
