package design.inherent.skyweavecompat.client.model;

import com.google.gson.*;
import java.util.*;
import org.joml.Matrix4f;

/** Re-express supported modern element rotations at resource load, never per rendered frame. */
public final class MultiAxisModels {
    public static final int MAX_ELEMENTS = 128;
    private MultiAxisModels() {}

    public static JsonElement adapt(JsonElement input) {
        if (!input.isJsonObject()) return input;
        var source = input.getAsJsonObject();
        if (!source.has("elements") || !source.get("elements").isJsonArray()
                || source.has("loader") || source.has("parent") || source.has("transform")) return input;
        var elements = source.getAsJsonArray("elements");
        boolean modern = false;
        for (var element : elements) if (element.isJsonObject() && euler(element.getAsJsonObject())) modern = true;
        if (!modern) return input;
        if (elements.size() > MAX_ELEMENTS) throw new JsonParseException("Multi-axis model exceeds " + MAX_ELEMENTS + " elements");

        var children = new JsonObject();
        var legacy = new JsonArray();
        int count = 0;
        for (var value : elements) {
            if (!value.isJsonObject()) throw new JsonParseException("Expected model element object");
            var element = value.getAsJsonObject();
            if (!euler(element)) { legacy.add(element.deepCopy()); continue; }
            var rotation = element.getAsJsonObject("rotation");
            if (rotation.has("rescale")) {
                var valueScale = rotation.get("rescale");
                if (!valueScale.isJsonPrimitive() || !valueScale.getAsJsonPrimitive().isBoolean())
                    throw new JsonParseException("Expected boolean multi-axis rescale");
                if (valueScale.getAsBoolean()) throw new JsonParseException("Multi-axis rescale needs explicit qualification");
            }
            if (element.has("faces")) for (var face : element.getAsJsonObject("faces").entrySet())
                if (face.getValue().isJsonObject() && face.getValue().getAsJsonObject().has("cullface"))
                    throw new JsonParseException("Multi-axis cull faces need explicit qualification");
            var body = element.deepCopy(); body.remove("rotation");
            var array = new JsonArray(); array.add(body);
            var child = child(source, array);
            var transform = new JsonObject(); transform.add("matrix", matrix(rotation));
            child.add("transform", transform);
            children.add("rotated_" + count++, child);
        }
        if (!legacy.isEmpty()) children.add("legacy", child(source, legacy));
        var result = source.deepCopy(); result.remove("elements"); result.remove("groups");
        result.addProperty("loader", "neoforge:composite"); result.add("children", children);
        return result;
    }

    private static boolean euler(JsonObject element) {
        if (!element.has("rotation") || !element.get("rotation").isJsonObject()) return false;
        var rotation = element.getAsJsonObject("rotation");
        // Minecraft 1.21.11 gives the original notation precedence, even when both sets occur.
        return !rotation.has("axis") && !rotation.has("angle")
                && (rotation.has("x") || rotation.has("y") || rotation.has("z"));
    }

    private static JsonObject child(JsonObject source, JsonArray elements) {
        var result = new JsonObject();
        for (var key : List.of("textures", "ambientocclusion", "gui_light", "render_type"))
            if (source.has(key)) result.add(key, source.get(key).deepCopy());
        result.add("elements", elements);
        return result;
    }

    /** X then Y then Z matches native EulerXYZRotation.rotationZYX; origin is in model pixels. */
    public static JsonArray matrix(JsonObject rotation) {
        if (!rotation.has("origin") || !rotation.get("origin").isJsonArray()
                || rotation.getAsJsonArray("origin").size() != 3)
            throw new JsonParseException("Expected three finite rotation-origin coordinates");
        var origin = rotation.getAsJsonArray("origin");
        float px = finite(origin.get(0)) / 16, py = finite(origin.get(1)) / 16, pz = finite(origin.get(2)) / 16;
        float radians = (float) (Math.PI / 180);
        var transform = new Matrix4f().translation(px, py, pz)
                .rotateZYX(angle(rotation, "z") * radians, angle(rotation, "y") * radians, angle(rotation, "x") * radians)
                .translate(-px, -py, -pz);
        var rows = new JsonArray();
        for (int row = 0; row < 3; row++) {
            var columns = new JsonArray();
            for (int column = 0; column < 4; column++) {
                float value = transform.get(column, row);
                if (!Float.isFinite(value)) throw new JsonParseException("Nonfinite multi-axis transform");
                columns.add(value);
            }
            rows.add(columns);
        }
        return rows;
    }

    private static float angle(JsonObject rotation, String key) { return rotation.has(key) ? finite(rotation.get(key)) : 0; }
    private static float finite(JsonElement value) {
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber() || !Float.isFinite(value.getAsFloat()))
            throw new JsonParseException("Expected finite numeric model rotation");
        return value.getAsFloat();
    }
}
