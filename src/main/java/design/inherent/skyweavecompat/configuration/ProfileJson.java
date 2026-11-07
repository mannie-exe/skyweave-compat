package design.inherent.skyweavecompat.configuration;

import com.google.gson.*;
import design.inherent.skyweavecompat.terrain.GenerationProfile;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/** Profile parsing is independent of optional generator classes and never applies runtime settings. */
public final class ProfileJson {
    private static final Gson JSON = new Gson();
    /** Startup and editor validation must reject the same ambiguous input. */
    public static JsonObject object(String text) {
        try { return JsonEdits.object(text); }
        catch (java.io.IOException invalid) { throw new IllegalArgumentException(invalid.getMessage(), invalid); }
    }
    public static GenerationProfile generation(String text) {
        var object = object(text);
        Set<String> fields = Arrays.stream(GenerationProfile.class.getRecordComponents())
                .map(component -> component.getName()).collect(Collectors.toSet());
        if (!object.keySet().equals(fields)) {
            throw new IllegalArgumentException("Generation profile must contain exactly these fields: " + fields);
        }
        for (var entry : object.entrySet()) {
            if (!entry.getValue().isJsonPrimitive() || !entry.getValue().getAsJsonPrimitive().isNumber()) {
                throw new IllegalArgumentException("Expected a number for " + entry.getKey());
            }
            if (!entry.getKey().equals("activation")) try { entry.getValue().getAsBigDecimal().intValueExact(); }
            catch (ArithmeticException invalid) { throw new IllegalArgumentException("Expected an integer for " + entry.getKey(), invalid); }
        }
        return JSON.fromJson(object, GenerationProfile.class);
    }

    /** Strict nested owned records: Gson's string-to-number coercion must not bypass editor validation. */
    public static <T extends Record> T record(String text, Class<T> type) {
        var value = object(text);
        recordFields(value, type, "");
        return JSON.fromJson(value, type);
    }
    private static void recordFields(JsonObject object, Class<?> type, String path) {
        var fields = Arrays.stream(type.getRecordComponents()).map(c -> c.getName()).collect(Collectors.toSet());
        if (!object.keySet().equals(fields)) throw new IllegalArgumentException(path + " requires exactly " + fields);
        for (var component : type.getRecordComponents()) {
            String key = path + component.getName(); var value = object.get(component.getName()); var field = component.getType();
            if (field.isRecord()) {
                if (!value.isJsonObject()) throw new IllegalArgumentException("Expected group for " + key);
                recordFields(value.getAsJsonObject(), field, key + ".");
            } else if (field == java.util.List.class) {
                if (!value.isJsonArray() || value.getAsJsonArray().asList().stream()
                        .anyMatch(v -> !v.isJsonPrimitive() || !v.getAsJsonPrimitive().isString()))
                    throw new IllegalArgumentException("Expected a text list for " + key);
            } else {
                if (!value.isJsonPrimitive()) throw new IllegalArgumentException("Expected scalar for " + key);
                var scalar = value.getAsJsonPrimitive();
                if (field == boolean.class ? !scalar.isBoolean() : !scalar.isNumber())
                    throw new IllegalArgumentException("Wrong value type for " + key);
                if (field == int.class) try { scalar.getAsBigDecimal().intValueExact(); }
                catch (ArithmeticException error) {
                    var invalid = new ArithmeticException("Expected integer for " + key);
                    invalid.initCause(error); throw invalid;
                }
            }
        }
    }
    private ProfileJson() {}
}
