package design.inherent.skyweavecompat.client.model;

import com.google.gson.*;
import java.util.*;
import java.util.function.*;

/** Immutable resource programs. Platform values and rendering stay outside the compiler. */
public final class ResourceDefinitions {
    public static final int MAX_BYTES = 262_144, MAX_DEPTH = 32, MAX_NODES = 4096, MAX_CASES = 512;
    public static final int MAX_TINTS = 32;
    public static final String MISSING = "minecraft:builtin/missing";
    private ResourceDefinitions() {}

    public interface Selector<C> {
        Object decode(JsonElement value);
        Object current(C context);
    }

    public interface Values<C> {
        Selector<C> select(String property, JsonObject definition);
        Predicate<C> condition(String property, JsonObject definition);
        ToDoubleFunction<C> range(String property, JsonObject definition);
        ToIntFunction<C> tint(String type, JsonObject definition);
    }

    public record Root<C>(Node<C> model, Set<String> dependencies, boolean handAnimation,
                          boolean oversizedInGui, float swapScale) {
        public Root { dependencies = Set.copyOf(dependencies); }
        public List<Layer<C>> layers(C context) {
            var result = new ArrayList<Layer<C>>();
            model.append(context, result);
            return List.copyOf(result);
        }
    }

    public record Layer<C>(String model, List<ToIntFunction<C>> tints) {
        public Layer { tints = List.copyOf(tints); }
    }

    public sealed interface Node<C> permits Leaf, Choice, Branch, Dispatch, Composite, Empty {
        void append(C context, List<Layer<C>> target);
    }

    private record Leaf<C>(Layer<C> layer) implements Node<C> {
        public void append(C context, List<Layer<C>> target) { target.add(layer); }
    }
    private record Choice<C>(Selector<C> selector, Map<Object, Node<C>> cases, Node<C> fallback) implements Node<C> {
        public void append(C context, List<Layer<C>> target) {
            var selected = cases.get(selector.current(context));
            (selected == null ? fallback : selected).append(context, target);
        }
    }
    private record Branch<C>(Predicate<C> predicate, Node<C> yes, Node<C> no) implements Node<C> {
        public void append(C context, List<Layer<C>> target) { (predicate.test(context) ? yes : no).append(context, target); }
    }
    private record Threshold<C>(float value, Node<C> model) {}
    private record Dispatch<C>(ToDoubleFunction<C> property, float scale, List<Threshold<C>> entries, Node<C> fallback) implements Node<C> {
        public void append(C context, List<Layer<C>> target) {
            // Native model properties, scale and thresholds are floats. Preserve their boundary rounding.
            float value = (float) property.applyAsDouble(context) * scale;
            if (!Float.isNaN(value)) {
                int low = 0, high = entries.size();
                while (low < high) {
                    int middle = (low + high) >>> 1;
                    if (Float.compare(entries.get(middle).value, value) <= 0) low = middle + 1; else high = middle;
                }
                if (low != 0) { entries.get(low - 1).model.append(context, target); return; }
            }
            fallback.append(context, target);
        }
    }
    private record Composite<C>(List<Node<C>> children) implements Node<C> {
        public void append(C context, List<Layer<C>> target) { for (var child : children) child.append(context, target); }
    }
    private record Empty<C>() implements Node<C> {
        public void append(C context, List<Layer<C>> target) {}
    }

    public static <C> Root<C> compile(JsonObject input, Values<C> values) {
        fields(input, Set.of("model", "hand_animation_on_swap", "oversized_in_gui", "swap_animation_scale"), "root");
        var compiler = new Compiler<>(values);
        var model = compiler.node(object(required(input, "model")), 0);
        float scale = (float) number(input, "swap_animation_scale", 1);
        if (!Float.isFinite(scale)) throw invalid("Nonfinite hand-swap scale");
        return new Root<>(model, compiler.models, bool(input, "hand_animation_on_swap", true),
                bool(input, "oversized_in_gui", false), scale);
    }

    private static final class Compiler<C> {
        private final Values<C> values;
        private final Set<String> models = new LinkedHashSet<>();
        private int nodes;
        Compiler(Values<C> values) { this.values = Objects.requireNonNull(values); }

        Node<C> node(JsonObject definition, int depth) {
            if (depth > MAX_DEPTH || ++nodes > MAX_NODES) throw invalid("Resource definition exceeds node/depth limit");
            String type = identifier(string(definition, "type"));
            fields(definition, switch (type) {
                case "minecraft:model" -> Set.of("type", "model", "tints");
                case "minecraft:empty" -> Set.of("type");
                case "minecraft:composite" -> Set.of("type", "models");
                case "minecraft:select" -> Set.of("type", "property", "component", "cases", "fallback", "block_state_property", "ignore_default", "ignore_unknown");
                case "minecraft:condition" -> Set.of("type", "property", "predicate", "value", "component", "ignore_default", "keybind", "fluid", "on_true", "on_false");
                case "minecraft:range_dispatch" -> Set.of("type", "property", "entries", "fallback", "target", "wobble", "normalize", "source", "period", "remaining", "scale");
                default -> throw invalid("Unsupported resource model type: " + type);
            }, type);
            return switch (type) {
                case "minecraft:model" -> {
                    String model = identifier(string(definition, "model")); models.add(model);
                    var tints = new ArrayList<ToIntFunction<C>>();
                    if (definition.has("tints")) {
                        var source = array(definition, "tints");
                        if (source.size() > MAX_TINTS) throw invalid("Resource model exceeds tint limit");
                        for (var value : source) {
                            var tint = object(value);
                            tints.add(values.tint(identifier(string(tint, "type")), tint));
                        }
                    }
                    yield new Leaf<>(new Layer<>(model, tints));
                }
                case "minecraft:empty" -> new Empty<>();
                case "minecraft:composite" -> {
                    var children = new ArrayList<Node<C>>();
                    for (var child : array(definition, "models")) children.add(node(object(child), depth + 1));
                    yield new Composite<>(List.copyOf(children));
                }
                case "minecraft:select" -> {
                    var selector = values.select(identifier(string(definition, "property")), definition);
                    var cases = new LinkedHashMap<Object, Node<C>>();
                    var entries = array(definition, "cases");
                    if (entries.size() > MAX_CASES) throw invalid("Resource select exceeds case limit");
                    int alternatives = 0;
                    for (var entry : entries) {
                        var row = object(entry);
                        fields(row, Set.of("model", "when"), "select case");
                        var child = node(object(required(row, "model")), depth + 1);
                        var when = required(row, "when");
                        var options = when.isJsonArray() ? when.getAsJsonArray() : singleton(when);
                        if (options.isEmpty()) throw invalid("Empty select alternatives");
                        for (var option : options) {
                            if (++alternatives > MAX_CASES) throw invalid("Resource select exceeds alternative limit");
                            var key = Objects.requireNonNull(selector.decode(option), "Null resource case");
                            if (cases.putIfAbsent(key, child) != null) throw invalid("Duplicate decoded resource case");
                        }
                    }
                    yield new Choice<>(selector, Collections.unmodifiableMap(cases), fallback(definition, depth));
                }
                case "minecraft:condition" -> new Branch<>(values.condition(identifier(string(definition, "property")), definition),
                        node(object(required(definition, "on_true")), depth + 1), node(object(required(definition, "on_false")), depth + 1));
                case "minecraft:range_dispatch" -> {
                    var entries = new ArrayList<Threshold<C>>();
                    var thresholds = new HashMap<Float, JsonElement>();
                    var source = array(definition, "entries");
                    if (source.size() > MAX_CASES) throw invalid("Resource range exceeds entry limit");
                    for (var value : source) {
                        var row = object(value); float threshold = floating(row, "threshold", Double.NaN);
                        fields(row, Set.of("model", "threshold"), "range entry");
                        var body = required(row, "model");
                        var previous = thresholds.putIfAbsent(threshold, body);
                        // Some current artwork repeats the exact same threshold/model. It has one unambiguous result.
                        if (previous != null) {
                            if (!previous.equals(body)) throw invalid("Conflicting resource range threshold");
                            continue;
                        }
                        entries.add(new Threshold<>(threshold, node(object(required(row, "model")), depth + 1)));
                    }
                    entries.sort(Comparator.comparingDouble(Threshold::value));
                    yield new Dispatch<>(values.range(identifier(string(definition, "property")), definition),
                            floating(definition, "scale", 1), List.copyOf(entries), fallback(definition, depth));
                }
                default -> throw invalid("Unsupported resource model type: " + type);
            };
        }

        private Node<C> fallback(JsonObject definition, int depth) {
            return definition.has("fallback") ? node(object(definition.get("fallback")), depth + 1)
                    : new Leaf<>(new Layer<>(MISSING, List.of()));
        }
    }

    public static String identifier(String value) {
        String id = value.indexOf(':') < 0 ? "minecraft:" + value : value;
        if (id.length() > 512 || !id.matches("[a-z0-9_.-]+:[a-z0-9/._-]+")) throw invalid("Invalid resource identifier: " + value);
        return id;
    }
    public static JsonElement required(JsonObject object, String key) {
        var value = object.get(key);
        if (value == null || value.isJsonNull()) throw invalid("Missing resource field: " + key);
        return value;
    }
    public static String string(JsonObject object, String key) {
        var value = required(object, key);
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) throw invalid("Expected string: " + key);
        return value.getAsString();
    }
    public static JsonObject object(JsonElement value) {
        if (!value.isJsonObject()) throw invalid("Expected resource object");
        return value.getAsJsonObject();
    }
    public static JsonArray array(JsonObject object, String key) {
        var value = required(object, key);
        if (!value.isJsonArray()) throw invalid("Expected array: " + key);
        return value.getAsJsonArray();
    }
    public static boolean bool(JsonObject object, String key, boolean fallback) {
        if (!object.has(key)) return fallback;
        var value = required(object, key);
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isBoolean()) throw invalid("Expected boolean: " + key);
        return value.getAsBoolean();
    }
    public static double number(JsonObject object, String key, double fallback) {
        if (!object.has(key)) {
            if (!Double.isFinite(fallback)) throw invalid("Missing finite resource number: " + key);
            return fallback;
        }
        var value = required(object, key);
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber() || !Double.isFinite(value.getAsDouble()))
            throw invalid("Expected finite number: " + key);
        return value.getAsDouble();
    }
    static float floating(JsonObject object, String key, double fallback) {
        float value = (float) number(object, key, fallback);
        if (!Float.isFinite(value)) throw invalid("Expected finite float: " + key);
        return value;
    }
    private static JsonArray singleton(JsonElement value) { var array = new JsonArray(); array.add(value); return array; }
    static void fields(JsonObject value, Set<String> allowed, String owner) {
        for (var key : value.keySet()) if (!allowed.contains(key)) throw invalid("Unsupported " + owner + " field: " + key);
    }
    private static JsonParseException invalid(String message) { return new JsonParseException(message); }
}
