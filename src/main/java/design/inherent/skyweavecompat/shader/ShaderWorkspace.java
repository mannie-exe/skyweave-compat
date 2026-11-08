package design.inherent.skyweavecompat.shader;

import com.google.gson.*;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Instance-local ownership, integrity and atomic publication. User presets are never build outputs. */
public final class ShaderWorkspace {
    public record Status(String id, String output, String state, String detail) {}
    private ShaderWorkspace() {}
    public static List<ShaderRecipe> recipes(Path root) throws IOException {
        Path directory = ShaderEngine.safe(root, "config/skyweave/shaders/recipes");
        if (!Files.isDirectory(directory)) return List.of();
        try (var paths = Files.list(directory)) {
            var result = new ArrayList<ShaderRecipe>(); var ids = new HashSet<String>(); var outputs = new HashSet<String>();
            var files = paths.filter(p -> p.getFileName().toString().endsWith(".json")).sorted().toList();
            if (files.size() > 16) throw new IOException("Too many shader recipes");
            for (Path path : files) {
                var recipe = ShaderRecipe.parse(new String(ShaderEngine.bounded(path, 2 * 1024 * 1024), StandardCharsets.UTF_8));
                if (!ids.add(recipe.id()) || !outputs.add(recipe.output().toLowerCase(Locale.ROOT))) throw new IOException("Duplicate recipe identity/output");
                result.add(recipe);
            }
            return List.copyOf(result);
        }
    }
    public static List<Status> assemble(Path root, Map<String, String> environment, boolean force, boolean check) throws IOException {
        var recipes = recipes(root);
        if (recipes.isEmpty()) return List.of();
        Path cache = ShaderEngine.safe(root, ".cache/skyweave-shaders"); Files.createDirectories(cache);
        Path lockPath = ShaderEngine.safe(root, ".cache/skyweave-shaders/build.lock");
        try (var channel = FileChannel.open(lockPath, StandardOpenOption.CREATE, StandardOpenOption.WRITE)) {
            java.nio.channels.FileLock lock;
            try { lock = channel.tryLock(); } catch (java.nio.channels.OverlappingFileLockException e) { lock = null; }
            if (lock == null) throw new IOException("Shader build already running in this instance");
            try (var acquired = lock) {
                var states = new ArrayList<Status>();
                for (var recipe : recipes) {
                    if (!recipe.enabled()) { states.add(new Status(recipe.id(), recipe.output(), "disabled", "")); continue; }
                    try { states.add(assemble(root, cache, recipe, environment, force, check)); }
                    catch (Exception failure) { states.add(new Status(recipe.id(), recipe.output(), "failed", String.valueOf(failure.getMessage()))); }
                }
                return List.copyOf(states);
            }
        }
    }
    private static Status assemble(Path root, Path cache, ShaderRecipe recipe, Map<String, String> environment, boolean force, boolean check) throws IOException {
        var prepared = ShaderEngine.prepare(root, recipe, environment);
        Path record = ShaderEngine.safe(root, ".cache/skyweave-shaders/" + recipe.id() + ".json");
        JsonObject previous = receipt(record);
        Path target = ShaderEngine.safe(root, "shaderpacks/" + recipe.output());
        Path artifact = ShaderEngine.safe(root, ".cache/skyweave-shaders/" + prepared.key() + ".zip");
        boolean same = previous != null && prepared.key().equals(previous.get("key").getAsString()) && recipe.output().equals(previous.get("output").getAsString());
        if (same && valid(target, previous.get("sha256").getAsString()) && !force && !check) return new Status(recipe.id(), recipe.output(), "cached", "");
        Path pending = ShaderEngine.safe(root, ".cache/skyweave-shaders/" + recipe.id() + ".pending.json");
        JsonObject intent = receipt(pending);
        boolean recovery = intent != null && prepared.key().equals(intent.get("key").getAsString()) && recipe.output().equals(intent.get("output").getAsString());
        if (Files.exists(target) && !(previous != null && recipe.output().equals(previous.get("output").getAsString())) && !recovery)
            throw new IOException("Output is not runner-owned: " + recipe.output());
        byte[] bytes;
        boolean reused = same && valid(artifact, previous.get("sha256").getAsString());
        try { bytes = reused && !force && !check ? ShaderEngine.bounded(artifact, ShaderArchives.TOTAL_LIMIT) : ShaderEngine.build(prepared); }
        catch (IOException failure) {
            if (reused && valid(target, previous.get("sha256").getAsString()))
                return new Status(recipe.id(), recipe.output(), "retained", failure.getMessage());
            throw failure;
        }
        if (check) {
            if (!Files.isRegularFile(target) || !Arrays.equals(bytes, ShaderEngine.bounded(target, ShaderArchives.TOTAL_LIMIT)))
                throw new IOException("Generated shader is stale: " + recipe.output());
            return new Status(recipe.id(), recipe.output(), "verified", "");
        }
        String digest = ShaderArchives.sha256(bytes);
        atomic(artifact, bytes);
        var current = new JsonObject(); current.addProperty("schema", 1); current.addProperty("key", prepared.key());
        current.addProperty("output", recipe.output()); current.addProperty("sha256", digest);
        atomic(pending, (current + "\n").getBytes(StandardCharsets.UTF_8));
        Files.createDirectories(target.getParent());
        atomic(target, bytes);
        migrate(root, recipe, previous);
        atomic(record, (current + "\n").getBytes(StandardCharsets.UTF_8));
        Files.deleteIfExists(pending);
        if (previous != null && !recipe.output().equals(previous.get("output").getAsString())) {
            String oldName = ShaderRecipe.filename(previous.get("output").getAsString());
            Path old = ShaderEngine.safe(root, "shaderpacks/" + oldName);
            if (valid(old, previous.get("sha256").getAsString())) Files.delete(old);
        }
        if (previous != null && !prepared.key().equals(previous.get("key").getAsString())) {
            Path retired = ShaderEngine.safe(root, ".cache/skyweave-shaders/" + previous.get("key").getAsString() + ".zip");
            if (valid(retired, previous.get("sha256").getAsString())) Files.delete(retired);
        }
        return new Status(recipe.id(), recipe.output(), reused && !force ? "restored" : "built", "");
    }
    private static void migrate(Path root, ShaderRecipe recipe, JsonObject previous) throws IOException {
        if (previous == null || previous.get("output").getAsString().equals(recipe.output())) return;
        String old = ShaderRecipe.filename(previous.get("output").getAsString());
        JsonObject settings = recipe.json().has("settings") ? recipe.json().getAsJsonObject("settings") : new JsonObject();
        ShaderRecipe.keys(settings, "compatible", "renames", "seedSha256");
        Path source = ShaderEngine.safe(root, "shaderpacks/" + old + ".txt"), destination = ShaderEngine.safe(root, "shaderpacks/" + recipe.output() + ".txt");
        if (settings.has("compatible") && settings.get("compatible").getAsBoolean() && Files.isRegularFile(source)
                && (!Files.exists(destination) || settings.has("seedSha256") && valid(destination, settings.get("seedSha256").getAsString()))) {
            String text = new String(ShaderEngine.bounded(source, 1024 * 1024), StandardCharsets.UTF_8);
            if (settings.has("renames")) for (var rename : settings.getAsJsonObject("renames").entrySet()) {
                if (!rename.getKey().matches("[A-Za-z0-9_]+") || !rename.getValue().getAsString().matches("[A-Za-z0-9_]+")) throw new IOException("Invalid option rename");
                text = text.replaceAll("(?m)^" + rename.getKey() + "(?==)", rename.getValue().getAsString());
            }
            atomic(destination, text.getBytes(StandardCharsets.UTF_8));
        }
        Path iris = ShaderEngine.safe(root, "config/iris.properties");
        if (Files.isRegularFile(iris)) {
            String text = new String(ShaderEngine.bounded(iris, 1024 * 1024), StandardCharsets.UTF_8);
            // Preserve all other lines, including enablement and unrelated selections.
            String changed = text.replaceAll("(?m)^shaderPack=" + java.util.regex.Pattern.quote(old) + "$", java.util.regex.Matcher.quoteReplacement("shaderPack=" + recipe.output()));
            if (!text.equals(changed)) atomic(iris, changed.getBytes(StandardCharsets.UTF_8));
        }
    }
    private static JsonObject receipt(Path path) throws IOException {
        if (!Files.exists(path)) return null;
        try {
            var value = JsonParser.parseString(new String(ShaderEngine.bounded(path, 4096), StandardCharsets.UTF_8)).getAsJsonObject();
            if (value.get("schema").getAsInt() != 1 || !value.get("key").getAsString().matches("[a-f0-9]{64}") || !value.get("sha256").getAsString().matches("[a-f0-9]{64}")) throw new IllegalArgumentException();
            ShaderRecipe.filename(value.get("output").getAsString()); return value;
        } catch (RuntimeException failure) { throw new IOException("Invalid shader ownership receipt", failure); }
    }
    public static boolean owned(Path root, Path target) {
        try {
            for (var recipe : recipes(root)) {
                Path record = ShaderEngine.safe(root, ".cache/skyweave-shaders/" + recipe.id() + ".json");
                var receipt = receipt(record);
                if (recipe.enabled() && receipt != null && target.toAbsolutePath().normalize().equals(root.resolve("shaderpacks/" + receipt.get("output").getAsString()).toAbsolutePath().normalize()) && valid(target, receipt.get("sha256").getAsString())) return true;
            }
        } catch (IOException ignored) { /* no receipt means no authority over a user pack */ }
        return false;
    }
    private static boolean valid(Path path, String digest) throws IOException {
        return Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) && !Files.isSymbolicLink(path) && ShaderArchives.sha256(ShaderEngine.bounded(path, ShaderArchives.TOTAL_LIMIT)).equals(digest);
    }
    static void atomic(Path target, byte[] bytes) throws IOException {
        if (Files.isSymbolicLink(target)) throw new IOException("Symlinked shader target");
        Path temporary = Files.createTempFile(target.getParent(), ".skyweave-", ".tmp");
        try {
            try (var file = FileChannel.open(temporary, StandardOpenOption.WRITE)) { var buffer = java.nio.ByteBuffer.wrap(bytes); while (buffer.hasRemaining()) file.write(buffer); file.force(true); }
            Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally { Files.deleteIfExists(temporary); }
    }
}
