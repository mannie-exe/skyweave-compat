package design.inherent.skyweavecompat.client.model;

import com.google.gson.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MultiAxisModelsTest {
    private static JsonObject rotation(String fields) {
        return JsonParser.parseString("{\"origin\":[0,0,0]," + fields + "}").getAsJsonObject();
    }
    private static JsonObject model(JsonObject rotation) {
        var root = JsonParser.parseString("{\"textures\":{\"particle\":\"minecraft:block/glass\",\"0\":\"minecraft:block/glass\"},\"display\":{\"gui\":{\"scale\":[1,2,3]}},\"elements\":[{\"from\":[1,2,3],\"to\":[4,5,6],\"faces\":{\"north\":{\"texture\":\"#0\",\"tintindex\":0,\"uv\":[1,2,3,4]}}}]}").getAsJsonObject();
        root.getAsJsonArray("elements").get(0).getAsJsonObject().add("rotation", rotation);
        return root;
    }
    private static double[] point(JsonArray matrix, double x, double y, double z) {
        var p = new double[]{x, y, z, 1}; var result = new double[3];
        for (int row = 0; row < 3; row++) for (int column = 0; column < 4; column++)
            result[row] += matrix.get(row).getAsJsonArray().get(column).getAsDouble() * p[column];
        return result;
    }

    @Test void rotationsUseTheObservedNativeXThenYThenZOrderAndPixelPivot() {
        assertArrayEquals(new double[]{2, -3, -1}, point(MultiAxisModels.matrix(rotation("\"x\":90,\"y\":90")), 1, 2, 3), 1e-5);
        var pivot = rotation("\"x\":-8.99061,\"y\":-3.96737,\"z\":-12.61739");
        pivot.add("origin", JsonParser.parseString("[4,8,12]"));
        assertArrayEquals(new double[]{.25, .5, .75}, point(MultiAxisModels.matrix(pivot), .25, .5, .75), 1e-6);
    }

    @Test void reexpressionPreservesOriginalUvTintDisplayAndTranslucentPolicy() {
        var original = model(rotation("\"z\":12")); original.addProperty("render_type", "minecraft:translucent");
        var before = original.deepCopy(); var transformed = MultiAxisModels.adapt(original).getAsJsonObject();
        assertEquals(before, original); assertEquals("neoforge:composite", transformed.get("loader").getAsString());
        assertEquals(before.get("display"), transformed.get("display"));
        var child = transformed.getAsJsonObject("children").getAsJsonObject("rotated_0");
        assertEquals(before.get("textures"), child.get("textures"));
        assertEquals("minecraft:translucent", child.get("render_type").getAsString());
        var expected = before.getAsJsonArray("elements").get(0).getAsJsonObject().deepCopy(); expected.remove("rotation");
        assertEquals(expected, child.getAsJsonArray("elements").get(0));
    }

    @Test void nativeAndForeignOwnersArePassedThroughAndOldNotationWins() {
        var legacy = model(rotation("\"axis\":\"x\",\"angle\":22.5,\"x\":90"));
        assertSame(legacy, MultiAxisModels.adapt(legacy));
        for (String key : new String[]{"parent", "loader", "transform"}) {
            var owned = model(rotation("\"x\":12")); owned.addProperty(key, "foreign:owner");
            assertSame(owned, MultiAxisModels.adapt(owned));
        }
        var nativeChild = model(rotation("\"axis\":\"z\",\"angle\":22.5")).getAsJsonArray("elements").get(0);
        var mixed = model(rotation("\"y\":12")); mixed.getAsJsonArray("elements").add(nativeChild);
        var result = MultiAxisModels.adapt(mixed).getAsJsonObject();
        assertEquals(nativeChild, result.getAsJsonObject("children").getAsJsonObject("legacy").getAsJsonArray("elements").get(0));
    }

    @Test void malformedUnsupportedAndOverBudgetModelsRefuseWithoutMutatingInputs() {
        for (String fields : new String[]{"\"x\":\"12\"", "\"x\":1e100", "\"x\":12,\"rescale\":true", "\"x\":12,\"rescale\":\"false\""}) {
            var bad = model(rotation(fields)); var original = bad.deepCopy();
            assertThrows(JsonParseException.class, () -> MultiAxisModels.adapt(bad)); assertEquals(original, bad);
        }
        var culled = model(rotation("\"x\":12"));
        culled.getAsJsonArray("elements").get(0).getAsJsonObject().getAsJsonObject("faces").getAsJsonObject("north").addProperty("cullface", "north");
        assertThrows(JsonParseException.class, () -> MultiAxisModels.adapt(culled));
        var crowded = model(rotation("\"x\":12")); var element = crowded.getAsJsonArray("elements").get(0);
        for (int i = 0; i < MultiAxisModels.MAX_ELEMENTS; i++) crowded.getAsJsonArray("elements").add(element.deepCopy());
        assertThrows(JsonParseException.class, () -> MultiAxisModels.adapt(crowded));
    }
}
