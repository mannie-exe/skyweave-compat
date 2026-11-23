package design.inherent.skyweavecompat.configuration;

import design.inherent.reform.definition.FieldPath;
import design.inherent.reform.presentation.FormLayout;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/** Presentation relationships reuse authored metadata without changing a document's domain. */
public final class SettingsLayout {
    private record Related(String parent, String name) {}
    private SettingsLayout() {}

    public static FormLayout json(JsonSettings.Definition source) {
        var related = new LinkedHashMap<Related, List<SettingDefinition>>();
        for (var field : source.fields()) {
            if (field.group().isBlank()) continue;
            String parent = parent(field.key());
            related.computeIfAbsent(new Related(parent, field.group()), ignored -> new ArrayList<>()).add(field);
        }
        var ranges = SettingsCatalog.ranges(source.filename());
        Set<String> endpoints = ranges.stream().flatMap(range -> java.util.stream.Stream.of(range.minimum(), range.maximum()))
                .collect(Collectors.toSet());
        var rows = new ArrayList<FormLayout.Row>();
        var groups = new ArrayList<FormLayout.Group>();
        for (var fields : related.values()) {
            if (fields.size() < 2) continue;
            var keys = fields.stream().map(SettingDefinition::key).toList();
            // A genuine ordered range already supplies its own labelled box and pair.
            if (ranges.stream().anyMatch(range -> keys.size() == 2 && keys.contains(range.minimum()) && keys.contains(range.maximum()))) continue;
            String identity = "related-" + groups.size();
            String key = "skyweave_compat.settings.group." + source.help() + "." + fields.getFirst().group();
            groups.add(new FormLayout.Group(identity, key, readable(fields.getFirst().group()), "",
                    keys.stream().map(ReformDocuments::path).toList()));
            var leaves = fields.stream().filter(field -> !endpoints.contains(field.key())
                    && field.kind() != SettingDefinition.Kind.STRINGS).map(field -> ReformDocuments.path(field.key())).toList();
            for (int offset = 0; leaves.size() - offset >= 2;) {
                int count = leaves.size() - offset == 4 ? 2 : Math.min(3, leaves.size() - offset);
                rows.add(new FormLayout.Row(identity + "/row-" + offset, leaves.subList(offset, offset + count)));
                offset += count;
            }
        }
        return new FormLayout(rows, groups);
    }

    /** Native categories are boxes, not evidence of a paired or ordered numeric relationship. */
    public static FormLayout nativeFields(List<NativeFields.Field> fields) {
        var categories = new LinkedHashMap<String, List<FieldPath>>();
        for (var field : fields) {
            var segments = field.binding().getPath();
            if (segments.size() < 2) continue;
            String category = String.join(".", segments.subList(0, segments.size() - 1));
            categories.computeIfAbsent(category, ignored -> new ArrayList<>()).add(NativeSettingsPolicy.path(field));
        }
        var groups = new ArrayList<FormLayout.Group>();
        categories.forEach((category, paths) -> groups.add(new FormLayout.Group("native-" + groups.size(),
                "skyweave_compat.settings.registered_group." + category, readable(category), "", paths)));
        return new FormLayout(List.of(), groups);
    }

    private static String parent(String key) { return key.substring(0, key.lastIndexOf('.') + 1); }

    private static String readable(String key) {
        String leaf = key.substring(key.lastIndexOf('.') + 1);
        String words = leaf.replace('_', ' ').replaceAll("([a-z])([A-Z])", "$1 $2");
        return words.isEmpty() ? key : Character.toUpperCase(words.charAt(0)) + words.substring(1);
    }
}
