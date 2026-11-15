package design.inherent.skyweavecompat.rendering;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import design.inherent.skyweavecompat.Feature;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Data-only selectors. Provider IDs select fixed adapters, never executable resource code. */
public record TransparencyProfile(boolean enabled, Feature feature, Set<Provider> providers,
        List<Selector> targets, List<Selector> exclusions) {
    public enum Provider { PLAIN, CREATE_CT, FUSION, MATERIAL }
    public enum Family {
        VANILLA_GLASS, VANILLA_PANES, TINTED_GLASS, CREATE_GLASS, CREATE_TILED,
        RECHISELED_PILLARS, MACAW_MOSAIC, CARAMEL_BRICKS, BUBBLE_GUM, ORNATE_GRATE, INDUSTRIAL_GRATING;

        public boolean matches(String ns, String path, java.util.function.Predicate<String> hierarchy) {
            return switch (this) {
                case VANILLA_GLASS -> ns.equals("minecraft") && (path.equals("glass") || path.endsWith("_stained_glass"));
                case VANILLA_PANES -> ns.equals("minecraft") && (path.equals("glass_pane") || path.endsWith("_stained_glass_pane"));
                case TINTED_GLASS -> ns.equals("minecraft") && path.equals("tinted_glass");
                case CREATE_TILED -> ns.equals("create") && (path.equals("tiled_glass") || path.equals("tiled_glass_pane"));
                case CREATE_GLASS -> hierarchy.test("com.simibubi.create.content.decoration.palettes.ConnectedGlassBlock")
                        || hierarchy.test("com.simibubi.create.content.decoration.palettes.WindowBlock")
                        || hierarchy.test("com.simibubi.create.content.decoration.palettes.GlassPaneBlock");
                case RECHISELED_PILLARS -> hierarchy.test("com.supermartijn642.rechiseled.blocks.RechiseledGlassPillarBlock");
                case MACAW_MOSAIC -> ns.equals("mcwwindows") && (path.endsWith("_mosaic_glass") || path.endsWith("_mosaic_glass_pane"));
                case CARAMEL_BRICKS -> ns.equals("create_confectionery") && Set.of("caramel_bricks", "caramel_bricks_slab", "caramel_bricks_stairs").contains(path);
                case BUBBLE_GUM -> ns.equals("linearbearing") && path.equals("bubble_gum_block");
                case ORNATE_GRATE -> ns.equals("dndecor") && path.equals("ornate_grate");
                case INDUSTRIAL_GRATING -> ns.equals("bits_n_bobs") && path.equals("industrial_grating");
            };
        }
    }
    public record Selector(String block, Family family, Map<String, String> state) {
        public Selector { state = Map.copyOf(state); }
    }
    public TransparencyProfile {
        providers = Set.copyOf(providers); targets = List.copyOf(targets); exclusions = List.copyOf(exclusions);
    }
    public static TransparencyProfile parse(JsonObject json) {
        keys(json, Set.of("schema", "enabled", "feature", "providers", "targets", "exclusions"));
        if (!json.has("schema") || !json.get("schema").isJsonPrimitive() || !json.getAsJsonPrimitive("schema").isNumber()
                || !json.get("schema").getAsString().equals("1")) throw new IllegalArgumentException("Expected schema integer 1");
        boolean enabled = true;
        if (json.has("enabled")) {
            if (!json.get("enabled").isJsonPrimitive() || !json.getAsJsonPrimitive("enabled").isBoolean())
                throw new IllegalArgumentException("Expected boolean enabled");
            enabled = json.get("enabled").getAsBoolean();
        }
        String key = string(json.get("feature"), 100);
        Feature feature = java.util.Arrays.stream(Feature.values()).filter(f -> f.key.equals(key)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown adapter feature"));
        Set<Provider> allowed = switch (feature) {
            case CREATE_BACKFACES -> EnumSet.of(Provider.PLAIN, Provider.CREATE_CT, Provider.MATERIAL);
            case FUSION_BACKFACES -> EnumSet.of(Provider.PLAIN, Provider.FUSION, Provider.MATERIAL);
            case MACAW_GLASS, CONFECTIONERY_BACKFACES, BUBBLE_GUM_BACKFACES, ORNATE_GRATE_BACKFACES,
                    INDUSTRIAL_GRATING_BACKFACES -> EnumSet.of(Provider.PLAIN, Provider.MATERIAL);
            default -> throw new IllegalArgumentException("Feature is not a glass adapter");
        };
        if (!json.has("providers") || !json.get("providers").isJsonArray()
                || json.getAsJsonArray("providers").isEmpty() || json.getAsJsonArray("providers").size() > 4)
            throw new IllegalArgumentException("Expected bounded providers");
        var providers = EnumSet.noneOf(Provider.class);
        for (var entry : json.getAsJsonArray("providers")) {
            var p = Provider.valueOf(string(entry, 32).toUpperCase(java.util.Locale.ROOT));
            if (!allowed.contains(p) || !providers.add(p)) throw new IllegalArgumentException("Invalid or repeated provider");
        }
        return new TransparencyProfile(enabled, feature, providers, selectors(json.get("targets"), true),
                selectors(json.get("exclusions"), false));
    }

    private static List<Selector> selectors(JsonElement value, boolean required) {
        if (value == null && !required) return List.of();
        if (value == null || !value.isJsonArray() || value.getAsJsonArray().size() > 128
                || required && value.getAsJsonArray().isEmpty()) throw new IllegalArgumentException("Expected bounded selectors");
        var result = new ArrayList<Selector>();
        for (var raw : value.getAsJsonArray()) {
            if (!raw.isJsonObject()) throw new IllegalArgumentException("Expected selector object");
            var object = raw.getAsJsonObject(); keys(object, Set.of("block", "family", "state"));
            if (object.has("block") == object.has("family")) throw new IllegalArgumentException("Select block or family");
            String block = object.has("block") ? string(object.get("block"), 256) : null;
            if (block != null && !block.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")) throw new IllegalArgumentException("Invalid block ID");
            Family family = object.has("family") ? Family.valueOf(string(object.get("family"), 48).toUpperCase(java.util.Locale.ROOT)) : null;
            var state = new LinkedHashMap<String, String>();
            if (object.has("state")) {
                if (!object.get("state").isJsonObject() || object.getAsJsonObject("state").size() > 16)
                    throw new IllegalArgumentException("Expected bounded state selector");
                object.getAsJsonObject("state").entrySet().forEach(e -> {
                    if (!e.getKey().matches("[a-z0-9_]{1,64}")) throw new IllegalArgumentException("Invalid state property");
                    state.put(e.getKey(), string(e.getValue(), 64));
                });
            }
            result.add(new Selector(block, family, state));
        }
        return List.copyOf(result);
    }
    private static String string(JsonElement value, int maximum) {
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()
                || value.getAsString().isEmpty() || value.getAsString().length() > maximum)
            throw new IllegalArgumentException("Expected bounded string");
        return value.getAsString();
    }
    private static void keys(JsonObject object, Set<String> keys) {
        if (!keys.containsAll(object.keySet())) throw new IllegalArgumentException("Unknown profile field");
    }
}
