package design.inherent.skyweavecompat.client;

import design.inherent.reform.definition.DocumentDefinition;
import design.inherent.reform.definition.FieldPath;
import design.inherent.reform.edit.DraftNode;
import design.inherent.reform.edit.DraftTree;
import design.inherent.reform.minecraft.client.ClientForm;
import design.inherent.reform.minecraft.client.FormInformation;
import design.inherent.skyweavecompat.DependencyVersions;
import design.inherent.skyweavecompat.Feature;
import design.inherent.skyweavecompat.FeatureSelection;
import design.inherent.skyweavecompat.FeatureSettings;
import design.inherent.skyweavecompat.OptionalIntegrations;
import design.inherent.skyweavecompat.configuration.SettingsCatalog;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import net.minecraft.network.chat.Component;
import net.neoforged.fml.loading.LoadingModList;

/** Cached domain facts for the shared information view; no separate editor or source reader. */
final class AdaptationInformation {
    private AdaptationInformation() {}

    static FormInformation<FeatureSettings> describe(DocumentDefinition<FeatureSettings> definition,
            Map<String, ClientForm> relatedForms) {
        var forms = Map.copyOf(relatedForms);
        var selection = OptionalIntegrations.selection();
        var loaded = OptionalIntegrations.startupSettings();
        var names = new LinkedHashMap<String, String>();
        LoadingModList.get().getMods().forEach(mod -> names.put(mod.getModId(), mod.getDisplayName()));
        var modNames = Map.copyOf(names);
        return FormInformation.of(definition, (information, view) -> {
            var selected = Arrays.stream(Feature.values())
                    .filter(feature -> information.path().equals(FieldPath.ROOT.field(feature.key))).findFirst();
            if (selected.isEmpty()) return FormInformation.Subject.EMPTY;
            var feature = selected.orElseThrow();
            var entry = SettingsCatalog.entry(feature);
            var facts = new ArrayList<FormInformation.Fact>();
            facts.add(fact("running", state(feature, selection, modNames)));
            facts.add(fact("loaded", choice(loaded.enabled(feature), loaded.values().containsKey(feature))));
            facts.add(fact("draft", view.snapshot().map(snapshot -> choice(snapshot.draft(), feature))
                    .orElseGet(() -> text("unknown"))));
            facts.add(fact("next_start", view.baseline().map(baseline -> choice(baseline, feature))
                    .orElseGet(() -> text("unknown"))));
            facts.add(fact("fallback", on(feature.defaultEnabled)));
            facts.add(fact("scope", settings(feature == Feature.BLUEMAP ? "dedicated"
                    : FeatureSelection.clientOnly(feature) ? "client" : "both")));
            FeatureSelection.dependencies(feature).entrySet().stream().sorted(Map.Entry.comparingByKey())
                    .forEach(dependency -> {
                        var contract = DependencyVersions.contract(dependency.getKey(), dependency.getValue());
                        facts.add(new FormInformation.Fact(
                                text("dependency", modNames.getOrDefault(dependency.getKey(), dependency.getKey())),
                                Component.literal(contract.describe(dependency.getKey(), selection.installed().get(dependency.getKey())))));
                    });
            var related = new ArrayList<FormInformation.Related>();
            var prerequisite = FeatureSelection.prerequisite(feature);
            if (prerequisite != null) {
                facts.add(fact("prerequisite", featureName(prerequisite)));
                related.add(new FormInformation.Related(definition.id(), FieldPath.ROOT.field(prerequisite.key),
                        featureName(prerequisite)));
            }
            SettingsCatalog.FILES.stream().filter(file -> file.owners().contains(feature))
                    .filter(file -> forms.containsKey(file.filename())).forEach(file -> {
                        var form = forms.get(file.filename());
                        related.add(new FormInformation.Related(form.document(), FieldPath.ROOT,
                                Component.translatable(file.labelKey())));
                    });
            return new FormInformation.Subject(Component.translatable(entry.summaryKey()), facts, related,
                    Optional.of(feature.key)).withTechnicalDetails(FormInformation.TechnicalDetails.DISCLOSURE);
        });
    }

    private static FormInformation.Fact fact(String label, Component value) {
        return new FormInformation.Fact(text(label), value);
    }

    private static Component choice(DraftTree tree, Feature feature) {
        var path = FieldPath.ROOT.field(feature.key);
        if (!tree.contains(path)) return choice(feature.defaultEnabled, false);
        var node = tree.at(path);
        if (node instanceof DraftNode.Defaulted value) {
            if (!value.overridden()) return choice(feature.defaultEnabled, false);
            node = value.value();
        }
        if (node instanceof DraftNode.Scalar scalar && (scalar.raw().equals("true") || scalar.raw().equals("false")))
            return choice(scalar.raw().equals("true"), true);
        return text("unknown");
    }

    private static Component choice(boolean enabled, boolean explicit) {
        return on(enabled).copy().append(" (").append(settings(explicit ? "explicit" : "omitted")).append(")");
    }

    private static Component state(Feature feature, FeatureSelection selection, Map<String, String> names) {
        if (selection.enabled(feature)) return settings("active");
        var reason = selection.reasons().get(feature);
        if (reason == null) return settings("disabled");
        return switch (reason.kind()) {
            case DISABLED -> settings("disabled");
            case WRONG_SIDE -> settings("wrong_side");
            case PREREQUISITE -> settings("prerequisite", featureName(FeatureSelection.prerequisite(feature)));
            case MISSING -> settings("missing", String.join(", ", reason.details().stream()
                    .map(id -> names.getOrDefault(id, id)).toList()));
            case EXTERNAL -> settings("external", String.join(", ", reason.details()));
        };
    }

    private static Component featureName(Feature feature) {
        return Component.translatable(SettingsCatalog.entry(feature).labelKey());
    }

    private static Component on(boolean value) { return Component.translatable(value ? "options.on" : "options.off"); }
    private static Component text(String key, Object... values) {
        return Component.translatable("skyweave_compat.settings.information." + key, values);
    }
    private static Component settings(String key, Object... values) {
        return Component.translatable("skyweave_compat.settings." + key, values);
    }
}
