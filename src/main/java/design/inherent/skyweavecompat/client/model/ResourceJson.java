package design.inherent.skyweavecompat.client.model;

import com.google.gson.*;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import java.io.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;

/** Bound the input before constructing a tree; reject duplicate keys rather than changing their meaning. */
final class ResourceJson {
    private ResourceJson() {}
    static JsonObject read(InputStream stream) throws IOException {
        byte[] bytes = stream.readNBytes(ResourceDefinitions.MAX_BYTES + 1);
        if (bytes.length > ResourceDefinitions.MAX_BYTES) throw new JsonParseException("Definition exceeds byte limit");
        try (var reader = new JsonReader(new InputStreamReader(new ByteArrayInputStream(bytes), StandardCharsets.UTF_8))) {
            var result = value(reader, 0);
            if (reader.peek() != JsonToken.END_DOCUMENT) throw new JsonParseException("Trailing JSON data");
            return ResourceDefinitions.object(result);
        }
    }
    private static JsonElement value(JsonReader reader, int depth) throws IOException {
        if (depth > 64) throw new JsonParseException("JSON nesting limit at " + reader.getPath());
        return switch (reader.peek()) {
            case BEGIN_OBJECT -> {
                var object = new JsonObject(); reader.beginObject();
                while (reader.hasNext()) {
                    String key = reader.nextName();
                    if (object.has(key)) throw new JsonParseException("Duplicate JSON key at " + reader.getPath());
                    object.add(key, value(reader, depth + 1));
                }
                reader.endObject(); yield object;
            }
            case BEGIN_ARRAY -> {
                var array = new JsonArray(); reader.beginArray();
                while (reader.hasNext()) array.add(value(reader, depth + 1));
                reader.endArray(); yield array;
            }
            case STRING -> new JsonPrimitive(reader.nextString());
            case BOOLEAN -> new JsonPrimitive(reader.nextBoolean());
            case NUMBER -> {
                String number = reader.nextString();
                if (number.length() > 128) throw new JsonParseException("Number exceeds limit at " + reader.getPath());
                yield new JsonPrimitive(new BigDecimal(number));
            }
            case NULL -> { reader.nextNull(); yield JsonNull.INSTANCE; }
            default -> throw new JsonParseException("Expected JSON value at " + reader.getPath());
        };
    }
}
