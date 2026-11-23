package design.inherent.skyweavecompat.client;

import design.inherent.reform.definition.FieldPath;
import design.inherent.reform.presentation.FormCatalog;
import design.inherent.reform.presentation.FormDependencies;
import design.inherent.reform.presentation.FormPresentation;
import design.inherent.skyweavecompat.configuration.JsonSettings;
import design.inherent.skyweavecompat.configuration.NativeFields;
import design.inherent.skyweavecompat.configuration.NativeSettingsPolicy;
import design.inherent.skyweavecompat.configuration.ReformDocuments;
import design.inherent.skyweavecompat.configuration.SettingsCatalog;
import design.inherent.skyweavecompat.configuration.SettingsLayout;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.minecraft.network.chat.Component;

/** Catalog labels and relations reuse the current domain metadata and native field identities. */
final class SettingsPresentation {
    private SettingsPresentation() {}

    static FormPresentation json(JsonSettings.Definition source) {
        return json(source, true);
    }

    static FormPresentation policy(JsonSettings.Definition source) {
        return json(source, false);
    }

    private static FormPresentation json(JsonSettings.Definition source, boolean local) {
        var entry = SettingsCatalog.file(source.filename());
        boolean fieldTiming = local && uniformTiming(source).isEmpty();
        var sections = source.fields().stream().map(field -> {
            String key = "skyweave_compat.settings.field." + entry.help() + "." + field.key();
            return new FormCatalog.Section(ReformDocuments.path(field.key()), key,
                    Component.translatableWithFallback(key, readable(field.key())).getString(),
                    Component.translatableWithFallback(key + ".help", "").getString())
                    .withSupplementalHelp(fieldTiming ? afterSave(entry.timing(field.key())) : "");
        }).toList();
        var ranges = SettingsCatalog.ranges(source.filename()).stream().map(range -> new FormPresentation.Range(
                range.id(), range.labelKey(), Component.translatable(range.labelKey()).getString(), "",
                ReformDocuments.path(range.minimum()), ReformDocuments.path(range.maximum()))).toList();
        return new FormPresentation(ranges, SettingsCatalog.dependencies(source.filename()), new FormCatalog(sections))
                .withLayout(SettingsLayout.json(source));
    }

    /** Uniform local context belongs beside the document, rather than repeated on every field. */
    static Component documentHelp(SettingsCatalog.FileEntry entry) {
        var help = Component.translatable(entry.summaryKey()).copy();
        var source = JsonSettings.find(entry.filename());
        if (source == null) return help;
        uniformTiming(source).ifPresent(timing -> help.append("\n").append(afterSave(timing)));
        if (!NativePreferences.admitted(entry))
            help.append("\n").append(Component.translatable("skyweave_compat.settings.staged_inactive"));
        return help;
    }

    private static java.util.Optional<SettingsCatalog.Timing> uniformTiming(JsonSettings.Definition source) {
        var entry = SettingsCatalog.file(source.filename());
        var timings = source.fields().stream().map(field -> entry.timing(field.key())).distinct().toList();
        return timings.size() == 1 ? java.util.Optional.of(timings.getFirst()) : java.util.Optional.empty();
    }

    private static String afterSave(SettingsCatalog.Timing timing) {
        return Component.translatable("skyweave_compat.settings.after_save."
                + timing.name().toLowerCase(java.util.Locale.ROOT)).getString();
    }

    static FormPresentation nativeFields(SettingsCatalog.FileEntry entry, List<NativeFields.Field> fields) {
        var sections = fields.stream().map(field -> {
            String translation = field.spec().getTranslationKey();
            if (translation == null || translation.isBlank()) translation = "skyweave_compat.settings.native." + entry.help() + "." + field.key();
            String help = Component.translatableWithFallback(translation + ".help",
                    field.spec().getComment() == null ? "" : field.spec().getComment()).getString();
            String facts = afterSave(field.timing(entry.filename()));
            for (String notice : entry.notices(field.key())) facts += "\n" + Component.translatable("skyweave_compat.settings." + notice).getString();
            return new FormCatalog.Section(NativeSettingsPolicy.path(field), translation,
                    Component.translatableWithFallback(translation, readable(field.key())).getString(), help)
                    .withSupplementalHelp(facts);
        }).toList();
        var dependencies = new ArrayList<FormDependencies.Rule>();
        if (entry.help().equals("rendering_client")) {
            var mode = FieldPath.ROOT.field("clouds").field("mode");
            dependencies.add(new FormDependencies.Rule("clouds_absolute", FieldPath.ROOT.field("clouds").field("absolute"),
                    mode, Set.of("ABSOLUTE"), "skyweave_compat.settings.rendering.absolute_mode", "Used in Absolute cloud mode"));
            dependencies.add(new FormDependencies.Rule("clouds_offset", FieldPath.ROOT.field("clouds").field("offset"),
                    mode, Set.of("MAPPED", "VANILLA"), "skyweave_compat.settings.rendering.offset_mode", "Used in Mapped or Vanilla cloud mode"));
        }
        var unavailable = fields.stream().filter(field -> {
            var feature = entry.fieldOwner(field.key());
            return feature != null && !design.inherent.skyweavecompat.OptionalIntegrations.enabled(feature);
        }).map(field -> new FormPresentation.UnavailableField(NativeSettingsPolicy.path(field),
                "skyweave_compat.settings.field_unavailable", "The owning adaptation is inactive: "
                        + design.inherent.skyweavecompat.OptionalIntegrations.selection().inactive().get(entry.fieldOwner(field.key())))).toList();
        return new FormPresentation(List.of(), new FormDependencies(dependencies), new FormCatalog(sections))
                .withLayout(SettingsLayout.nativeFields(fields))
                .withUnavailableFields(unavailable);
    }

    private static String readable(String key) {
        String leaf = key.substring(key.lastIndexOf('.') + 1);
        String words = leaf.replace('_', ' ').replaceAll("([a-z])([A-Z])", "$1 $2");
        return words.isEmpty() ? key : Character.toUpperCase(words.charAt(0)) + words.substring(1);
    }
}
