package design.inherent.skyweavecompat.configuration;

import com.google.gson.*;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import java.io.IOException;
import java.io.StringReader;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** A typed file draft. Invalid input stays in the draft and cannot reach disk. */
public final class JsonEdits {
    public enum Kind { NUMBER, BOOLEAN, STRING, STRINGS }
    private static final Gson JSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private final ConfigFile file;
    private final JsonSettings.Definition definition;
    private final List<String> keys;
    private String baseline;
    private JsonObject saved;
    private EditSession<String, String> session;

    public JsonEdits(Path directory, JsonSettings.Definition definition) throws IOException {
        if (!JsonSettings.FILES.contains(definition)) throw new IllegalArgumentException("Unknown configuration");
        this.definition = definition;
        keys = leafKeys(JsonParser.parseString(definition.defaults()).getAsJsonObject());
        if (!definition.fields().isEmpty() && !keys.equals(definition.fields().stream().map(SettingDefinition::key).toList()))
            throw new IllegalArgumentException("Owned fields do not match the document schema: " + definition.filename());
        file = new ConfigFile(directory, definition.filename());
        reload();
    }
    public JsonSettings.Definition definition() { return definition; }
    public List<String> keys() { return keys; }
    public SettingDefinition field(String key) { kind(key); return definition.field(key); }
    public SettingsBinding binding(String key) {
        var field = field(key);
        if (field == null) throw new IllegalArgumentException("The native codec has no field definition: " + key);
        return new SettingsBinding(SettingsCatalog.field(definition.filename(), key), field, () -> value(key), value -> set(key, value), this::issues, () -> edited(key), () -> localIssue(key), () -> strings(key), field::elementIssue);
    }
    private SettingIssue localIssue(String key) {
        var field = definition.field(key);
        return field == null ? null : field.inspect(value(key)).issue();
    }
    public Kind kind(String key) {
        JsonElement value = at(saved, key);
        if (value == null) throw new IllegalArgumentException("Unknown setting: " + key);
        if (value.isJsonArray()) return Kind.STRINGS;
        if (!value.isJsonPrimitive()) throw new IllegalArgumentException("Unsupported setting: " + key);
        var primitive = value.getAsJsonPrimitive();
        return primitive.isBoolean() ? Kind.BOOLEAN : primitive.isString() ? Kind.STRING : Kind.NUMBER;
    }
    private String display(JsonElement value) { return value.isJsonArray() ? value.toString() : value.getAsString(); }
    public String value(String key) { kind(key); return session.value(key); }
    public String savedValue(String key) { return session.saved(key); }
    public String defaultValue(String key) { return display(at(JsonParser.parseString(definition.defaults()).getAsJsonObject(), key)); }
    public void set(String key, String value) {
        kind(key);
        session.set(key, value);
    }
    public void reset(String key) { set(key, defaultValue(key)); }
    public List<String> strings(String key) {
        if (kind(key) != Kind.STRINGS) throw new IllegalArgumentException("Not a string list");
        return textArray(value(key));
    }
    static List<String> textArray(String raw) {
        try {
            var wrapper = object("{\"value\":" + raw + "}");
            var array = wrapper.getAsJsonArray("value");
            if (wrapper.size() != 1 || array == null || array.asList().stream().anyMatch(item ->
                    !item.isJsonPrimitive() || !item.getAsJsonPrimitive().isString()))
                throw new IllegalArgumentException("Expected text entries");
            return array.asList().stream().map(JsonElement::getAsString).toList();
        } catch (IOException invalid) { throw new IllegalArgumentException(invalid.getMessage(), invalid); }
    }
    public void strings(String key, List<String> values) { set(key, new Gson().toJson(values)); }
    public boolean dirty() { return session.dirty(); }
    public boolean edited(String key) { return session.edited(key); }
    public java.util.Set<String> changedKeys() { return session.changes().keySet(); }
    public void discard() { session.discard(); }
    public void reload() throws IOException {
        String current = file.read();
        object(current); // Preserve the file reader's checked syntax errors and the previous draft on failure.
        definition.validate().accept(current);
        JsonObject parsed = object(definition.normalize().apply(current));
        var values = displayed(parsed);
        if (session == null) session = new EditSession<>(values); else session.reload(values);
        baseline = current; saved = parsed;
    }
    public String candidate() { return candidate(session.changes()); }
    private String candidate(Map<String, String> changes) {
        JsonObject result = saved.deepCopy();
        changes.forEach((key, raw) -> {
            try {
                var parent = parent(result, key);
                String leaf = key.substring(key.lastIndexOf('.') + 1);
                var field = definition.field(key);
                if (field != null) {
                    parent.add(leaf, JSON.toJsonTree(field.parse(raw)));
                    return;
                }
                switch (kind(key)) {
                    case NUMBER -> parent.add(leaf, new JsonPrimitive(new BigDecimal(raw.strip())));
                    case STRING -> parent.addProperty(leaf, raw);
                    case BOOLEAN -> {
                        if (!raw.equals("true") && !raw.equals("false")) throw new IllegalArgumentException("Expected true or false");
                        parent.addProperty(leaf, Boolean.parseBoolean(raw));
                    }
                    case STRINGS -> {
                        var array = JsonParser.parseString(raw).getAsJsonArray();
                        for (var item : array) if (!item.isJsonPrimitive() || !item.getAsJsonPrimitive().isString())
                            throw new IllegalArgumentException("Expected text entries");
                        if (array.size() != array.asList().stream().map(JsonElement::getAsString).distinct().count())
                            throw new IllegalArgumentException("Text entries must be unique");
                        parent.add(leaf, array);
                    }
                }
            } catch (SettingIssue.InvalidValue invalid) { throw invalid; }
            catch (RuntimeException invalid) { throw new IllegalArgumentException(key + ": " + invalid.getMessage(), invalid); }
        });
        String text = JSON.toJson(result) + "\n";
        definition.validate().accept(text);
        return baseline.contains("\r\n") ? text.replace("\n", "\r\n") : text;
    }
    public List<SettingIssue> issues() {
        var issues = new ArrayList<SettingIssue>();
        for (var entry : session.changes().entrySet()) {
            var field = definition.field(entry.getKey());
            if (field != null) {
                var input = field.inspect(entry.getValue());
                if (input.issue() != null) issues.add(input.issue());
            }
        }
        if (!issues.isEmpty()) return List.copyOf(issues);
        try { candidate(); }
        catch (RuntimeException invalid) {
            var issue = SettingIssue.find(invalid);
            if (issue != null) issues.add(issue);
            else issues.add(new SettingIssue("document", "", List.of(), String.valueOf(invalid.getMessage())));
        }
        return List.copyOf(issues);
    }
    public void check() throws IOException { candidate(); file.check(baseline); }
    /** Capture all submitted text before another document or callback can change the draft. */
    public SettingsCommit.Write prepare() throws IOException { return prepare(Runnable::run); }
    /** File work may run on an authority thread; all mutable editor acknowledgement stays on its owning thread. */
    public SettingsCommit.Write prepare(java.util.concurrent.Executor acknowledgements) throws IOException {
        var submitted = session.submit();
        String original = baseline, next = candidate(submitted.changes());
        ConfigFile.validateCandidate(next);
        var observed = new java.util.concurrent.atomic.AtomicReference<String>();
        return new SettingsCommit.Write(definition.filename(), new SettingsCommit.Text(original, next), () -> {
            session.checkCurrent(submitted); file.check(original);
        }, () -> {
            String actual = file.write(original, next);
            definition.validate().accept(actual);
            var parsed = object(actual);
            observed.set(actual);
            acknowledgements.execute(() -> {
                try { session.checkCurrent(submitted); }
                catch (IllegalStateException detached) { return; }
                session.acknowledge(submitted, displayed(parsed));
                saved = parsed; baseline = actual;
            });
        }, () -> new SettingsCommit.Observation(SettingsCommit.Persistence.WRITTEN, observed.get()),
                () -> SettingsCommit.observe(file, original, next::equals));
    }
    public void save() throws IOException {
        if (!dirty()) return;
        ProfileEditPlan.prepare(file.path().getParent(), List.of(prepare())).save().requireSuccess();
    }

    private Map<String, String> displayed(JsonObject object) {
        var values = new LinkedHashMap<String, String>();
        for (String key : keys) values.put(key, display(at(object, key)));
        return values;
    }

    private static List<String> leafKeys(JsonObject object) {
        var keys = new ArrayList<String>();
        leaves(object, "", keys);
        return List.copyOf(keys);
    }
    private static void leaves(JsonObject object, String prefix, List<String> keys) {
        for (var entry : object.entrySet()) {
            if (entry.getKey().contains(".")) throw new IllegalArgumentException("Dots are reserved for setting paths");
            String key = prefix + entry.getKey();
            if (entry.getValue().isJsonObject()) leaves(entry.getValue().getAsJsonObject(), key + ".", keys);
            else keys.add(key);
        }
    }
    private static JsonObject parent(JsonObject object, String key) {
        String[] parts = key.split("\\.");
        for (int i = 0; i < parts.length - 1; i++) {
            var next = object.get(parts[i]);
            if (next == null || !next.isJsonObject()) throw new IllegalArgumentException("Unknown setting: " + key);
            object = next.getAsJsonObject();
        }
        return object;
    }
    private static JsonElement at(JsonObject object, String key) {
        return parent(object, key).get(key.substring(key.lastIndexOf('.') + 1));
    }

    // Reject duplicate keys rather than letting Gson silently discard one value.
    public static JsonObject object(String text) throws IOException {
        try (var reader = new JsonReader(new StringReader(text))) {
            var value = read(reader, 0);
            if (!value.isJsonObject() || reader.peek() != JsonToken.END_DOCUMENT)
                throw new IOException("Expected one JSON object");
            return value.getAsJsonObject();
        }
    }
    private static JsonElement read(JsonReader reader, int depth) throws IOException {
        if (depth > 16) throw new IOException("JSON nesting exceeds configuration limit");
        return switch (reader.peek()) {
            case BEGIN_OBJECT -> {
                var object = new JsonObject(); reader.beginObject();
                while (reader.hasNext()) {
                    String name = reader.nextName();
                    if (object.has(name)) throw new IOException("Duplicate JSON key: " + name);
                    object.add(name, read(reader, depth + 1));
                }
                reader.endObject(); yield object;
            }
            case BEGIN_ARRAY -> {
                var array = new JsonArray(); reader.beginArray();
                while (reader.hasNext()) array.add(read(reader, depth + 1));
                reader.endArray(); yield array;
            }
            case STRING -> new JsonPrimitive(reader.nextString());
            case NUMBER -> new JsonPrimitive(new BigDecimal(reader.nextString()));
            case BOOLEAN -> new JsonPrimitive(reader.nextBoolean());
            case NULL -> { reader.nextNull(); yield JsonNull.INSTANCE; }
            default -> throw new IOException("Unexpected JSON token");
        };
    }
}
