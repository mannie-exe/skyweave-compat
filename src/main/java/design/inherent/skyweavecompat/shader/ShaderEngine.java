package design.inherent.skyweavecompat.shader;

import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** File-only composition shared by the client and authoring CLI. No Minecraft classes. */
public final class ShaderEngine {
    public static final String REVISION = "3";
    public static final String EUPHORIA_STEP = "euphoria-1.11.1";
    public static final String EUPHORIA_VERSION = "1.11.1-r5.10-neoforge";
    public record Prepared(Path root, ShaderRecipe recipe, Map<String, byte[]> inputs, String key) {}
    private ShaderEngine() {}

    public static Path safe(Path root, String relative) throws IOException {
        ShaderArchives.path(relative);
        Path result = root.toAbsolutePath().normalize();
        if (Files.isSymbolicLink(result)) throw new IOException("Symlinked shader root");
        for (String part : relative.split("/")) {
            result = result.resolve(part);
            if (Files.isSymbolicLink(result)) throw new IOException("Symlink in shader path: " + relative);
        }
        return result;
    }
    public static byte[] bounded(Path path, int maximum) throws IOException {
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) || Files.size(path) > maximum)
            throw new IOException("Missing or oversized shader input: " + path.getFileName());
        try (var input = Files.newInputStream(path)) {
            byte[] bytes = input.readNBytes(maximum + 1);
            if (bytes.length > maximum) throw new IOException("Shader input grew beyond limit");
            return bytes;
        }
    }
    public static Prepared prepare(Path root, ShaderRecipe recipe, Map<String, String> environment) throws IOException {
        var required = recipe.json().has("requires") ? recipe.json().getAsJsonObject("requires") : new JsonObject();
        for (var dependency : required.entrySet())
            if (!dependency.getValue().getAsString().equals(environment.get(dependency.getKey())))
                throw new IOException("Recipe requires " + dependency.getKey() + " " + dependency.getValue().getAsString());
        var inputs = new TreeMap<String, byte[]>();
        var identity = new StringBuilder(REVISION).append('\n').append(recipe.canonical());
        for (var input : new TreeMap<>(recipe.json().getAsJsonObject("inputs").asMap()).entrySet()) {
            var definition = input.getValue().getAsJsonObject();
            ShaderRecipe.keys(definition, "path", "sha256");
            String name = ShaderRecipe.string(definition, "path"), expected = ShaderRecipe.string(definition, "sha256");
            if (!(name.startsWith("shader-inputs/") || name.startsWith("mods/")))
                throw new IOException("Input must be in shader-inputs or mods");
            byte[] bytes = bounded(safe(root, name), ShaderArchives.TOTAL_LIMIT);
            String digest = ShaderArchives.sha256(bytes);
            if (!digest.equals(expected)) throw new IOException("Input digest mismatch: " + input.getKey());
            inputs.put(input.getKey(), bytes); identity.append('\n').append(input.getKey()).append(':').append(digest);
        }
        return new Prepared(root, recipe, Map.copyOf(inputs), ShaderArchives.sha256(identity.toString().getBytes(StandardCharsets.UTF_8)));
    }
    public static byte[] build(Prepared prepared) throws IOException {
        var recipe = prepared.recipe();
        var files = new TreeMap<>(ShaderArchives.read(input(prepared, ShaderRecipe.string(recipe.json(), "base"))));
        int index = 0;
        for (var element : recipe.json().getAsJsonArray("steps")) {
            index++;
            try {
                var step = element.getAsJsonObject();
                switch (ShaderRecipe.string(step, "type")) {
                    case "overlay" -> {
                        ShaderRecipe.keys(step, "type", "input", "member", "before");
                        if (!treeHash(files).equals(ShaderRecipe.string(step, "before"))) throw new IOException("Overlay input state changed");
                        byte[] source = input(prepared, ShaderRecipe.string(step, "input"));
                        if (step.has("member")) source = member(source, ShaderRecipe.string(step, "member"));
                        var overlay = ShaderArchives.read(source);
                        if (overlay.keySet().stream().anyMatch(p -> !p.startsWith("shaders/")))
                            throw new IOException("Overlay escapes shader sources");
                        files.putAll(overlay);
                    }
                    case EUPHORIA_STEP -> {
                        ShaderRecipe.keys(step, "type", "input", "before");
                        if (!treeHash(files).equals(ShaderRecipe.string(step, "before"))) throw new IOException("Euphoria input state changed");
                        files = new TreeMap<>(EuphoriaPatch.apply(files,
                            input(prepared, ShaderRecipe.string(recipe.json(), "base")),
                            input(prepared, ShaderRecipe.string(step, "input")), safe(prepared.root(), ".cache/skyweave-shaders/work")));
                    }
                    case "text" -> {
                        ShaderRecipe.keys(step, "type", "path", "sha256", "find", "replace", "count");
                        String path = ShaderArchives.path(ShaderRecipe.string(step, "path"));
                        if (!(path.startsWith("shaders/") || path.equals("skyweave-glass.json") || path.equals("SKYWEAVE.txt")))
                            throw new IOException("Text edit escapes supported shader files");
                        byte[] old = files.getOrDefault(path, new byte[0]);
                        if (!ShaderArchives.sha256(old).equals(ShaderRecipe.string(step, "sha256")))
                            throw new IOException("Text precondition changed: " + path);
                        String text = new String(old, StandardCharsets.UTF_8), find = step.get("find").getAsString();
                        String replacement = step.get("replace").getAsString();
                        if (find.isEmpty()) {
                            if (old.length != 0) throw new IOException("Append requires an absent file");
                            text = replacement;
                        } else {
                            int count = 0, at = 0;
                            while ((at = text.indexOf(find, at)) >= 0) { count++; at += find.length(); }
                            if (count != step.get("count").getAsInt() || count == 0) throw new IOException("Text occurrence precondition changed: " + path);
                            long size = text.length() + (long) count * (replacement.length() - find.length());
                            if (size > ShaderArchives.ENTRY_LIMIT) throw new IOException("Text replacement exceeds limit");
                            text = text.replace(find, replacement);
                        }
                        byte[] value = text.getBytes(StandardCharsets.UTF_8);
                        if (value.length > ShaderArchives.ENTRY_LIMIT) throw new IOException("Text operation exceeds limit");
                        files.put(path, value);
                    }
                    default -> throw new IOException("Unknown patch operation");
                }
            } catch (Exception failure) { throw new IOException(recipe.id() + " step " + index + ": " + failure.getMessage(), failure); }
        }
        if (!files.containsKey("shaders/shaders.properties")) throw new IOException("Result has no shader properties");
        byte[] result = ShaderArchives.write(files);
        if (recipe.json().has("expected") && !treeHash(files).equals(recipe.json().get("expected").getAsString()))
            throw new IOException("Result differs from qualified shader contents");
        return result;
    }
    public static String treeHash(Map<String, byte[]> files) {
        var text = new StringBuilder();
        new TreeMap<>(files).forEach((path, bytes) -> text.append(path).append('\0').append(ShaderArchives.sha256(bytes)).append('\n'));
        return ShaderArchives.sha256(text.toString().getBytes(StandardCharsets.UTF_8));
    }
    static byte[] input(Prepared prepared, String name) throws IOException {
        byte[] bytes = prepared.inputs().get(name);
        if (bytes == null) throw new IOException("Unknown input: " + name);
        return bytes;
    }
    static byte[] member(byte[] zip, String name) throws IOException {
        byte[] result = ShaderArchives.read(zip).get(ShaderArchives.path(name));
        if (result == null) throw new IOException("Missing patch member: " + name);
        return result;
    }
}
