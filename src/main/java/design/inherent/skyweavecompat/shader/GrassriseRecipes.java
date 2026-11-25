/* SPDX-License-Identifier: Apache-2.0 */
package design.inherent.skyweavecompat.shader;

import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/** Verified generated identities for one shader-load lifetime; never called from quad encoding. */
public final class GrassriseRecipes {
    private record Release(String family, String base, String payload, String output) {}
    private static final Map<String, Release> RELEASES = Map.of(
        "bsl-colorwheel", new Release("bsl", "36b0a50ff7918bf10e9422c401d93779f9d0088a26acea975b435243f96930b5", "d4ed85a28edf75b4223da037eae9504823094e4d99fa9a16513cabdc8e189f9c", "563d949bfa4f4aab1417a5dd320544193357b4476cd6aa34ed63ba3af824610f"),
        "complementary-euphoria-skyweave", new Release("complementary-unbound", "f19bf3268669576485124b7a717980f46b6200cde146fa181e794a2df0f9c2da", "3fc3e1486ad6a6719b0d8bc020cc17b226ca629804e3539f55767d19ffe8c1e9", "7536dbc454cbdba83e7266b25bfe5d234ae46d5b91f31dd43660a8c22985a1a1"),
        "photon-skyweave", new Release("photon", "da0929b38f5fd99befaaa8cd141cbf60e1fad05b92d916d0eeeee7c79f86c0fd", "c582385a20b84b7018cee33bad18ce56331053fdf32b0ac8372581f5d128bef6", "89a23e660c8e40348359f4a02441afb47f969641184f4bac651455861b6c943e"));
    private record Admission(String name, String family) {}
    private static volatile Admission current;
    private GrassriseRecipes() {}

    public static void clear() { current = null; }
    public static String family(String name) {
        var admitted = current;
        return admitted != null && admitted.name.equals(name) ? admitted.family : null;
    }
    public static void load(Path root, String selected) {
        clear();
        try {
            String family = resolve(root, selected);
            if (family != null) current = new Admission(selected, family);
        } catch (IOException | RuntimeException failure) {
            com.mojang.logging.LogUtils.getLogger().warn("Grassrise generated shader admission unavailable: {}", failure.getMessage());
        }
    }

    static String resolve(Path root, String selected) throws IOException {
        if (selected == null || selected.isBlank()) return null;
        ShaderRecipe.filename(selected);
        for (var recipe : ShaderWorkspace.recipes(root)) {
            var release = RELEASES.get(recipe.id());
            if (!recipe.enabled() || release == null || !recipe.output().equals(selected)) continue;
            var definition = recipe.json().getAsJsonObject("inputs").getAsJsonObject(ShaderRecipe.string(recipe.json(), "base"));
            if (definition == null || !release.base.equals(ShaderRecipe.string(definition, "sha256"))
                    || !release.payload.equals(ShaderRecipe.string(recipe.json(), "expected"))) return null;
            Path record = ShaderEngine.safe(root, ".cache/skyweave-shaders/" + recipe.id() + ".json");
            if (!Files.isRegularFile(record)) return null;
            var receipt = JsonParser.parseString(new String(ShaderEngine.bounded(record, 8192), StandardCharsets.UTF_8)).getAsJsonObject();
            if (receipt.get("schema").getAsInt() != 1 || !selected.equals(ShaderRecipe.string(receipt, "output"))
                    || !ShaderEngine.recipeKey(recipe).equals(ShaderRecipe.string(receipt, "key"))
                    || !release.output.equals(ShaderRecipe.string(receipt, "sha256"))) return null;
            if (!release.output.equals(ShaderArchives.sha256(ShaderEngine.bounded(
                    ShaderEngine.safe(root, "shaderpacks/" + selected), ShaderArchives.TOTAL_LIMIT)))) return null;
            return release.family;
        }
        return null;
    }
}
