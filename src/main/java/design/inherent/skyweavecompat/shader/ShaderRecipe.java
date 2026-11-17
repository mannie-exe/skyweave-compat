package design.inherent.skyweavecompat.shader;

import com.google.gson.*;
import java.io.IOException;
import java.util.*;

/** Versioned data contract. Recipes cannot select executable classes or commands. */
public record ShaderRecipe(JsonObject json, String id, String output, boolean enabled) {
    public static ShaderRecipe parse(String text) throws IOException {
        try {
            int depth = 0; boolean quoted = false, escaped = false;
            for (char character : text.toCharArray()) {
                if (quoted) { if (escaped) escaped = false; else if (character == '\\') escaped = true; else if (character == '"') quoted = false; }
                else if (character == '"') quoted = true;
                else if (character == '{' || character == '[') { if (++depth > 32) throw new IOException("Recipe nesting exceeds limit"); }
                else if (character == '}' || character == ']') depth--;
            }
            JsonObject value = JsonParser.parseString(text).getAsJsonObject();
            keys(value, "schema", "id", "version", "enabled", "output", "base", "inputs", "steps", "requires", "expected", "settings");
            if (!value.get("schema").toString().equals("1")) throw new IOException("Unsupported recipe schema");
            String id = string(value, "id"), output = string(value, "output");
            if (!id.matches("[a-z0-9][a-z0-9_-]{0,63}")) throw new IOException("Invalid recipe identity");
            filename(output);
            if (!output.endsWith(".zip")) throw new IOException("Output must be a ZIP filename");
            string(value, "version"); string(value, "base");
            if (value.getAsJsonObject("inputs").size() > 12 || value.getAsJsonArray("steps").size() > 64)
                throw new IOException("Recipe exceeds operation limits");
            if (value.has("settings")) {
                var settings = value.getAsJsonObject("settings");
                keys(settings, "compatible", "renames", "seedSha256", "fromInput");
                if (settings.has("fromInput") && !value.getAsJsonObject("inputs").has(string(settings, "fromInput")))
                    throw new IOException("Unknown settings input");
            }
            return new ShaderRecipe(value, id, output, !value.has("enabled") || value.get("enabled").getAsBoolean());
        } catch (IllegalStateException | NullPointerException | IllegalArgumentException failure) {
            throw new IOException("Invalid shader recipe", failure);
        }
    }
    public static String string(JsonObject value, String name) throws IOException {
        if (!value.has(name) || !value.get(name).isJsonPrimitive() || !value.get(name).getAsJsonPrimitive().isString())
            throw new IOException("Expected string: " + name);
        String result = value.get(name).getAsString();
        if (result.isBlank()) throw new IOException("Empty " + name);
        return result;
    }
    public static void keys(JsonObject value, String... allowed) throws IOException {
        var names = Set.of(allowed);
        for (String key : value.keySet()) if (!names.contains(key)) throw new IOException("Unknown recipe field: " + key);
    }
    public static String filename(String name) throws IOException {
        ShaderArchives.path(name);
        if (name.contains("/") || name.length() > 180 || name.matches("(?i)(con|prn|aux|nul|com[1-9]|lpt[1-9])(\\..*)?"))
            throw new IOException("Unsafe filename: " + name);
        return name;
    }
    public String canonical() { return canonical(json).toString(); }
    static JsonElement canonical(JsonElement value) {
        if (value.isJsonObject()) {
            var result = new JsonObject();
            new TreeSet<>(value.getAsJsonObject().keySet()).forEach(k -> result.add(k, canonical(value.getAsJsonObject().get(k))));
            return result;
        }
        if (value.isJsonArray()) {
            var result = new JsonArray(); value.getAsJsonArray().forEach(v -> result.add(canonical(v))); return result;
        }
        return value.deepCopy();
    }
}
