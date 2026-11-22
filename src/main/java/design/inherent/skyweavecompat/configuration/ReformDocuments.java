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
import design.inherent.reform.storage.OwnedFile;
import design.inherent.reform.storage.PropertiesStore;
import design.inherent.reform.storage.StorageBinding;
import design.inherent.reform.validation.Issue;
import design.inherent.reform.validation.DecodedValue;
import design.inherent.reform.validation.ValidationReport;
import design.inherent.reform.value.ValueType;
import design.inherent.reform.value.ValueTypes;
import design.inherent.reform.value.NumericDomain;
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
                domainIssues(filename, recordType(type, filename, "")), () -> defaults);
    }

    /** Route expected domain-constructor failures through the original complete JSON parser.
     * The shared typed decoder still owns scalar/structural validation and successful values. */
    private static <T> ValueType<T> domainIssues(String filename, ValueType<T> typed) {
        return new ValueType<>() {
            public com.mojang.serialization.Codec<T> codec() { return typed.codec(); }
            public design.inherent.reform.value.ValueShape shape() { return typed.shape(); }
            public design.inherent.reform.value.SnapshotPolicy<T> snapshotPolicy() { return typed.snapshotPolicy(); }
            @Override public <O> DecodedValue<T> decode(com.mojang.serialization.DynamicOps<O> ops, O input, FieldPath path) {
                var result = typed.decode(ops, input, path);
                if (result.report().valid() || result.report().issues().stream().noneMatch(issue -> issue.code().equals("record.constructor"))) return result;
                var source = JsonSettings.find(filename);
                if (source == null) return result;
                try { source.validate().accept(ops.convertTo(com.mojang.serialization.JsonOps.INSTANCE, input).toString()); }
                catch (RuntimeException failure) {
                    var issue = SettingIssue.find(failure);
                    if (issue != null && source.field(issue.field()) != null
                            && issue.related().stream().allMatch(field -> source.field(field) != null))
                        return DecodedValue.failure(invalid(failure));
                }
                return result;
            }
        };
    }

    /** The existing record constructor remains the authority for cross-field rules. */
    private static <T extends Record> ValueType<T> recordType(Class<T> type, String filename, String prefix) {
        var builder = RecordTypes.record(type);
        for (var component : type.getRecordComponents()) {
            var javaType = component.getType();
            var spec = component.getAnnotation(SettingSpec.class);
            if (javaType.isRecord()) {
                builder.field(component.getName(), FieldKey.of(component.getName(), recordType(javaType.asSubclass(Record.class), filename, prefix + component.getName() + ".")));
            } else if (javaType == boolean.class || javaType == Boolean.class) {
                builder.field(component.getName(), FieldKey.of(component.getName(), ValueTypes.bool()));
            } else if (spec != null) {
                var bounds = SettingConstraints.from(spec);
                ValueType<?> scalar;
                String key = prefix + component.getName();
                if (javaType == List.class && bounds.maximumEntries() >= 0)
                    scalar = ValueTypes.list(SettingsReferences.text(filename, key, bounds.maximumLength() < 0 ? 4096 : bounds.maximumLength()), bounds.maximumEntries());
                else if (javaType == String.class && bounds.maximumLength() >= 0)
                    scalar = SettingsReferences.text(filename, key, bounds.maximumLength());
                else scalar = scalarType(javaType, bounds, filename, key);
                if (scalar != null)
                    builder.field(component.getName(), FieldKey.of(component.getName(), scalar));
            }
        }
        return builder.build();
    }

    static ValueType<?> scalarType(Class<?> type, SettingConstraints bounds) {
        return scalarType(type, bounds, "", "");
    }

    static ValueType<?> scalarType(Class<?> type, SettingConstraints bounds, String filename, String field) {
        if (type == int.class || type == Integer.class)
            return display(ValueTypes.integer(domain(bounds, BigDecimal.valueOf(Integer.MIN_VALUE), BigDecimal.valueOf(Integer.MAX_VALUE))), bounds, filename, field);
        if (type == long.class || type == Long.class)
            return display(ValueTypes.longInteger(domain(bounds, BigDecimal.valueOf(Long.MIN_VALUE), BigDecimal.valueOf(Long.MAX_VALUE))), bounds, filename, field);
        if (type == double.class || type == Double.class)
            return display(ValueTypes.decimal(domain(bounds, BigDecimal.valueOf(-Double.MAX_VALUE), BigDecimal.valueOf(Double.MAX_VALUE))), bounds, filename, field);
        if (type == float.class || type == Float.class)
            return display(ValueTypes.floating(domain(bounds, new BigDecimal(Float.toString(-Float.MAX_VALUE)), new BigDecimal(Float.toString(Float.MAX_VALUE)))), bounds, filename, field);
        if (type == String.class && bounds.maximumLength() >= 0)
            return ValueTypes.text(bounds.maximumLength());
        if (type == List.class && bounds.maximumEntries() >= 0)
            return ValueTypes.list(ValueTypes.text(bounds.maximumLength() < 0 ? 4096 : bounds.maximumLength()),
                    bounds.maximumEntries());
        return null;
    }

    private static NumericDomain domain(SettingConstraints bounds, BigDecimal minimum, BigDecimal maximum) {
        return new NumericDomain(bounds.minimum() == null ? minimum : bounds.minimum(), !bounds.exclusiveMinimum(),
                bounds.maximum() == null ? maximum : bounds.maximum(), true);
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

    static <T extends Number> ValueType<T> display(ValueType<T> value, SettingConstraints bounds) {
        return display(value, bounds, "", "");
    }

    private static <T extends Number> ValueType<T> display(ValueType<T> value, SettingConstraints bounds, String filename, String field) {
        var declared = switch (bounds.unit()) {
            case "ticks" -> NumericDisplay.ticks(BigDecimal.valueOf(20));
            case "probability" -> NumericDisplay.probability();
            case "percent" -> NumericDisplay.percentStored();
            default -> {
                String name = bounds.unit().isBlank() ? "number" : bounds.unit();
                var unit = new NumericDisplay.Unit(name.toLowerCase(java.util.Locale.ROOT), name.replace('_', ' '), BigDecimal.ONE);
                yield new NumericDisplay(unit.id(), List.of(unit), bounds.step(), Optional.empty(), DecimalText.Limits.CONFIGURATION);
            }
        };
        var display = new NumericDisplay(declared.canonicalUnit(), declared.units(), bounds.step(),
                declared.referenceTicksPerSecond(), declared.limits());
        if (bounds.minimum() != null && bounds.maximum() != null && bounds.maximum().compareTo(bounds.minimum()) > 0
                && (bounds.unit().equals("probability") || bounds.unit().equals("percent"))) display = display.withSlider();
        var entry = SettingsCatalog.file(filename);
        var sentinel = entry == null ? null : entry.valueHint(field, 0);
        if (sentinel != null) display = display.withCaptions(List.of(new NumericDisplay.Caption(BigDecimal.ZERO,
                "skyweave_compat.settings." + sentinel, sentinel.equals("value_off") ? "Off" : "Native initial view")));
        return ValueTypes.numericDisplay(value, display);
    }

    public static FieldPath path(String field) {
        var result = FieldPath.ROOT;
        for (String part : field.split("\\.")) result = result.field(part);
        return result;
    }

    public static ValidationReport invalid(RuntimeException failure) {
        var domain = SettingIssue.find(failure);
        var issue = domain == null ? Issue.error("compat.validation", FieldPath.ROOT,
                failure.getMessage() == null ? "Complete configuration is invalid" : failure.getMessage())
                : new Issue(domain.code(), Issue.Severity.ERROR, path(domain.field()),
                        domain.related().stream().map(ReformDocuments::path).toList(), Map.of(), domain.message());
        return new ValidationReport(List.of(issue), true);
    }

    public static <T> ReformJsonStore<T> json(Path configRoot, String filename, DocumentDefinition<T> definition) {
        var source = JsonSettings.find(filename);
        if (source == null) throw new IllegalArgumentException("The existing JSON owner is not admitted: " + filename);
        return new ReformJsonStore<>(configRoot, source, definition);
    }

    private static OwnedFile ownedFile(Path configRoot, String filename) {
        var storage = StorageBinding.relative(ConfigLocations.directory(configRoot.toAbsolutePath(), filename), filename);
        return new OwnedFile(storage, ConfigFile.LIMIT);
    }
}
