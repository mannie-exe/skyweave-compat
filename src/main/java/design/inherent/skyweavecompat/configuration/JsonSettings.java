package design.inherent.skyweavecompat.configuration;

import com.google.gson.GsonBuilder;
import design.inherent.skyweavecompat.bluemap.BlueMapSettings;
import design.inherent.skyweavecompat.terrain.*;
import java.util.List;
import java.util.function.Consumer;

/** Only these owned files are editable. Validators are shared with runtime loaders. */
public final class JsonSettings {
    public record Definition(String filename, String help, String defaults, Consumer<String> validate,
            java.util.function.UnaryOperator<String> normalize, List<SettingDefinition> fields) {
        public Definition { fields = List.copyOf(fields); }
        public Definition(String filename, String help, String defaults, Consumer<String> validate,
                java.util.function.UnaryOperator<String> normalize) {
            this(filename, help, defaults, validate, normalize, List.of());
        }
        public Definition(String filename, String help, String defaults, Consumer<String> validate) {
            this(filename, help, defaults, validate, java.util.function.UnaryOperator.identity());
        }
        public SettingDefinition field(String key) {
            return fields.stream().filter(field -> field.key().equals(key)).findFirst().orElse(null);
        }
    }
    private static Definition owned(String filename, String help, Record defaults, Consumer<String> validate) {
        return owned(filename, help, defaults, validate, java.util.function.UnaryOperator.identity());
    }
    private static Definition owned(String filename, String help, Record defaults, Consumer<String> validate,
            java.util.function.UnaryOperator<String> normalize) {
        return new Definition(filename, help, json(defaults), validate, normalize, RecordSettings.fields(defaults));
    }
    private static String json(Object value) { return new GsonBuilder().setPrettyPrinting().create().toJson(value) + "\n"; }
    private static final List<Definition> DEFINITIONS = new java.util.concurrent.CopyOnWriteArrayList<>(List.of(
            owned(design.inherent.skyweavecompat.wayfinder.WayfinderSettings.FILE, "wayfinder",
                    design.inherent.skyweavecompat.wayfinder.WayfinderSettings.DEFAULT, design.inherent.skyweavecompat.wayfinder.WayfinderSettings::parse,
                    design.inherent.skyweavecompat.wayfinder.WayfinderSettings::normalize),
            owned(design.inherent.skyweavecompat.maps.MapZoomSettings.FILE, "hoofprint_zoom", design.inherent.skyweavecompat.maps.MapZoomSettings.DEFAULT, design.inherent.skyweavecompat.maps.MapZoomSettings::parse),
            owned("skyweave_compat-generation.json", "generation", GenerationProfile.INITIAL, ProfileJson::generation),
            owned(ShapeConfiguration.FILE, "shapes", ShapeProfile.INITIAL, ShapeConfiguration::parse, ShapeConfiguration::normalize),
            owned(design.inherent.skyweavecompat.climate.ClimateConfiguration.FILE, "climate",
                    design.inherent.skyweavecompat.climate.ClimateProfile.DEFAULT,
                    design.inherent.skyweavecompat.climate.ClimateConfiguration::parse,
                    design.inherent.skyweavecompat.climate.ClimateConfiguration::normalize),
            owned("skyweave_compat-interiors.json", "interiors", InteriorProfile.INITIAL, InteriorConfiguration::parse, InteriorConfiguration::normalize),
            owned("skyweave_compat-oceans.json", "oceans", OceanProfile.INITIAL, OceanConfiguration::parse, OceanConfiguration::normalize),
            owned(design.inherent.skyweavecompat.terrain.structure.StructureConfiguration.FILE, "structures",
                    design.inherent.skyweavecompat.terrain.structure.StructurePolicy.DEFAULT,
                    design.inherent.skyweavecompat.terrain.structure.StructureConfiguration::parse,
                    design.inherent.skyweavecompat.terrain.structure.StructureConfiguration::normalize),
            owned(design.inherent.skyweavecompat.terrain.city.CityConfiguration.FILE, "ancient_cities",
                    design.inherent.skyweavecompat.terrain.city.CityConfiguration.DEFAULT,
                    design.inherent.skyweavecompat.terrain.city.CityConfiguration::parse),
            owned(design.inherent.skyweavecompat.terrain.structure.SearchSettings.FILE, "structure_search",
                    design.inherent.skyweavecompat.terrain.structure.SearchSettings.DEFAULT,
                    design.inherent.skyweavecompat.terrain.structure.SearchSettings::parse),
            owned(BlueMapSettings.FILE, "bluemap", BlueMapSettings.DEFAULT, BlueMapSettings::parse)));
    public static final List<Definition> FILES = java.util.Collections.unmodifiableList(DEFINITIONS);

    /** Register an optional native codec only after its integration is admitted. */
    public static synchronized void register(Definition definition) {
        if (find(definition.filename()) != null) throw new IllegalStateException("Duplicate configuration: " + definition.filename());
        DEFINITIONS.add(definition);
    }

    public static Definition find(String filename) {
        return FILES.stream().filter(file -> file.filename().equals(filename)).findFirst().orElse(null);
    }
    private JsonSettings() {}
}
