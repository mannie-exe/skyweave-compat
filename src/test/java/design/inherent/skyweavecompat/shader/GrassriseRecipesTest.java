package design.inherent.skyweavecompat.shader;

import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class GrassriseRecipesTest {
    @TempDir Path root;
    @Test void filenameAndSelfAuthoredReceiptCannotAuthorizeDifferentContent() throws Exception {
        var recipe=JsonParser.parseString("""
            {"schema":1,"id":"bsl-colorwheel","version":"test","output":"BSL generated.zip","base":"bsl",
             "inputs":{"bsl":{"path":"shader-inputs/base.zip","sha256":"36b0a50ff7918bf10e9422c401d93779f9d0088a26acea975b435243f96930b5"}},
             "steps":[],"expected":"d4ed85a28edf75b4223da037eae9504823094e4d99fa9a16513cabdc8e189f9c"}
            """).getAsJsonObject();
        Files.createDirectories(root.resolve("config/skyweave/shaders/recipes"));
        Files.writeString(root.resolve("config/skyweave/shaders/recipes/bsl.json"),recipe.toString());
        assertNull(GrassriseRecipes.resolve(root,"BSL generated.zip"));
        Files.createDirectories(root.resolve(".cache/skyweave-shaders"));
        Files.createDirectories(root.resolve("shaderpacks"));
        var receipt=JsonParser.parseString("""
            {"schema":1,"key":"stale","output":"BSL generated.zip","sha256":"563d949bfa4f4aab1417a5dd320544193357b4476cd6aa34ed63ba3af824610f"}
            """).getAsJsonObject();
        receipt.addProperty("key",ShaderEngine.recipeKey(ShaderRecipe.parse(recipe.toString())));
        Files.writeString(root.resolve(".cache/skyweave-shaders/bsl-colorwheel.json"),receipt.toString());
        Files.writeString(root.resolve("shaderpacks/BSL generated.zip"),"not the qualified shader");
        assertNull(GrassriseRecipes.resolve(root,"BSL generated.zip"));
        recipe.addProperty("enabled",false);
        Files.writeString(root.resolve("config/skyweave/shaders/recipes/bsl.json"),recipe.toString());
        assertNull(GrassriseRecipes.resolve(root,"BSL generated.zip"));
        assertThrows(java.io.IOException.class,()->GrassriseRecipes.resolve(root,"../shader.zip"));
    }
    @Test void reloadRefusalAndDisableClearRetainedIdentity() {
        GrassriseRecipes.load(root,"unknown.zip");assertNull(GrassriseRecipes.family("unknown.zip"));
        GrassriseRecipes.load(root,"");assertNull(GrassriseRecipes.family("unknown.zip"));
        GrassriseRecipes.clear();assertNull(GrassriseRecipes.family(null));
    }
}
