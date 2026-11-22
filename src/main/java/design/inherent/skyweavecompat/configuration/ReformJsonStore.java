package design.inherent.skyweavecompat.configuration;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import design.inherent.reform.definition.DocumentDefinition;
import design.inherent.reform.definition.DocumentId;
import design.inherent.reform.definition.FieldKey;
import design.inherent.reform.definition.FieldPath;
import design.inherent.reform.definition.RecordType;
import design.inherent.reform.edit.Candidate;
import design.inherent.reform.edit.DraftLimits;
import design.inherent.reform.edit.DraftTree;
import design.inherent.reform.serialization.JsonInput;
import design.inherent.reform.serialization.JsonInputException;
import design.inherent.reform.serialization.JsonLimits;
import design.inherent.reform.storage.OwnedFile;
import design.inherent.reform.storage.StorageBinding;
import design.inherent.reform.storage.TextStore;
import design.inherent.reform.validation.Issue;
import design.inherent.reform.validation.ValidationReport;
import design.inherent.reform.value.ValueType;
import design.inherent.reform.value.ValueTypes;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Shared storage retains existing source syntax, optional-codec admission and complete domain rules. */
public final class ReformJsonStore<T> extends TextStore<T> {
    private static final Gson JSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final JsonLimits LIMITS = new JsonLimits(ConfigFile.LIMIT, 16, ConfigFile.LIMIT, ConfigFile.LIMIT, 128, 4096);
    private final JsonSettings.Definition source;

    public ReformJsonStore(Path configRoot, JsonSettings.Definition source, DocumentDefinition<T> definition) {
        super(definition, new OwnedFile(StorageBinding.relative(
                        ConfigLocations.directory(configRoot.toAbsolutePath(), source.filename()), source.filename()), ConfigFile.LIMIT),
                LIMITS, DraftLimits.CONFIGURATION, "json", "Compat owned JSON");
        this.source = source;
        if (!definition.id().equals(DocumentId.parse(SettingsCatalog.file(source.filename()).id())))
            throw new IllegalArgumentException("The typed definition belongs to another configuration source");
    }

    public static ReformJsonStore<Map<String, Object>> map(Path configRoot, JsonSettings.Definition source) {
        return new ReformJsonStore<>(configRoot, source, definition(source));
    }

    private static DocumentDefinition<Map<String, Object>> definition(JsonSettings.Definition source) {
        var defaults = ProfileJson.object(source.defaults());
        var type = object(defaults, "", source);
        var value = type.codec().parse(JsonOps.INSTANCE, defaults).getOrThrow();
        return new DocumentDefinition<>(DocumentId.parse(SettingsCatalog.file(source.filename()).id()), 1, type, () -> value,
                candidate -> validate(source, JSON.toJson(candidate)));
    }

    private static ValueType<Map<String, Object>> object(JsonObject defaults, String prefix, JsonSettings.Definition source) {
        var keys = new ArrayList<FieldKey<?>>();
        var builder = RecordType.<Map<String, Object>>builder(values -> {
            var result = new LinkedHashMap<String, Object>();
            keys.forEach(key -> result.put(key.id(), values.get(key)));
            return java.util.Collections.unmodifiableMap(result);
        });
        for (var entry : defaults.entrySet()) {
            String name = entry.getKey();
            String path = prefix + name;
            ValueType<?> field;
            if (entry.getValue().isJsonObject()) field = object(entry.getValue().getAsJsonObject(), path + ".", source);
            else {
                var declared = source.field(path);
                if (declared == null) throw new IllegalStateException("Missing declared JSON field " + path);
                field = fieldType(source.filename(), declared);
            }
            add(builder, keys, FieldKey.of(name, field));
        }
        return builder.build();
    }

    private static <T> void add(RecordType.Builder<Map<String, Object>> builder, List<FieldKey<?>> keys, FieldKey<T> key) {
        keys.add(key);
        builder.field(key, value -> key.type().codec().parse(JsonOps.INSTANCE, JSON.toJsonTree(value.get(key.id()))).getOrThrow());
    }

    private static ValueType<?> fieldType(String filename, SettingDefinition field) {
        return switch (field.kind()) {
            case BOOLEAN -> ValueTypes.bool();
            case INTEGER, LONG, DECIMAL -> ReformDocuments.scalarType(field.valueType(), field.constraints(), filename, field.key());
            case STRING -> SettingsReferences.text(filename, field.key(), field.constraints().maximumLength() < 0 ? ConfigFile.LIMIT : field.constraints().maximumLength());
            case CHOICE -> enumType(field.valueType());
            case STRINGS -> {
                var text = SettingsReferences.text(filename, field.key(), field.constraints().maximumLength() < 0 ? ConfigFile.LIMIT : field.constraints().maximumLength());
                int count = field.maximumEntries() < 0 ? ConfigFile.LIMIT : field.maximumEntries();
                yield field.valueType() == Set.class ? ValueTypes.set(text, count) : ValueTypes.list(text, count);
            }
        };
    }

    @SuppressWarnings({"unchecked", "rawtypes"}) // The existing declared choice type is an enum.
    private static ValueType<?> enumType(Class<?> type) { return ValueTypes.enumeration((Class) type); }

    private static ValidationReport validate(JsonSettings.Definition source, String text) {
        try {
            source.validate().accept(text);
            return ValidationReport.VALID;
        } catch (RuntimeException invalid) {
            return ReformDocuments.invalid(invalid);
        }
    }

    @Override protected DraftTree decode(String text) {
        var validation = validate(source, text);
        if (!validation.valid()) throw new InvalidData(validation);
        try {
            return DraftTree.fromParsed(definition.type(), JsonInput.read(source.normalize().apply(text), LIMITS), DraftLimits.CONFIGURATION);
        } catch (JsonInputException invalid) {
            throw new InvalidData(new ValidationReport(List.of(Issue.error(invalid.code(), FieldPath.ROOT, invalid.getMessage())), true));
        }
    }

    @Override protected String encode(Read<T> baseline, Candidate<T> candidate) {
        String text = JSON.toJson(com.google.gson.JsonParser.parseString(candidate.authoredJson())) + "\n";
        return baseline.source().text().orElse("").contains("\r\n") ? text.replace("\n", "\r\n") : text;
    }

    @Override protected void validateEncoded(String text) throws IOException {
        JsonInput.read(text, LIMITS);
        var report = validate(source, text);
        if (!report.valid()) throw new IOException(report.issues().getFirst().fallback());
    }
}
