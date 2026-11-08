package design.inherent.skyweavecompat.configuration;

import com.google.gson.GsonBuilder;
import design.inherent.skyweavecompat.bluemap.BlueMapSettings;
import design.inherent.skyweavecompat.terrain.*;
import java.util.List;
import java.util.function.Consumer;

/** Only these owned files are editable. Validators are shared with runtime loaders. */
public final class JsonSettings {
    public record Definition(String filename, String help, String defaults, Consumer<String> validate,
            java.util.function.UnaryOperator<String> normalize) {
        public Definition(String filename, String help, String defaults, Consumer<String> validate) {
            this(filename, help, defaults, validate, java.util.function.UnaryOperator.identity());
        }
    }
    private static String json(Object value) { return new GsonBuilder().setPrettyPrinting().create().toJson(value) + "\n"; }
    private static final List<Definition> DEFINITIONS = new java.util.concurrent.CopyOnWriteArrayList<>(List.of(
            new Definition("skyweave_compat-generation.json", "generation", json(GenerationProfile.INITIAL), ProfileJson::generation),
            new Definition(ShapeConfiguration.FILE, "shapes", json(ShapeProfile.INITIAL), ShapeConfiguration::parse, ShapeConfiguration::normalize),
            new Definition("skyweave_compat-interiors.json", "interiors", json(InteriorProfile.INITIAL), InteriorConfiguration::parse),
            new Definition("skyweave_compat-oceans.json", "oceans", json(OceanProfile.INITIAL), OceanConfiguration::parse, OceanConfiguration::normalize),
            new Definition(design.inherent.skyweavecompat.terrain.structure.StructureConfiguration.FILE, "structures",
                    json(design.inherent.skyweavecompat.terrain.structure.StructurePolicy.DEFAULT),
                    design.inherent.skyweavecompat.terrain.structure.StructureConfiguration::parse),
            new Definition(design.inherent.skyweavecompat.terrain.city.CityConfiguration.FILE, "ancient_cities",
                    json(design.inherent.skyweavecompat.terrain.city.CityConfiguration.DEFAULT),
                    design.inherent.skyweavecompat.terrain.city.CityConfiguration::parse),
            new Definition(BlueMapSettings.FILE, "bluemap", json(BlueMapSettings.DEFAULT), BlueMapSettings::parse)));
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
