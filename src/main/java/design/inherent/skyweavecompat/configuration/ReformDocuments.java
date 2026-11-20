package design.inherent.skyweavecompat.configuration;

import design.inherent.reform.definition.DocumentDefinition;
import design.inherent.reform.definition.DocumentId;
import design.inherent.reform.definition.FieldKey;
import design.inherent.reform.definition.FieldPath;
import design.inherent.reform.definition.RecordType;
import design.inherent.reform.definition.RecordTypes;
import design.inherent.reform.edit.Candidate;
import design.inherent.reform.edit.DraftLimits;
import design.inherent.reform.edit.DraftNode;
import design.inherent.reform.edit.DraftTree;
import design.inherent.reform.presentation.NumericDisplay;
import design.inherent.reform.serialization.DecimalText;
import design.inherent.reform.serialization.JsonLimits;
import design.inherent.reform.storage.JsonStore;
import design.inherent.reform.storage.OwnedFile;
import design.inherent.reform.storage.PropertiesStore;
import design.inherent.reform.storage.StorageBinding;
import design.inherent.reform.validation.Issue;
import design.inherent.reform.validation.ValidationReport;
import design.inherent.reform.value.ValueType;
import design.inherent.reform.value.ValueTypes;
import design.inherent.skyweavecompat.Feature;
import design.inherent.skyweavecompat.FeatureSelection;
import design.inherent.skyweavecompat.FeatureSettings;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Retains Compat's existing bare JSON and configuration-file bounds. */
public final class ReformDocuments {
    private static final JsonLimits JSON = new JsonLimits(ConfigFile.LIMIT, 16, ConfigFile.LIMIT, ConfigFile.LIMIT,
            128, 4096);
    private static final RecordType<FeatureSettings> FEATURES = featureType();

    private ReformDocuments() {}

    public static <T extends Record> DocumentDefinition<T> record(String filename, Class<T> type, T defaults) {
        return new DocumentDefinition<>(DocumentId.parse(SettingsCatalog.file(filename).id()), 1,
                recordType(type), () -> defaults);
    }

    /** The existing record constructor remains the authority for cross-field rules. */
    private static <T extends Record> ValueType<T> recordType(Class<T> type) {
        var builder = RecordTypes.record(type);
        for (var component : type.getRecordComponents()) {
            var javaType = component.getType();
            var spec = component.getAnnotation(SettingSpec.class);
            if (javaType.isRecord()) {
                builder.field(component.getName(), FieldKey.of(component.getName(), recordType(javaType.asSubclass(Record.class))));
            } else if (javaType == boolean.class || javaType == Boolean.class) {
                builder.field(component.getName(), FieldKey.of(component.getName(), ValueTypes.bool()));
            } else if (spec != null) {
                var bounds = SettingConstraints.from(spec);
                var scalar = scalarType(javaType, bounds);
                if (scalar != null)
                    builder.field(component.getName(), FieldKey.of(component.getName(), scalar));
            }
        }
        return builder.build();
    }

    private static ValueType<?> scalarType(Class<?> type, SettingConstraints bounds) {
        var minimum = bounds.minimum();
        var maximum = bounds.maximum();
        if (type == int.class || type == Integer.class)
            return display(ValueTypes.integer(minimum == null ? Integer.MIN_VALUE : minimum.intValueExact(),
                    maximum == null ? Integer.MAX_VALUE : maximum.intValueExact()), bounds);
        if (type == long.class || type == Long.class)
            return display(ValueTypes.longInteger(minimum == null ? Long.MIN_VALUE : minimum.longValueExact(),
                    maximum == null ? Long.MAX_VALUE : maximum.longValueExact()), bounds);
        if (type == double.class || type == Double.class)
            return display(ValueTypes.decimal(minimum == null ? BigDecimal.valueOf(-Double.MAX_VALUE) : minimum,
                    maximum == null ? BigDecimal.valueOf(Double.MAX_VALUE) : maximum), bounds);
        if (type == float.class || type == Float.class)
            return display(ValueTypes.floating(minimum == null ? -Float.MAX_VALUE : minimum.floatValue(),
                    maximum == null ? Float.MAX_VALUE : maximum.floatValue()), bounds);
        if (type == String.class && bounds.maximumLength() >= 0)
            return ValueTypes.text(bounds.maximumLength());
        if (type == List.class && bounds.maximumEntries() >= 0)
            return ValueTypes.list(ValueTypes.text(bounds.maximumLength() < 0 ? 4096 : bounds.maximumLength()),
                    bounds.maximumEntries());
        return null;
    }

    private static RecordType<FeatureSettings> featureType() {
        var keys = new EnumMap<Feature, FieldKey<Boolean>>(Feature.class);
        var builder = RecordType.<FeatureSettings>builder(values -> {
            var settings = new EnumMap<Feature, Boolean>(Feature.class);
            keys.forEach((feature, key) -> settings.put(feature, values.get(key)));
            return new FeatureSettings(settings);
        });
        for (var feature : Feature.values()) {
            var key = FieldKey.of(feature.key, ValueTypes.defaultedField(ValueTypes.bool(), feature.defaultEnabled));
            keys.put(feature, key);
            builder.field(key, settings -> settings.enabled(feature));
        }
        return builder.build();
    }

    public static PropertiesStore<FeatureSettings> startup(Path configRoot, FeatureSettings active,
            Map<String, String> mods, boolean client) {
        var versions = Map.copyOf(mods);
        var definition = new DocumentDefinition<>(DocumentId.parse(SettingsCatalog.file(FeatureSettings.FILE).id()),
                1, FEATURES, () -> active, proposed -> {
                    try {
                        FeatureSelection.select(proposed, versions, client);
                        return ValidationReport.VALID;
                    } catch (IllegalStateException incompatible) {
                        return new ValidationReport(List.of(Issue.error("compat.startup_admission", FieldPath.ROOT,
                                incompatible.getMessage())), true);
                    }
                });
        return new PropertiesStore<>(definition, ownedFile(configRoot, FeatureSettings.FILE), JSON, DraftLimits.CONFIGURATION);
    }

    public static Candidate<FeatureSettings> selectedStartup(DocumentDefinition<FeatureSettings> definition,
            FeatureSettings active) {
        var authored = new LinkedHashMap<String, DraftNode>();
        for (var feature : Feature.values()) authored.put(feature.key, new DraftNode.Defaulted(
                active.values().containsKey(feature), new DraftNode.Scalar(Boolean.toString(active.enabled(feature)))));
        var draft = new DraftTree(new DraftNode.ObjectValue(authored), DraftLimits.CONFIGURATION);
        return draft.inspect(definition, JSON).candidate().orElseThrow();
    }

    private static <T extends Number> ValueType<T> display(ValueType<T> value, SettingConstraints bounds) {
        if (bounds.unit().isBlank()) return value;
        var unit = new NumericDisplay.Unit(bounds.unit(), bounds.unit().replace('_', ' '), BigDecimal.ONE);
        return ValueTypes.numericDisplay(value, new NumericDisplay(unit.id(), List.of(unit), bounds.step(),
                Optional.empty(), DecimalText.Limits.CONFIGURATION));
    }

    public static <T> JsonStore<T> json(Path configRoot, String filename, DocumentDefinition<T> definition) {
        return new JsonStore<>(definition, ownedFile(configRoot, filename), JsonStore.Format.EXISTING_BARE,
                JSON, DraftLimits.CONFIGURATION);
    }

    private static OwnedFile ownedFile(Path configRoot, String filename) {
        var storage = StorageBinding.relative(ConfigLocations.directory(configRoot.toAbsolutePath(), filename), filename);
        return new OwnedFile(storage, ConfigFile.LIMIT);
    }
}
