package design.inherent.skyweavecompat.clientprobe;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import design.inherent.skyweavecompat.shader.ShaderArchives;
import design.inherent.skyweavecompat.shader.ShaderEngine;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import net.irisshaders.iris.helpers.StringPair;
import net.irisshaders.iris.pipeline.transform.PatchShaderType;
import net.irisshaders.iris.pipeline.transform.TransformPatcher;
import net.irisshaders.iris.shaderpack.preprocessor.JcppProcessor;

/** Source qualification after real loader initialization; Photon is never selected or submitted to GL. */
public final class ClientShaderStaticProbe {
    private static final Pattern INCLUDE = Pattern.compile("(?m)^\\s*#include\\s+\"([^\"]+)\"[^\\n]*$");
    private boolean finished;
    ClientShaderStaticProbe() {
        var root = net.neoforged.fml.loading.FMLPaths.GAMEDIR.get();
        if (!Files.isRegularFile(root.resolve("rendering-review-fixture.json"))) throw new IllegalStateException("Missing disposable rendering marker");
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.client.event.ClientTickEvent.Post event) -> {
            var mc = net.minecraft.client.Minecraft.getInstance();
            if (finished || mc.getOverlay() != null || !(mc.screen instanceof net.minecraft.client.gui.screens.TitleScreen)) return;
            finished = true;
            try {
                if (net.irisshaders.iris.Iris.isPackInUseQuick()) throw new IllegalStateException("Static fixture requires shaders disabled");
                String name = design.inherent.skyweavecompat.shader.ShaderRecipe.filename(System.getProperty("skyweave_compat.probe.shaderStatic.archive"));
                main(new String[]{root.resolve("shaderpacks/" + name).toString(), root.resolve(".cache/shader-static").toString(),
                        System.getProperty("skyweave_compat.probe.shaderStatic.compiler", "glslangValidator")});
            } catch (Throwable failure) {
                failure.printStackTrace();
                try { Files.createDirectories(root.resolve(".cache/shader-static")); Files.writeString(root.resolve(".cache/shader-static/failure.txt"), failure.toString()); }
                catch (Exception writeFailure) { failure.addSuppressed(writeFailure); }
            } finally { mc.stop(); }
        });
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 3) throw new IllegalArgumentException("Expected archive, owned output and glslang executable");
        Path archive = Path.of(args[0]), output = Path.of(args[1]);
        byte[] bytes = ShaderEngine.bounded(archive, ShaderArchives.TOTAL_LIMIT);
        var files = ShaderArchives.read(bytes); Files.createDirectories(output);
        var report = new JsonObject(); var cases = new JsonArray(); report.add("cases", cases);
        report.addProperty("archive", archive.getFileName().toString()); report.addProperty("sha256", ShaderArchives.sha256(bytes));
        report.addProperty("scope", "Source preprocessing, exact Iris DH AST transform and glslang compile/link only; no GPU/native image or timing qualification");
        var defines = defines();
        var environment = new JsonObject();
        for (var define : defines) if (!define.key().startsWith("MC_GL_EXT_")) environment.addProperty(define.key(), define.value());
        report.add("environment", environment);
        for (String dimension : List.of("world0", "world1", "world-1")) for (boolean caustics : List.of(true, false)) {
            String id = dimension.replace('-', 'n') + (caustics ? "-caustics-on" : "-caustics-off");
            String vertex = expand(files, "shaders/" + dimension + "/dh_shadow.vsh", new HashSet<>(), new int[]{0});
            String fragment = expand(files, "shaders/" + dimension + "/dh_shadow.fsh", new HashSet<>(), new int[]{0});
            vertex = option(vertex, "WATER_CAUSTICS", caustics); fragment = option(fragment, "WATER_CAUSTICS", caustics);
            vertex = JcppProcessor.glslPreprocessSource(vertex, defines);
            fragment = JcppProcessor.glslPreprocessSource(fragment, defines);
            var transformed = TransformPatcher.patchDHTerrain("skyweave-static-" + id, vertex, null, null, null, fragment,
                    new Object2ObjectOpenHashMap<>());
            Path v = output.resolve(id + ".vert"), f = output.resolve(id + ".frag");
            Files.writeString(v, transformed.get(PatchShaderType.VERTEX)); Files.writeString(f, transformed.get(PatchShaderType.FRAGMENT));
            var process = new ProcessBuilder(args[2], "-l", v.toString(), f.toString()).redirectErrorStream(true)
                    .redirectOutput(output.resolve(id + ".log").toFile()).start();
            if (!process.waitFor(30, TimeUnit.SECONDS)) { process.destroyForcibly(); throw new IllegalStateException("GLSL compiler timeout"); }
            var row = new JsonObject(); row.addProperty("case", id); row.addProperty("exit", process.exitValue());
            row.addProperty("vertexSha256", ShaderArchives.sha256(Files.readAllBytes(v)));
            row.addProperty("fragmentSha256", ShaderArchives.sha256(Files.readAllBytes(f))); cases.add(row);
            Files.writeString(output.resolve("results.json"), new GsonBuilder().setPrettyPrinting().create().toJson(report) + "\n");
            if (process.exitValue() != 0) throw new IllegalStateException("GLSL compile/link failed: " + id + "\n" + Files.readString(output.resolve(id + ".log")));
        }
        report.addProperty("complete", true);
        Files.writeString(output.resolve("results.json"), new GsonBuilder().setPrettyPrinting().create().toJson(report) + "\n");
        System.out.println("Static DH shadow variants compiled/linked: " + cases.size());
    }

    private static String option(String source, String option, boolean enabled) {
        return source.replaceAll("(?m)^[ \\t]*(?://[ \\t]*)?#define[ \\t]+" + option + "(?:[ \\t]+[^\\r\\n]*)?[ \\t]*\\r?$",
                enabled ? "#define " + option : "// fixture: " + option + " disabled");
    }

    private static List<StringPair> defines() {
        var macros = new java.util.LinkedHashMap<String, String>();
        for (var define : net.irisshaders.iris.gl.shader.StandardMacros.createStandardEnvironmentDefines())
            macros.put(define.key(), define.value());
        macros.put("DISTANT_HORIZONS", "1"); macros.putIfAbsent("MC_SHADOW_QUALITY", "1.0");
        for (var material : com.seibel.distanthorizons.api.enums.rendering.EDhApiBlockMaterial.values())
            macros.put("DH_BLOCK_" + material.name(), Byte.toString(material.index));
        return macros.entrySet().stream().map(pair -> new StringPair(pair.getKey(), pair.getValue())).toList();
    }

    private static String expand(Map<String, byte[]> files, String path, Set<String> stack, int[] size) throws Exception {
        ShaderArchives.path(path);
        if (stack.size() >= 64 || !stack.add(path)) throw new IllegalArgumentException("Recursive/deep shader include: " + path);
        try {
            byte[] bytes = files.get(path); if (bytes == null) throw new IllegalArgumentException("Missing shader include: " + path);
            String source = new String(bytes, StandardCharsets.UTF_8); var matcher = INCLUDE.matcher(source); var output = new StringBuilder();
            while (matcher.find()) {
                String name = matcher.group(1);
                String resolved = name.startsWith("/") ? "shaders" + name : path.substring(0, path.lastIndexOf('/') + 1) + name;
                matcher.appendReplacement(output, java.util.regex.Matcher.quoteReplacement(expand(files, resolved, stack, size)));
            }
            matcher.appendTail(output);
            if ((size[0] += output.length()) > 8 * 1024 * 1024) throw new IllegalArgumentException("Shader include expansion exceeds fixture budget");
            return output.toString();
        } finally { stack.remove(path); }
    }
}
