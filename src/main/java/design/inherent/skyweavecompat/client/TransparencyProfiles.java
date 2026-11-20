package design.inherent.skyweavecompat.client;

import design.inherent.skyweavecompat.Feature;
import design.inherent.skyweavecompat.OptionalIntegrations;
import design.inherent.skyweavecompat.configuration.ConfigJson;
import design.inherent.skyweavecompat.rendering.TransparencyProfile;
import design.inherent.skyweavecompat.rendering.TransparencyProfile.Provider;
import design.inherent.skyweavecompat.rendering.TransparencyProfile.Selector;
import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.level.block.state.BlockState;

/** One immutable resource generation; compiled state decisions keep JSON out of meshing. */
public final class TransparencyProfiles {
    private static final org.slf4j.Logger LOG = com.mojang.logging.LogUtils.getLogger();
    private static volatile Snapshot current = new Snapshot(0, Map.of(), Map.of());
    private static volatile long published;
    private TransparencyProfiles() {}
    public static Snapshot current() { return current; }
    public static long publishedGeneration() { return published; }
    public static void beginPublication() { published = -1; }
    public static void publish(Snapshot snapshot) { published = snapshot.generation; }

    public record Rule(ResourceLocation profile, Feature feature, boolean enabled) {}
    public static final class Snapshot {
        private final long generation;
        private final Map<Provider, Map<BlockState, Rule>> rules;
        private final Map<ResourceLocation, String> diagnostics;
        private final Set<ResourceLocation> plainTargets;
        public Snapshot(long generation, Map<Provider, Map<BlockState, Rule>> rules, Map<ResourceLocation, String> diagnostics) {
            if (generation < 0) throw new IllegalArgumentException("Negative resource generation");
            this.generation = generation;
            var copy = new EnumMap<Provider, Map<BlockState, Rule>>(Provider.class);
            rules.forEach((provider, values) -> copy.put(provider, Map.copyOf(values)));
            this.rules = Map.copyOf(copy); this.diagnostics = Map.copyOf(diagnostics);
            var targets = new HashSet<ResourceLocation>();
            rules.getOrDefault(Provider.PLAIN, Map.of()).forEach((state, rule) -> {
                if (rule.enabled && OptionalIntegrations.enabled(rule.feature)) targets.add(BuiltInRegistries.BLOCK.getKey(state.getBlock()));
            });
            plainTargets = Set.copyOf(targets);
        }
        public long generation() { return generation; }
        public Map<Provider, Map<BlockState, Rule>> rules() { return rules; }
        public Map<ResourceLocation, String> diagnostics() { return diagnostics; }
        public boolean supports(Provider provider, BlockState state) {
            if (state == null) return false;
            Rule rule = rules.getOrDefault(provider, Map.of()).get(state);
            return rule != null && rule.enabled && (provider == Provider.MATERIAL
                    ? OptionalIntegrations.configured(rule.feature) : OptionalIntegrations.enabled(rule.feature));
        }
        public boolean wraps(ResourceLocation id) {
            return plainTargets.contains(id);
        }
    }

    /** Invoked at native model-reload entry, before constructing any model in that generation. */
    public static Snapshot reload(ResourceManager resources) {
        TransparencyDiagnostics.reset();
        var profiles = new LinkedHashMap<ResourceLocation, TransparencyProfile>();
        var diagnostics = new LinkedHashMap<ResourceLocation, String>();
        var selected = resources.listResources("transparency", id -> id.getPath().endsWith(".json"));
        if (selected.size() > 128) {
            LOG.warn("Transparency resource budget exceeded; retaining original rendering for this reload");
            current = new Snapshot(current.generation + 1, Map.of(),
                    Map.of(ResourceLocation.parse("skyweave_compat:transparency"), "More than 128 profiles; original rendering retained"));
            return current;
        }
        selected.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
            try (var stream = entry.getValue().open()) {
                byte[] bytes = stream.readNBytes(65537);
                if (bytes.length > 65536) throw new IllegalArgumentException("Profile exceeds 64 KiB");
                profiles.put(entry.getKey(), TransparencyProfile.parse(ConfigJson.object(new String(bytes, StandardCharsets.UTF_8))));
            } catch (Exception invalid) {
                diagnostics.put(entry.getKey(), invalid.getMessage() == null ? invalid.getClass().getSimpleName() : invalid.getMessage());
            }
        });
        var compiled = new EnumMap<Provider, Map<BlockState, Rule>>(Provider.class);
        var conflicts = new EnumMap<Provider, Set<BlockState>>(Provider.class);
        int entries = 0;
        for (var entry : profiles.entrySet()) {
            var profile = entry.getValue();
            try {
                var states = resolve(profile.targets()); states.removeAll(resolve(profile.exclusions()));
                if (states.size() > 32768 || (entries += states.size() * profile.providers().size()) > 131072)
                    throw new IllegalArgumentException("Compiled profile state budget exceeded");
                var rule = new Rule(entry.getKey(), profile.feature(), profile.enabled());
                for (Provider provider : profile.providers()) {
                    var decisions = compiled.computeIfAbsent(provider, ignored -> new HashMap<>());
                    var rejected = conflicts.computeIfAbsent(provider, ignored -> new HashSet<>());
                    for (var state : states) {
                        if (rejected.contains(state)) continue;
                        Rule previous = decisions.putIfAbsent(state, rule);
                        if (previous != null) {
                            rejected.add(state); decisions.remove(state);
                            diagnostics.put(entry.getKey(), "Overlapping " + provider + " selectors with " + previous.profile);
                        }
                    }
                }
            } catch (IllegalArgumentException invalid) { diagnostics.put(entry.getKey(), invalid.getMessage()); }
        }
        current = new Snapshot(current.generation + 1, compiled, diagnostics);
        diagnostics.entrySet().stream().limit(32).forEach(e -> LOG.warn("Transparency profile {}: {}", e.getKey(), e.getValue()));
        return current;
    }

    private static Set<BlockState> resolve(List<Selector> selectors) {
        var result = new HashSet<BlockState>();
        for (var selector : selectors) {
            if (selector.block() != null && !BuiltInRegistries.BLOCK.containsKey(ResourceLocation.parse(selector.block())))
                continue; // An optional content mod may be absent.
            for (var block : BuiltInRegistries.BLOCK) {
                var id = BuiltInRegistries.BLOCK.getKey(block);
                if (selector.block() != null ? !id.toString().equals(selector.block()) : !selector.family().matches(id.getNamespace(), id.getPath(), name -> hierarchy(block, name))) continue;
                for (var property : selector.state().entrySet()) {
                    var nativeProperty = block.getStateDefinition().getProperty(property.getKey());
                    if (nativeProperty == null || nativeProperty.getValue(property.getValue()).isEmpty())
                        throw new IllegalArgumentException("Unknown state selector for " + id + ": " + property);
                }
                for (var state : block.getStateDefinition().getPossibleStates()) {
                    boolean match = true;
                    for (var property : selector.state().entrySet()) {
                        var nativeProperty = block.getStateDefinition().getProperty(property.getKey());
                        if (!propertyValue(state, nativeProperty).equals(property.getValue())) { match = false; break; }
                    }
                    if (match) result.add(state);
                }
            }
        }
        return result;
    }

    private static boolean hierarchy(Object object, String name) {
        for (Class<?> c = object.getClass(); c != null; c = c.getSuperclass()) if (c.getName().equals(name)) return true;
        return false;
    }
    private static <T extends Comparable<T>> String propertyValue(BlockState state, net.minecraft.world.level.block.state.properties.Property<T> property) {
        return property.getName(state.getValue(property));
    }
}
