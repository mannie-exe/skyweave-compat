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
    }
    private final String filename;
    private final ConfigFile file;
    private final ModConfigSpec spec;
    private final List<Field> fields = new ArrayList<>();
    private final Map<String, String> edits = new LinkedHashMap<>();
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
    public String value(String key) { field(key); return edits.getOrDefault(key, display(saved.get(key))); }
    public String defaultValue(String key) { return display(field(key).defaultValue()); }
    public void set(String key, String value) { field(key); if (value.equals(display(saved.get(key)))) edits.remove(key); else edits.put(key, value); }
    public void reset(String key) { set(key, defaultValue(key)); }
    public boolean dirty() { return !edits.isEmpty(); }
    public java.util.Set<String> changedKeys() { return java.util.Set.copyOf(edits.keySet()); }
    public void discard() { edits.clear(); }
    public String warning() { return warning; }

    public void reload() throws IOException {
        String text = file.read(); CommentedConfig parsed = parse(text);
        var values = values(parsed);
        baseline = text; saved.clear(); saved.putAll(values); edits.clear(); warning = null;
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
    public Map<String, Object> candidate() throws IOException {
        var parsed = parse(baseline);
        edits.forEach((key, value) -> parsed.set(field(key).binding().getPath(), convert(field(key), value)));
        validate(parsed); return values(parsed);
    }
    private void checkNative() throws IOException {
        for (var field : fields) if (!Objects.equals(field.binding().getRaw(), saved.get(field.key())))
            throw new IOException("Registered values changed outside this editor. Reload to review them.");
    }
    public void check() throws IOException { candidate(); file.check(baseline); checkNative(); }
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void apply(Field field, Object value) { ((ModConfigSpec.ConfigValue) field.binding()).set(value); }
    public void save() throws Exception {
        if (!dirty()) return;
        var expected = candidate(); var previous = new LinkedHashMap<>(saved); boolean[] applied = {false};
        String actual;
        try {
            actual = file.throughOwner(baseline, () -> {
                checkNative(); applied[0] = true;
                for (var field : fields) if (edits.containsKey(field.key())) apply(field, expected.get(field.key()));
                spec.save();
            });
            if (!values(parse(actual)).equals(expected)) throw new IOException("Registered save readback differs. Reload to review it.");
        } catch (Exception failure) {
            if (!applied[0]) throw failure;
            // Native save may have written successfully before a reload callback failed.
            String current;
            try { current = file.read(); } catch (IOException unreadable) { failure.addSuppressed(unreadable); throw failure; }
            if (values(parse(current)).equals(expected)) {
                accept(current); warning = "Saved, but the registered reload failed: " + failure.getMessage(); return;
            }
            if (current.equals(baseline)) for (var field : fields)
                if (edits.containsKey(field.key()) && Objects.equals(field.binding().getRaw(), expected.get(field.key()))) apply(field, previous.get(field.key()));
            throw failure;
        }
        accept(actual); warning = null;
    }
    private void accept(String text) throws IOException {
        var parsed = parse(text); baseline = text; saved.clear(); saved.putAll(values(parsed)); edits.clear();
    }
}
