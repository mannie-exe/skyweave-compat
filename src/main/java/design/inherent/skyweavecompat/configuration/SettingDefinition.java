package design.inherent.skyweavecompat.configuration;

import com.google.gson.Gson;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/** A scalar or text collection definition; contains neither widgets nor a file writer. */
public record SettingDefinition(String key, Class<?> valueType, Object defaultValue, SettingConstraints constraints,
        java.util.function.Consumer<Object> ownerValidator) {
    private static final Gson JSON = new Gson();
    private static final java.util.function.Consumer<Object> NO_OWNER_VALIDATION = value -> {};
    public enum Kind { BOOLEAN, INTEGER, LONG, DECIMAL, STRING, CHOICE, STRINGS }
    public enum InputState { EMPTY, INCOMPLETE, INVALID, VALID }
    public record Input(InputState state, Object value, SettingIssue issue) {}

    public SettingDefinition(String key, Class<?> valueType, Object defaultValue, SettingConstraints constraints) {
        this(key, valueType, defaultValue, constraints, NO_OWNER_VALIDATION);
    }

    public SettingDefinition(String key, Class<?> valueType, Object defaultValue, SettingSpec spec) {
        this(key, valueType, defaultValue, SettingConstraints.from(spec));
    }

    public SettingDefinition {
        java.util.Objects.requireNonNull(ownerValidator);
        if (defaultValue instanceof java.util.Collection<?> values && values.stream().anyMatch(java.util.Objects::isNull))
            throw new SettingIssue.InvalidValue(new SettingIssue("required", key, List.of(), "Missing text entry for " + key));
        if (defaultValue instanceof List<?> list) defaultValue = List.copyOf(list);
        if (defaultValue instanceof Set<?> set) defaultValue = Set.copyOf(set);
        if (!(valueType == boolean.class || valueType == Boolean.class || valueType == int.class || valueType == Integer.class
                || valueType == long.class || valueType == Long.class || valueType == double.class || valueType == Double.class
                || valueType == float.class || valueType == Float.class || valueType == String.class
                || valueType.isEnum() || valueType == List.class || valueType == Set.class))
            throw new IllegalArgumentException("Unsupported setting type: " + valueType);
    }
    public Kind kind() {
        if (valueType == boolean.class || valueType == Boolean.class) return Kind.BOOLEAN;
        if (valueType == int.class || valueType == Integer.class) return Kind.INTEGER;
        if (valueType == long.class || valueType == Long.class) return Kind.LONG;
        if (valueType == double.class || valueType == Double.class || valueType == float.class || valueType == Float.class) return Kind.DECIMAL;
        if (valueType.isEnum()) return Kind.CHOICE;
        if (valueType == List.class || valueType == Set.class) return Kind.STRINGS;
        return Kind.STRING;
    }
    public List<String> choices() {
        return kind() == Kind.CHOICE ? Arrays.stream(valueType.getEnumConstants()).map(value -> ((Enum<?>) value).name()).toList() : List.of();
    }
    public BigDecimal minimum() { return constraints.minimum(); }
    public BigDecimal maximum() { return constraints.maximum(); }
    public BigDecimal step() { return constraints.step(); }
    public String unit() { return constraints.unit(); }
    public String group() { return constraints.group(); }
    public int maximumEntries() { return constraints.maximumEntries(); }
    public String defaultText() { return text(defaultValue); }
    public String adjust(String raw, int direction) {
        if (kind() != Kind.INTEGER && kind() != Kind.LONG && kind() != Kind.DECIMAL)
            throw new IllegalArgumentException("Not a numeric setting");
        if (direction != -1 && direction != 1) throw new IllegalArgumentException("Expected a single increment");
        var current = new BigDecimal(parse(raw).toString());
        long scale = Math.max(0L, Math.max(current.scale(), step().scale()));
        long whole = Math.max(Math.max(0L, (long) current.precision() - current.scale()),
                Math.max(0L, (long) step().precision() - step().scale())) + 1;
        // A compact exponent must not expand into an unbounded integer allocation on a button press.
        if (scale + whole + 2 > ConfigFile.LIMIT) fail("precision", "Stepped number exceeds configuration size limits");
        var next = current.add(step().multiply(BigDecimal.valueOf(direction)));
        if (minimum() != null && next.compareTo(minimum()) < 0) next = minimum();
        if (maximum() != null && next.compareTo(maximum()) > 0) next = maximum();
        if (minimum() != null && constraints.exclusiveMinimum() && next.compareTo(minimum()) <= 0) return raw;
        String text = next.stripTrailingZeros().toPlainString(); parse(text); return text;
    }
    public static String text(Object value) {
        return value instanceof Enum<?> item ? item.name() : value instanceof java.util.Collection<?> ? JSON.toJson(value) : String.valueOf(value);
    }
    public Input inspect(String raw) {
        if (kind() == Kind.INTEGER || kind() == Kind.LONG || kind() == Kind.DECIMAL) {
            if (raw.isBlank()) return new Input(InputState.EMPTY, null, issue("required", "Enter a number"));
            if (raw.strip().matches("[+-]?\\.?|[+-]?(?:[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+)[eE][+-]?"))
                return new Input(InputState.INCOMPLETE, null, issue("incomplete", "Complete the number"));
        }
        try { return new Input(InputState.VALID, parse(raw), null); }
        catch (SettingIssue.InvalidValue invalid) { return new Input(InputState.INVALID, null, invalid.issue()); }
    }
    @SuppressWarnings({"rawtypes", "unchecked"})
    public Object parse(String raw) {
        try {
            Object value = switch (kind()) {
                case BOOLEAN -> {
                    if (!raw.equals("true") && !raw.equals("false")) fail("type", "Expected true or false");
                    yield Boolean.parseBoolean(raw);
                }
                case INTEGER -> new BigDecimal(raw.strip()).intValueExact();
                case LONG -> new BigDecimal(raw.strip()).longValueExact();
                case DECIMAL -> {
                    var decimal = new BigDecimal(raw.strip());
                    if (valueType == float.class || valueType == Float.class) {
                        float number = decimal.floatValue(); if (!Float.isFinite(number)) fail("finite", "Expected a finite number");
                        yield decimal;
                    }
                    if (!Double.isFinite(decimal.doubleValue())) fail("finite", "Expected a finite number");
                    yield decimal;
                }
                case CHOICE -> Enum.valueOf((Class) valueType, raw);
                case STRING -> raw;
                case STRINGS -> JsonEdits.textArray(raw);
            };
            Object nativeValue = value;
            if (value instanceof BigDecimal decimal && kind() == Kind.DECIMAL) {
                if (valueType == float.class || valueType == Float.class) nativeValue = Float.valueOf(decimal.floatValue());
                else nativeValue = Double.valueOf(decimal.doubleValue());
            }
            validate(nativeValue);
            return value;
        } catch (SettingIssue.InvalidValue failure) { throw failure; }
        catch (RuntimeException invalid) { throw new SettingIssue.InvalidValue(issue("type", "Invalid " + kind().name().toLowerCase(java.util.Locale.ROOT) + " value for " + key)); }
    }
    public void validate(Object value) {
        if (value == null) fail("required", "Missing value for " + key);
        if (value instanceof Number number) {
            final BigDecimal decimal;
            try { decimal = new BigDecimal(number.toString()); }
            catch (NumberFormatException invalid) { fail("finite", "Expected a finite number for " + key); return; }
            if (minimum() != null && (decimal.compareTo(minimum()) < 0 || constraints.exclusiveMinimum() && decimal.compareTo(minimum()) == 0))
                fail("minimum", key + " must be " + (constraints.exclusiveMinimum() ? "greater than " : "at least ") + minimum());
            if (maximum() != null && decimal.compareTo(maximum()) > 0) fail("maximum", key + " must be at most " + maximum());
        }
        if (value instanceof String text) validateText(text);
        if (value instanceof java.util.Collection<?> values) {
            if (maximumEntries() >= 0 && values.size() > maximumEntries()) fail("length", key + " allows at most " + maximumEntries() + " entries");
            if ((valueType == Set.class || constraints.unique()) && values.stream().distinct().count() != values.size()) fail("unique", "Entries must be unique for " + key);
            for (Object item : values) {
                if (!(item instanceof String)) fail("type", "Expected text entries for " + key);
                validateText((String) item);
            }
        }
        validateOwner(value);
    }
    private void validateOwner(Object value) {
        try { ownerValidator.accept(value); }
        catch (SettingIssue.InvalidValue invalid) { throw invalid; }
        catch (RuntimeException invalid) { fail("native_value", String.valueOf(invalid.getMessage())); }
    }
    /** Element syntax is independent of collection size and duplicate validation. */
    public SettingIssue elementIssue(String text) {
        if (kind() != Kind.STRINGS) throw new IllegalArgumentException("Not a text collection");
        try { validateText(text); validateOwner(List.of(text)); return null; }
        catch (SettingIssue.InvalidValue invalid) { return invalid.issue(); }
    }
    private void validateText(String text) {
        if (constraints.nonBlank() && text.isBlank()) fail("required", "Text must not be blank for " + key);
        if (constraints.maximumLength() >= 0 && text.length() > constraints.maximumLength()) fail("length", "Text is too long for " + key);
        if (!constraints.pattern().isEmpty() && !Pattern.matches(constraints.pattern(), text)) fail("pattern", "Invalid identifier/text for " + key);
    }
    private SettingIssue issue(String code, String message) { return new SettingIssue(code, key, List.of(), message); }
    private void fail(String code, String message) { throw new SettingIssue.InvalidValue(issue(code, message)); }
}
