package design.inherent.skyweavecompat.shader;

import com.google.gson.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class ShaderWorkspaceTest {
    @TempDir Path root;
    private JsonObject setup() throws Exception {
        Files.createDirectories(root.resolve("shader-inputs"));
        byte[] source = ShaderArchives.write(Map.of("shaders/shaders.properties", "old".getBytes()));
        Files.write(root.resolve("shader-inputs/base.zip"), source);
        var recipe = JsonParser.parseString("""
            {"schema":1,"id":"test","version":"1","output":"Result.zip","base":"base","inputs":{},"steps":[],"settings":{"compatible":true}}
            """).getAsJsonObject();
        var input = new JsonObject(); input.addProperty("path", "shader-inputs/base.zip"); input.addProperty("sha256", ShaderArchives.sha256(source));
        recipe.getAsJsonObject("inputs").add("base", input); save(recipe); return recipe;
    }
    private void save(JsonObject value) throws Exception {
        Path directory = root.resolve("config/skyweave/shaders/recipes"); Files.createDirectories(directory);
        Files.writeString(directory.resolve("test.json"), value.toString());
    }
    private ShaderWorkspace.Status run(boolean force) throws Exception { return ShaderWorkspace.assemble(root, Map.of(), force, false).getFirst(); }
    @Test void cacheRepairAndDeterminism() throws Exception {
        setup(); assertEquals("built", run(false).state());
        Path result = root.resolve("shaderpacks/Result.zip"); byte[] first = Files.readAllBytes(result);
        assertEquals("cached", run(false).state());
        Files.writeString(result, "corrupt"); assertEquals("restored", run(false).state()); assertArrayEquals(first, Files.readAllBytes(result));
        assertEquals("built", run(true).state()); assertArrayEquals(first, Files.readAllBytes(result));
        Files.writeString(root.resolve("shader-inputs/base.zip"), "wrong");
        assertEquals("failed", run(false).state()); assertArrayEquals(first, Files.readAllBytes(result));
    }
    @Test void preservesUnownedOutputsAndOptionalDisabledInputs() throws Exception {
        var recipe = setup(); Files.createDirectories(root.resolve("shaderpacks"));
        Files.writeString(root.resolve("shaderpacks/Result.zip"), "user");
        assertEquals("failed", run(false).state()); assertEquals("user", Files.readString(root.resolve("shaderpacks/Result.zip")));
        recipe.addProperty("enabled", false); recipe.add("requires", JsonParser.parseString("{\"absent\":\"unsupported\"}")); save(recipe);
        Files.delete(root.resolve("shader-inputs/base.zip")); assertEquals("disabled", run(false).state());
    }
    @Test void orderedTextAndPreconditions() throws Exception {
        var recipe = setup();
        var step = new JsonObject(); step.addProperty("type", "text"); step.addProperty("path", "shaders/shaders.properties");
        step.addProperty("sha256", ShaderArchives.sha256("old".getBytes())); step.addProperty("find", "old"); step.addProperty("replace", "new"); step.addProperty("count", 1);
        recipe.getAsJsonArray("steps").add(step); save(recipe);
        assertEquals("built", run(false).state());
        assertEquals("new", new String(ShaderArchives.read(Files.readAllBytes(root.resolve("shaderpacks/Result.zip"))).get("shaders/shaders.properties")));
        step.addProperty("sha256", "0".repeat(64)); save(recipe); assertEquals("failed", run(true).state());
    }
    @Test void migrationPreservesUserSelectionAndOptions() throws Exception {
        var recipe = setup(); run(false);
        Files.createDirectories(root.resolve("config")); Files.writeString(root.resolve("config/iris.properties"), "enableShaders=false\nshaderPack=Result.zip\n");
        Files.writeString(root.resolve("shaderpacks/Result.zip.txt"), "QUALITY=custom\n");
        Files.writeString(root.resolve("shaderpacks/Next.zip.txt"), "QUALITY=default\n");
        recipe.getAsJsonObject("settings").addProperty("seedSha256", ShaderArchives.sha256("QUALITY=default\n".getBytes()));
        recipe.addProperty("output", "Next.zip"); recipe.addProperty("version", "2"); save(recipe);
        assertEquals("built", run(false).state());
        assertEquals("enableShaders=false\nshaderPack=Next.zip\n", Files.readString(root.resolve("config/iris.properties")));
        assertEquals("QUALITY=custom\n", Files.readString(root.resolve("shaderpacks/Next.zip.txt"))); assertFalse(Files.exists(root.resolve("shaderpacks/Result.zip")));
        Files.writeString(root.resolve("config/iris.properties"), "shaderPack=Other.zip\n");
        recipe.addProperty("output", "Third.zip"); save(recipe); run(false);
        assertEquals("shaderPack=Other.zip\n", Files.readString(root.resolve("config/iris.properties")));
    }
    @Test void rejectsTraversalSymlinksCollisionsAndUnknownOperations() throws Exception {
        setup(); assertThrows(Exception.class, () -> ShaderArchives.write(Map.of("../bad", new byte[0])));
        assertThrows(Exception.class, () -> ShaderArchives.write(Map.of("A", new byte[0], "a", new byte[0])));
        assertThrows(Exception.class, () -> ShaderArchives.write(Map.of("a", new byte[0], "a/b", new byte[0])));
        Path link = root.resolve("shaderpacks"); Files.createSymbolicLink(link, root.resolve("shader-inputs"));
        assertEquals("failed", run(false).state()); assertFalse(Files.exists(root.resolve("shader-inputs/Result.zip")));
    }
    @Test void recoversPublicationIntentAndHonorsBuildLock() throws Exception {
        setup(); run(false);
        Path record = root.resolve(".cache/skyweave-shaders/test.json");
        Files.copy(record, record.resolveSibling("test.pending.json")); Files.delete(record);
        assertEquals("built", run(false).state());
        try (var file = java.nio.channels.FileChannel.open(root.resolve(".cache/skyweave-shaders/build.lock"), StandardOpenOption.WRITE); var lock = file.lock()) {
            assertThrows(java.io.IOException.class, () -> run(false));
        }
    }
    @Test void canonicalInputOrderAndBoundedExpansion() throws Exception {
        var recipe = setup();
        var input = recipe.getAsJsonObject("inputs").get("base").deepCopy();
        recipe.getAsJsonObject("inputs").add("second", input);
        var a = ShaderEngine.prepare(root, ShaderRecipe.parse(recipe.toString()), Map.of());
        var reversed = new JsonObject(); reversed.add("second", input); reversed.add("base", input);
        recipe.add("inputs", reversed);
        assertEquals(a.key(), ShaderEngine.prepare(root, ShaderRecipe.parse(recipe.toString()), Map.of()).key());
        assertThrows(java.io.IOException.class, () -> ShaderRecipe.parse("[".repeat(33) + "]".repeat(33)));
        var step = new JsonObject(); step.addProperty("type", "text"); step.addProperty("path", "shaders/shaders.properties");
        step.addProperty("sha256", ShaderArchives.sha256("old".getBytes())); step.addProperty("find", "o");
        step.addProperty("replace", "x".repeat(ShaderArchives.ENTRY_LIMIT)); step.addProperty("count", 1);
        recipe.getAsJsonArray("steps").add(step);
        // Build directly: encoded recipe-size rejection is a separate boundary.
        assertThrows(java.io.IOException.class, () -> ShaderEngine.build(ShaderEngine.prepare(root, new ShaderRecipe(recipe, "test", "Result.zip", true), Map.of())));
    }

}
