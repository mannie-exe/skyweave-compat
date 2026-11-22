package design.inherent.skyweavecompat;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Retain strings only so error reporting never resolves an optional target class. */
public final class DependencyDiagnostics {
    private DependencyDiagnostics() {}
    private static final String MIXINS = "design.inherent.skyweavecompat.mixin.";
    private record Snapshot(Map<String, String> contexts, String platform) {}
    private static volatile Snapshot snapshot = new Snapshot(Map.of(), "platform metadata unavailable");

    public static void prepare(FeatureSelection selection, boolean client) {
        var owners = new LinkedHashMap<String, List<String>>();
        var selectedMixins = Set.copyOf(selection.mixins(client));
        var alternatives = new java.util.TreeMap<String, String>();
        for (var feature : Feature.values()) {
            if (!selection.enabled(feature)) continue;
            var single = new FeatureSelection(Set.of(feature), Map.of(), Map.of(), Map.of());
            var context = selection.describe(feature);
            com.mojang.logging.LogUtils.getLogger().debug("Skyweave: Compatibility Patches admitted adapter: {}", context);
            var featureMixins = new java.util.HashSet<>(single.mixins(client));
            // Include hooks requiring two selected features, while keeping all owners
            // of a shared either-feature hook. Derive both from the real selector.
            var remaining = new java.util.HashSet<>(selection.active());
            remaining.remove(feature);
            var without = new FeatureSelection(remaining, Map.of(), Map.of(), Map.of()).mixins(client);
            for (var mixin : selectedMixins) if (!without.contains(mixin)) featureMixins.add(mixin);
            featureMixins.retainAll(selectedMixins);
            for (var mixin : featureMixins)
                owners.computeIfAbsent(MIXINS + mixin, ignored -> new ArrayList<>()).add(context);
            FeatureSelection.dependencies(feature).forEach((id, baseline) -> {
                if (!baseline.equals(selection.installed().get(id)))
                    alternatives.put(id, DependencyVersions.contract(id, baseline).describe(id, selection.installed().get(id)));
            });
        }
        if (!alternatives.isEmpty())
            com.mojang.logging.LogUtils.getLogger().info("Skyweave: Compatibility Patches admitted alternative dependency versions: {}", alternatives);
        if (selection.enabled(Feature.SHADER_PATCHES)) {
            var context = selection.describe(Feature.SHADER_PATCHES) + "; "
                    + DependencyVersions.exact(design.inherent.skyweavecompat.shader.ShaderEngine.EUPHORIA_VERSION)
                            .describe("euphoria_patcher", selection.installed().get("euphoria_patcher"));
            for (var mixin : List.of("EuphoriaAssemblyMixin", "EuphoriaOwnedOutputMixin", "EuphoriaDetectorMixin"))
                owners.put(MIXINS + "client." + mixin, List.of(context));
        }
        var retained = new LinkedHashMap<String, String>();
        owners.forEach((mixin, details) -> retained.put(mixin, String.join("; ", details)));
        var base = DependencyVersions.describeBase(selection.installed()).entrySet().stream()
                .filter(entry -> selection.installed().containsKey(entry.getKey())).map(Map.Entry::getValue).toList();
        snapshot = new Snapshot(Map.copyOf(retained), base.isEmpty() ? "platform metadata unavailable" : String.join("; ", base));
    }

    public static String forMixin(String mixin) {
        var current = snapshot;
        return current.contexts().getOrDefault(mixin, "skyweave_compat; adapter admission unavailable") + "; " + current.platform();
    }
}
