package design.inherent.skyweavecompat.configuration;

import com.electronwill.nightconfig.core.CommentedConfig;
import com.electronwill.nightconfig.core.UnmodifiableConfig;
import com.electronwill.nightconfig.toml.TomlParser;
import design.inherent.skyweavecompat.placement.NeighborRule;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import net.neoforged.neoforge.common.ModConfigSpec;

/** Staged scalars and string lists; the registered spec owns validation and persistence. */
public final class RegisteredEdits {
    public record Field(ModConfigSpec.ConfigValue<?> binding) {
        public String key() { return String.join(".", binding.getPath()); }
        public ModConfigSpec.ValueSpec spec() { return binding.getSpec(); }
        public Object defaultValue() { return binding.getDefault(); }
        public List<String> choices() {
            if (defaultValue() instanceof Boolean) return List.of("false", "true");
            if (defaultValue() instanceof Enum<?> value) return java.util.Arrays.stream(value.getDeclaringClass().getEnumConstants()).map(Enum::name).toList();
            return List.of();
        }
        public SettingDefinition definition() {
            Object initial = defaultValue();
            Class<?> type = initial instanceof List<?> ? List.class : initial instanceof Enum<?> value ? value.getDeclaringClass() : initial.getClass();
            var range = spec().getRange();
            Number minimum = range != null && range.getMin() instanceof Number value ? value : null;
            Number maximum = range != null && range.getMax() instanceof Number value ? value : null;
            var constraints = SettingConstraints.nativeRange(minimum, maximum, initial instanceof Integer || initial instanceof Long,
                    String.join(".", binding.getPath().subList(0, binding.getPath().size() - 1)));
            if (spec() instanceof ModConfigSpec.ListValueSpec list && list.getSizeRange() != null)
                constraints = new SettingConstraints(constraints.minimum(), constraints.maximum(), false, constraints.step(), "", constraints.group(),
                        list.getSizeRange().getMax(), -1, false, "");
            return new SettingDefinition(key(), type, initial, constraints);
        }
    }
    private final String filename;
    private final ConfigFile file;
    private final ModConfigSpec spec;
    private final List<Field> fields = new ArrayList<>();
    private EditSession<String, String> session;
    private final Map<String, Object> saved = new LinkedHashMap<>();
    private String baseline, warning;

    public RegisteredEdits(Path directory, String filename, ModConfigSpec spec) throws IOException {
        if (SettingsCatalog.FILES.stream().noneMatch(entry -> entry.filename().equals(filename) && filename.endsWith(".toml")))
            throw new IOException("Uncatalogued registered configuration");
        this.filename = filename; this.spec = spec; file = new ConfigFile(directory, filename);
        collect(spec.getValues()); reload();
    }
    private void collect(UnmodifiableConfig values) throws IOException {
        for (Object value : values.valueMap().values()) {
            if (value instanceof ModConfigSpec.ConfigValue<?> binding) {
                Object initial = binding.getDefault();
                if (!(initial instanceof Integer || initial instanceof Long || initial instanceof Double || initial instanceof Float
                        || initial instanceof Boolean || initial instanceof Enum<?> || initial instanceof String
                        || initial instanceof List<?> list && list.stream().allMatch(String.class::isInstance)))
                    throw new IOException("Unsupported registered value: " + String.join(".", binding.getPath()));
                fields.add(new Field(binding));
            } else if (value instanceof UnmodifiableConfig nested) collect(nested);
        }
    }
    public String filename() { return filename; }
    public List<Field> fields() { return List.copyOf(fields); }
    private Field field(String key) { return fields.stream().filter(field -> field.key().equals(key)).findFirst().orElseThrow(); }
    private static String display(Object value) {
        return value instanceof Enum<?> choice ? choice.name()
                : value instanceof List<?> ? new com.google.gson.Gson().toJson(value) : String.valueOf(value);
    }
    public String value(String key) { field(key); return session.value(key); }
    public String defaultValue(String key) { return display(field(key).defaultValue()); }
    public void set(String key, String value) { field(key); session.set(key, value); }
    public void reset(String key) { set(key, defaultValue(key)); }
    public boolean dirty() { return session.dirty(); }
    public java.util.Set<String> changedKeys() { return session.changes().keySet(); }
    public void discard() { session.discard(); }
    public String warning() { return warning; }
    public SettingsBinding binding(String key) {
        return new SettingsBinding(SettingsCatalog.field(filename, key), field(key).definition(), () -> value(key), value -> set(key, value), this::issues,
                () -> session.edited(key), () -> localIssue(key), () -> elements(key), text -> elementIssue(key, text));
    }
    @SuppressWarnings("unchecked")
    private List<String> elements(String key) {
        if (!(field(key).defaultValue() instanceof List<?>)) throw new IllegalArgumentException("Not a text collection");
        return (List<String>) convert(field(key), value(key));
    }
    private SettingIssue elementIssue(String key, String text) {
        var field = field(key);
        if (!(field.defaultValue() instanceof List<?>)) throw new IllegalArgumentException("Not a text collection");
        // An aggregate-only native predicate cannot truthfully attribute failure to one row.
        if (!(field.spec() instanceof ModConfigSpec.ListValueSpec list)) return null;
        return list.testElement(text) ? null : new SettingIssue("native_value", key, List.of(), "Entry does not satisfy its registered specification");
    }
    private SettingIssue localIssue(String key) {
        var field = field(key);
        try {
            var value = convert(field, value(key));
            boolean valid = field.spec().test(value);
            if (value instanceof List<?> values && field.spec() instanceof ModConfigSpec.ListValueSpec list)
                valid &= (list.getSizeRange() == null || list.getSizeRange().test(values.size())) && values.stream().allMatch(list::testElement);
            if (!valid) throw new IllegalArgumentException("Value does not satisfy its registered specification");
            return null;
        } catch (RuntimeException invalid) { return new SettingIssue("native_value", key, List.of(), String.valueOf(invalid.getMessage())); }
    }
    public List<SettingIssue> issues() {
        var issues = new ArrayList<SettingIssue>();
        session.changes().forEach((key, text) -> {
            var issue = localIssue(key); if (issue != null) issues.add(issue);
        });
        if (issues.isEmpty()) try { candidate(); }
        catch (Exception invalid) { issues.add(new SettingIssue("document", "", List.of(), String.valueOf(invalid.getMessage()))); }
        return List.copyOf(issues);
    }

    public void reload() throws IOException {
        String text = file.read(); CommentedConfig parsed = parse(text);
        var values = values(parsed);
        if (session == null) session = new EditSession<>(displayed(values)); else session.reload(displayed(values));
        baseline = text; saved.clear(); saved.putAll(values); warning = null;
    }
    private Map<String, Object> values(CommentedConfig parsed) {
        var values = new LinkedHashMap<String, Object>();
        for (var field : fields) values.put(field.key(), raw(field.binding(), parsed));
        return values;
    }
    private static <T> Object raw(ModConfigSpec.ConfigValue<T> binding, CommentedConfig parsed) {
        return binding.getRaw(parsed, binding.getPath(), binding::getDefault);
    }
    private CommentedConfig parse(String text) throws IOException {
        final CommentedConfig parsed;
        try { parsed = new TomlParser().parse(text); }
        catch (RuntimeException invalid) { throw new IOException("Invalid registered TOML", invalid); }
        validate(parsed); return parsed;
    }
    private void validate(CommentedConfig candidate) throws IOException {
        if (!spec.isCorrect(candidate)) throw new IOException("A value does not satisfy its registered specification");
        if (filename.equals("skyweave_compat-common.toml")) try {
            new NeighborRule.Settings(true, ((Number) candidate.get("minimumGap")).intValue(),
                    ((Number) candidate.get("maximumGap")).intValue(), ((Number) candidate.get("minimumIslandRadius")).intValue());
        } catch (IllegalArgumentException invalid) { throw new IOException(invalid.getMessage(), invalid); }
    }
    @SuppressWarnings({"unchecked", "rawtypes"})
    private Object convert(Field field, String input) {
        Object initial = field.defaultValue();
        try {
            if (initial instanceof Enum<?> choice) return Enum.valueOf((Class) choice.getDeclaringClass(), input);
            if (initial instanceof Boolean) {
                if (!input.equals("true") && !input.equals("false")) throw new IllegalArgumentException("Expected true or false");
                return Boolean.parseBoolean(input);
            }
            if (initial instanceof Integer) return new BigDecimal(input.strip()).intValueExact();
            if (initial instanceof Long) return new BigDecimal(input.strip()).longValueExact();
            if (initial instanceof Double) { double value = Double.parseDouble(input.strip()); if (!Double.isFinite(value)) throw new IllegalArgumentException("Expected a finite number"); return value; }
            if (initial instanceof Float) { float value = Float.parseFloat(input.strip()); if (!Float.isFinite(value)) throw new IllegalArgumentException("Expected a finite number"); return value; }
            if (initial instanceof String) return input;
            if (initial instanceof List<?>) {
                var parsed = new TomlParser().parse("values = " + input);
                Object value = parsed.get("values");
                if (parsed.valueMap().size() != 1 || !(value instanceof List<?> list)
                        || !list.stream().allMatch(String.class::isInstance))
                    throw new IllegalArgumentException("Expected a string list");
                return List.copyOf(list);
            }
            throw new IllegalArgumentException("Unsupported registered number");
        } catch (RuntimeException invalid) { throw new IllegalArgumentException(field.key() + ": invalid value", invalid); }
    }
    public Map<String, Object> candidate() throws IOException { return candidate(session.changes()); }
    private Map<String, Object> candidate(Map<String, String> changes) throws IOException {
        var parsed = parse(baseline);
        changes.forEach((key, value) -> parsed.set(field(key).binding().getPath(), convert(field(key), value)));
        validate(parsed); return values(parsed);
    }
    private void checkNative() throws IOException {
        if(!nativeMatchesFile())throw new IOException("File and registered values differ. Let the owning mod reload, then reload this editor to review them.");
    }
    public boolean nativeMatchesFile() {
        return fields.stream().allMatch(field -> Objects.equals(field.binding().getRaw(),saved.get(field.key())));
    }
    public void check() throws IOException { candidate(); file.check(baseline); checkNative(); }
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void apply(Field field, Object value) { ((ModConfigSpec.ConfigValue) field.binding()).set(value); }
    public SettingsCommit.Write prepare() throws IOException {
        var submitted = session.submit();
        String original = baseline;
        var expected = snapshot(candidate(submitted.changes()));
        var previous = snapshot(saved);
        return new SettingsCommit.Write(filename, () -> {
            session.checkCurrent(submitted); file.check(original); checkNative(previous);
        }, () -> savePrepared(submitted, original, expected, previous));
    }
    public void save() throws Exception {
        if (!dirty()) return;
        prepare().save();
    }
    private static Map<String, Object> snapshot(Map<String, Object> values) {
        var copy = new LinkedHashMap<String, Object>();
        values.forEach((key, value) -> copy.put(key, value instanceof List<?> list ? List.copyOf(list) : value));
        return java.util.Collections.unmodifiableMap(copy);
    }
    private void checkNative(Map<String, Object> expected) throws IOException {
        if (!fields.stream().allMatch(field -> Objects.equals(field.binding().getRaw(), expected.get(field.key()))))
            throw new IOException("File and registered values differ. Let the owning mod reload, then reload this editor to review them.");
    }
    private void savePrepared(EditSession.Submission<String, String> submitted, String original,
            Map<String, Object> expected, Map<String, Object> previous) throws Exception {
        boolean[] applied = {false};
        String actual;
        try {
            actual = file.throughOwner(original, () -> {
                checkNative(previous); applied[0] = true;
                for (var field : fields) if (submitted.changes().containsKey(field.key())) apply(field, expected.get(field.key()));
                spec.save();
            });
            if (!values(parse(actual)).equals(expected)) throw new IOException("Registered save readback differs. Reload to review it.");
        } catch (Exception failure) {
            if (!applied[0]) throw failure;
            // Native save may have written successfully before a reload callback failed.
            String current;
            try { current = file.read(); } catch (IOException unreadable) { failure.addSuppressed(unreadable); throw failure; }
            if (values(parse(current)).equals(expected)) {
                accept(current, submitted); warning = "Saved, but the registered reload failed: " + failure.getMessage(); return;
            }
            if (current.equals(original)) for (var field : fields)
                if (submitted.changes().containsKey(field.key()) && Objects.equals(field.binding().getRaw(), expected.get(field.key()))) apply(field, previous.get(field.key()));
            throw failure;
        }
        accept(actual, submitted); warning = null;
    }
    private void accept(String text, EditSession.Submission<String, String> submitted) throws IOException {
        var parsed = values(parse(text));
        session.acknowledge(submitted, displayed(parsed));
        baseline = text; saved.clear(); saved.putAll(parsed);
    }
    private static Map<String, String> displayed(Map<String, Object> values) {
        var result = new LinkedHashMap<String, String>();
        values.forEach((key, value) -> result.put(key, display(value)));
        return result;
    }
}
