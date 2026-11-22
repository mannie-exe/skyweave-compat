package design.inherent.skyweavecompat.configuration;

import com.google.gson.*;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import java.io.IOException;
import java.io.StringReader;
import java.math.BigDecimal;
import java.util.List;

/** Strict owned configuration input shared by runtime codecs and editing adapters. */
public final class ConfigJson {
    // Reject duplicate keys rather than letting Gson silently discard one value.
    public static JsonObject object(String text) throws IOException {
        return object(text, 16);
    }

    public static JsonObject object(String text, int maximumDepth) throws IOException {
        try (var reader = new JsonReader(new StringReader(text))) {
            var value = read(reader, 0, maximumDepth);
            if (!value.isJsonObject() || reader.peek() != JsonToken.END_DOCUMENT)
                throw new IOException("Expected one JSON object");
            return value.getAsJsonObject();
        }
    }
    private static JsonElement read(JsonReader reader, int depth, int maximumDepth) throws IOException {
        if (depth > maximumDepth) throw new IOException("JSON nesting exceeds input limit");
        return switch (reader.peek()) {
            case BEGIN_OBJECT -> {
                var object = new JsonObject(); reader.beginObject();
                while (reader.hasNext()) {
                    String name = reader.nextName();
                    if (object.has(name)) throw new IOException("Duplicate JSON key: " + name);
                    object.add(name, read(reader, depth + 1, maximumDepth));
                }
                reader.endObject(); yield object;
            }
            case BEGIN_ARRAY -> {
                var array = new JsonArray(); reader.beginArray();
                while (reader.hasNext()) array.add(read(reader, depth + 1, maximumDepth));
                reader.endArray(); yield array;
            }
            case STRING -> new JsonPrimitive(reader.nextString());
            case NUMBER -> new JsonPrimitive(new BigDecimal(reader.nextString()));
            case BOOLEAN -> new JsonPrimitive(reader.nextBoolean());
            case NULL -> { reader.nextNull(); yield JsonNull.INSTANCE; }
            default -> throw new IOException("Unexpected JSON token");
        };
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
    private ConfigJson() {}
}
