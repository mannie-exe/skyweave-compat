package design.inherent.skyweavecompat.shader;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Pattern;
import java.util.zip.ZipInputStream;

/** Calls the exact bundled ShaderPatcher API without starting the Euphoria mod. */
final class EuphoriaPatch {
    private static final String PIN = "44f11bebc249f59ec71f72b36bd9bb9014f62d3732030cfa9b9c68f3e7ac6638";
    private static final String API = "com.euphoriapatches.shadow.com.euphoriapatches.shaderpatcher.";
    private static final String PATCH_FILE = "/EuphoriaPatches_1.11.1.patch";
    private static final String COMMON = "shaders/lib/common.glsl";
    private static final Pattern STYLE = Pattern.compile("SHADER_STYLE [14]");

    private EuphoriaPatch() {}

    static Map<String, byte[]> apply(Map<String, byte[]> files, byte[] original, byte[] engine, Path work) throws Exception {
        if (!ShaderArchives.sha256(engine).equals(PIN)) throw new IOException("Unreviewed Euphoria patch engine");
        Files.createDirectories(work);
        Path temporary = Files.createTempDirectory(work, "euphoria-");
        try {
            Path jar = temporary.resolve("engine.jar");
            Path base = Files.createDirectory(temporary.resolve("ComplementaryUnbound_r5.10"));
            // Upstream hashes empty directories as well as shader files.
            try (var zip = new ZipInputStream(new ByteArrayInputStream(original))) {
                for (var entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry())
                    if (entry.isDirectory()) Files.createDirectories(base.resolve(ShaderArchives.path(entry.getName().replaceFirst("/$", ""))));
            }
            for (var entry : files.entrySet()) {
                Path destination = base.resolve(ShaderArchives.path(entry.getKey()));
                Files.createDirectories(destination.getParent());
                Files.write(destination, entry.getKey().equals(COMMON) ? style(entry.getValue(), "1") : entry.getValue());
            }
            Files.write(jar, engine);
            try (var loader = new URLClassLoader(new java.net.URL[] {jar.toUri().toURL()}, ClassLoader.getPlatformClassLoader())) {
                Class<?> sourceType = loader.loadClass(API + "PatchSource");
                Class<?> patcherType = loader.loadClass(API + "ShaderPatcher");
                Object source = sourceType.getMethod("classpath", Class.class, String.class).invoke(null, patcherType, PATCH_FILE);
                Object builder = patcherType.getMethod("builder", sourceType).invoke(null, source);
                Object patcher = builder.getClass().getMethod("build").invoke(builder);
                var parent = Files.createDirectory(temporary.resolve("output"));
                Object result;
                try {
                    result = patcherType.getMethod("apply", Path.class, Path.class).invoke(patcher, base, parent);
                } catch (java.lang.reflect.InvocationTargetException error) {
                    throw new IOException("ShaderPatcher refused the base: " + error.getCause().getMessage(), error.getCause());
                }
                Path output = (Path) result.getClass().getMethod("getOutputDir").invoke(result);
                if (!parent.equals(output.getParent()) || !Files.isDirectory(output, LinkOption.NOFOLLOW_LINKS))
                    throw new IOException("ShaderPatcher returned an invalid output directory");
                var patched = readOutput(output);
                patched.put(COMMON, style(patched.get(COMMON), "4"));
                return patched;
            }
        } finally {
            try (var paths = Files.walk(temporary)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
            }
        }
    }

    private static Map<String, byte[]> readOutput(Path output) throws IOException {
        var files = new TreeMap<String, byte[]>();
        int total = 0;
        try (var paths = Files.walk(output)) {
            var entries = paths.iterator();
            while (entries.hasNext()) {
                Path path = entries.next();
                if (Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) continue;
                if (files.size() >= 12000) throw new IOException("Too many Euphoria files");
                String name = ShaderArchives.path(output.relativize(path).toString().replace(java.io.File.separatorChar, '/'));
                byte[] data = ShaderEngine.bounded(path, Math.min(ShaderArchives.TOTAL_LIMIT - total, ShaderArchives.ENTRY_LIMIT));
                total += data.length;
                files.put(name, data);
            }
        }
        return files;
    }

    private static byte[] style(byte[] bytes, String version) throws IOException {
        if (bytes == null) throw new IOException("Missing shader style");
        var matcher = STYLE.matcher(new String(bytes, StandardCharsets.UTF_8));
        if (!matcher.find()) throw new IOException("Changed shader style declaration");
        return matcher.replaceFirst("SHADER_STYLE " + version).getBytes(StandardCharsets.UTF_8);
    }
}
