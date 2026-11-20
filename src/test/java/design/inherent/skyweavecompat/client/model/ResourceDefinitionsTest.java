package design.inherent.skyweavecompat.client.model;

import com.google.gson.*;
import java.util.*;
import java.util.function.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ResourceDefinitionsTest {
    private record Context(JsonElement component, boolean using, double duration) {}
    private static final ResourceDefinitions.Values<Context> VALUES = new ResourceDefinitions.Values<>() {
        public ResourceDefinitions.Selector<Context> select(String property, JsonObject definition) {
            return new ResourceDefinitions.Selector<>() {
                public Object decode(JsonElement value) { return value.deepCopy(); }
                public Object current(Context context) { return context.component; }
            };
        }
        public Predicate<Context> condition(String property, JsonObject definition) { return Context::using; }
        public ToDoubleFunction<Context> range(String property, JsonObject definition) { return Context::duration; }
        public ToIntFunction<Context> tint(String type, JsonObject definition) {
            int value = definition.get("value").getAsInt(); return context -> value;
        }
    };

    @Test void completeComponentValuesSelectWithoutSubsetCredit() {
        var program = compile("""
                {"type":"select","property":"component","component":"stored_enchantments",
                 "cases":[{"when":{"minecraft:sharpness":2},"model":{"type":"model","model":"test:sharpness"}}],
                 "fallback":{"type":"model","model":"test:generic"}}
                """);
        assertEquals("test:sharpness", model(program, "{\"minecraft:sharpness\":2}"));
        assertEquals("test:generic", model(program, "{\"minecraft:sharpness\":3}"));
        assertEquals("test:generic", model(program, "{\"minecraft:sharpness\":2,\"minecraft:mending\":1}"));
        assertEquals("test:generic", model(program, "{}"));
        assertEquals(Set.of("test:sharpness", "test:generic"), program.dependencies());
    }

    @Test void nestedListsAreCompleteAlternativeValues() {
        var program = compile("""
                {"type":"select","property":"component","component":"suspicious_stew_effects",
                 "cases":[{"when":[[{"id":"minecraft:blindness","duration":220}],[{"id":"minecraft:blindness","duration":200}]],
                 "model":{"type":"model","model":"test:blindness"}}],"fallback":{"type":"model","model":"test:generic"}}
                """);
        assertEquals("test:blindness", model(program, "[{\"id\":\"minecraft:blindness\",\"duration\":220}]"));
        assertEquals("test:blindness", model(program, "[{\"id\":\"minecraft:blindness\",\"duration\":200}]"));
        assertEquals("test:generic", model(program, "[{\"id\":\"minecraft:blindness\",\"duration\":201}]"));
        assertEquals("test:generic", model(program, "[]"));
    }

    @Test void rangeAndConditionPreserveBoundarySelectionAndChildTints() {
        var program = compile("""
                {"type":"composite","models":[
                  {"type":"model","model":"test:glass","tints":[{"type":"constant","value":7}]},
                  {"type":"condition","property":"using_item","on_false":{"type":"empty"},
                   "on_true":{"type":"range_dispatch","property":"use_duration","scale":2,
                     "entries":[{"threshold":10,"model":{"type":"model","model":"test:late","tints":[{"type":"constant","value":19}]}},
                                {"threshold":0,"model":{"type":"model","model":"test:early"}}]}}]}
                """);
        var idle = program.layers(new Context(JsonNull.INSTANCE, false, 20));
        assertEquals(List.of("test:glass"), idle.stream().map(ResourceDefinitions.Layer::model).toList());
        assertEquals(7, idle.getFirst().tints().getFirst().applyAsInt(null));
        assertEquals(List.of("test:glass", "test:early"), program.layers(new Context(JsonNull.INSTANCE, true, 4.99)).stream().map(ResourceDefinitions.Layer::model).toList());
        var boundary = program.layers(new Context(JsonNull.INSTANCE, true, 5));
        assertEquals(List.of("test:glass", "test:late"), boundary.stream().map(ResourceDefinitions.Layer::model).toList());
        assertEquals(19, boundary.get(1).tints().getFirst().applyAsInt(null));
        assertEquals(Set.of("test:glass", "test:late", "test:early"), program.dependencies());
    }

    @Test void duplicateDecodedCasesAndInvalidValuesRefuseTheDefinition() {
        assertThrows(JsonParseException.class, () -> compile("""
                {"type":"select","property":"component","cases":[
                  {"when":{"a":1,"b":2},"model":{"type":"model","model":"test:a"}},
                  {"when":{"b":2,"a":1},"model":{"type":"model","model":"test:b"}}]}
                """));
        assertThrows(JsonParseException.class, () -> compile("{\"type\":\"future_node\"}"));
        assertThrows(JsonParseException.class, () -> compile("{\"type\":\"model\",\"model\":\"test:leaf\",\"future_tint\":7}"));
        assertThrows(JsonParseException.class, () -> compile("{\"type\":\"model\",\"model\":\"Invalid:Model\"}"));
        assertThrows(JsonParseException.class, () -> compile("{\"type\":\"range_dispatch\",\"property\":\"use_duration\",\"entries\":[{\"threshold\":\"0\",\"model\":{\"type\":\"empty\"}}]}"));
        String tint = "{\"type\":\"constant\",\"value\":1}";
        assertThrows(JsonParseException.class, () -> compile("{\"type\":\"model\",\"model\":\"test:leaf\",\"tints\":["
                + String.join(",", Collections.nCopies(ResourceDefinitions.MAX_TINTS + 1, tint)) + "]}"));
    }

    @Test void deepProgramsRefuseBeforeEvaluationAndOutputsAreImmutable() {
        JsonObject node = JsonParser.parseString("{\"type\":\"model\",\"model\":\"test:leaf\"}").getAsJsonObject();
        for (int i = 0; i < ResourceDefinitions.MAX_DEPTH + 1; i++) {
            var parent = new JsonObject(); parent.addProperty("type", "composite");
            var children = new JsonArray(); children.add(node); parent.add("models", children); node = parent;
        }
        var root = new JsonObject(); root.add("model", node);
        assertThrows(JsonParseException.class, () -> ResourceDefinitions.compile(root, VALUES));
        var simple = compile("{\"type\":\"model\",\"model\":\"test:leaf\"}");
        assertThrows(UnsupportedOperationException.class, () -> simple.dependencies().clear());
        assertThrows(UnsupportedOperationException.class, () -> simple.layers(new Context(JsonNull.INSTANCE, false, 0)).clear());
    }

    @Test void identicalArtworkThresholdsHaveOneResultButConflictsRefuse() {
        String entry = "{\"threshold\":0,\"model\":{\"type\":\"model\",\"model\":\"test:berry\"}}";
        String prefix = "{\"type\":\"range_dispatch\",\"property\":\"use_duration\",\"entries\":[";
        var root = compile(prefix + entry + "," + entry + "]}");
        assertEquals("test:berry", root.layers(new Context(JsonNull.INSTANCE, false, 0)).getFirst().model());
        assertThrows(JsonParseException.class, () -> compile(prefix + entry + "," + entry.replace("test:berry", "test:other") + "]}"));
    }

    @Test void nativeFloatThresholdRoundingPreservesTheExactBoundary() {
        var root = compile("""
                {"type":"range_dispatch","property":"crossbow/pull","entries":[
                  {"threshold":0.58,"model":{"type":"model","model":"test:charged"}}],
                 "fallback":{"type":"model","model":"test:pulling"}}
                """);
        assertEquals("test:charged", root.layers(new Context(JsonNull.INSTANCE, false, 0.58f)).getFirst().model());
        assertEquals("test:pulling", root.layers(new Context(JsonNull.INSTANCE, false, Math.nextDown(0.58f))).getFirst().model());
        assertThrows(JsonParseException.class, () -> compile("{\"type\":\"range_dispatch\",\"property\":\"count\",\"scale\":1e100,\"entries\":[]}"));
    }

    private static ResourceDefinitions.Root<Context> compile(String model) {
        var root = new JsonObject(); root.add("model", JsonParser.parseString(model));
        return ResourceDefinitions.compile(root, VALUES);
    }
    private static String model(ResourceDefinitions.Root<Context> root, String component) {
        return root.layers(new Context(JsonParser.parseString(component), false, 0)).getFirst().model();
    }
}
