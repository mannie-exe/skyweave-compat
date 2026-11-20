package design.inherent.skyweavecompat.client;

import design.inherent.skyweavecompat.shader.*;
import java.nio.file.Path;
import java.util.*;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.fml.loading.LoadingModList;

/** File-only startup coordinator. Publication occurs before Iris opens archives, or after reload closes them. */
public final class ShaderAssembly {
    private static List<ShaderWorkspace.Status> states = List.of();
    private static boolean rebuild;
    private ShaderAssembly() {}
    public static synchronized List<ShaderWorkspace.Status> states() { return states; }
    public static synchronized void requestRebuild() { rebuild = true; }
    public static synchronized boolean prepare() {
        String before = selected();
        var versions = new TreeMap<String, String>();
        for (var mod : LoadingModList.get().getMods()) versions.put(mod.getModId(), mod.getVersion().toString());
        try {
            states = ShaderWorkspace.assemble(FMLPaths.GAMEDIR.get(), versions, rebuild, false);
        } catch (Exception failure) {
            states = List.of(new ShaderWorkspace.Status("recipes", "", "failed", String.valueOf(failure.getMessage())));
        } finally { rebuild = false; }
        for (var state : states) {
            var log = com.mojang.logging.LogUtils.getLogger();
            if (state.state().equals("failed")) log.error("Shader recipe {} failed: {}", state.id(), state.detail());
            else log.info("Shader recipe {}: {} {}", state.id(), state.state(), state.output());
        }
        return !java.util.Objects.equals(before, selected());
    }
    private static String selected() {
        try {
            Path path = ShaderEngine.safe(FMLPaths.GAMEDIR.get(), "config/iris.properties");
            if (!java.nio.file.Files.exists(path)) return null;
            var properties = new java.util.Properties();
            properties.load(new java.io.ByteArrayInputStream(ShaderEngine.bounded(path, 1024 * 1024)));
            return properties.getProperty("shaderPack");
        } catch (java.io.IOException failure) { return null; }
    }
    public static synchronized boolean failed(String selected) {
        return states.stream().anyMatch(s -> s.state().equals("failed") && (s.output().isEmpty() || s.output().equals(selected)));
    }
    public static Path euphoriaOutput() {
        Path root = FMLPaths.GAMEDIR.get();
        try {
            for (var recipe : ShaderWorkspace.recipes(root))
                if (recipe.enabled() && recipe.json().getAsJsonArray("steps").asList().stream().anyMatch(s -> s.getAsJsonObject().get("type").getAsString().equals(ShaderEngine.EUPHORIA_STEP))) {
                    Path output = root.resolve("shaderpacks").resolve(recipe.output());
                    if (ShaderWorkspace.owned(root, output)) return output;
                }
        } catch (java.io.IOException ignored) { /* failed recipes remain native detector failures */ }
        return null;
    }
    public static boolean owned(Path path) { return ShaderWorkspace.owned(FMLPaths.GAMEDIR.get(), path); }
}
