/* SPDX-License-Identifier: Apache-2.0 */
package design.inherent.skyweavecompat.clientprobe;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import design.inherent.skyweavecompat.shader.GrassriseRecipes;
import dev.grassrise.betterlighting.shader.RuntimeGate;
import dev.grassrise.betterlighting.shader.ShaderPackAdapter;
import dev.grassrise.betterlighting.shader.ShaderTargetIdCache;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import java.nio.file.Files;
import net.irisshaders.iris.Iris;
import net.irisshaders.iris.gl.blending.AlphaTest;
import net.irisshaders.iris.pipeline.transform.PatchShaderType;
import net.irisshaders.iris.pipeline.transform.TransformPatcher;
import net.irisshaders.iris.shaderpack.loading.ProgramId;
import net.irisshaders.iris.shaderpack.materialmap.NamespacedId;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;

/** Native Iris file/preprocessor/AST and Grassrise activation, without a driver-rendering claim. */
final class ClientGrassriseProbe {
    private boolean done;
    ClientGrassriseProbe(){NeoForge.EVENT_BUS.addListener(this::tick);}
    private void tick(ClientTickEvent.Post event) {
        var mc=Minecraft.getInstance();if(done || mc.getOverlay()!=null || !(mc.screen instanceof TitleScreen))return;
        done=true;var report=new JsonObject();var families=new JsonArray();report.add("families",families);
        try {
            require(Files.isRegularFile(FMLPaths.GAMEDIR.get().resolve("grassrise-fixture.json")),"Missing fixture marker");
            if(Boolean.getBoolean("skyweave_compat.probe.grassrise.cold")) {
                Files.delete(FMLPaths.GAMEDIR.get().resolve("shaderpacks/BSL_v10.1.8 + Clrwl_1.0.5 + Skyweave_0.3.1.zip"));
                Files.delete(FMLPaths.GAMEDIR.get().resolve(".cache/skyweave-shaders/bsl-colorwheel.json"));
                report.addProperty("coldRecipePublication",true);
            }
            for(String[] selection:new String[][]{
                {"BSL_v10.1.8 + Clrwl_1.0.5 + Skyweave_0.3.1.zip","bsl"},
                {"Complementary Unbound r5.10 + Euphoria 1.11.2 + Skyweave 0.4.2.zip","complementary-unbound"},
                {"Photon v1.4 + Skyweave 0.3.1.zip","photon"}}) {
                var row=new JsonObject();row.addProperty("name",selection[0]);families.add(row);
                Iris.getIrisConfig().setShaderPackName(selection[0]);Iris.getIrisConfig().setShadersEnabled(true);Iris.getIrisConfig().save();Iris.reload();
                if(Boolean.getBoolean("skyweave_compat.probe.grassrise.disabled")) {
                    require(GrassriseRecipes.family(selection[0])==null && RuntimeGate.prepareForShaderLoad()==null
                        && !RuntimeGate.shouldMarkGrassriseGeometry(),"Explicit false must retain native unsupported fallback");
                    row.addProperty("disabledNativeFallback",true);
                    Iris.getIrisConfig().setShadersEnabled(false);Iris.getIrisConfig().save();Iris.reload();continue;
                }
                require(selection[1].equals(GrassriseRecipes.family(selection[0])),"Verify actual archive/recipe/receipt identity");
                var adapter=RuntimeGate.prepareForShaderLoad();require(adapter!=null && selection[1].equals(adapter.familyId()),"Native adapter classification");
                require(!RuntimeGate.shouldMarkGrassriseGeometry(),"No marker before successful source transform");
                var refused=java.util.Map.of(PatchShaderType.VERTEX,"void main() {}",PatchShaderType.FRAGMENT,"void main() {}");
                require(dev.grassrise.betterlighting.shader.ShadowProgramPatcher.transform("shadow",refused,
                    AlphaTest.ALWAYS,new Object2ObjectOpenHashMap<>(),true)==refused
                    && !RuntimeGate.shouldMarkGrassriseGeometry(),"Failed native transform must not activate markers");
                row.addProperty("failedTransformNotActive",true);
                var pack=Iris.getCurrentPack().orElseThrow();
                var program=pack.getProgramSet(new NamespacedId("minecraft:overworld")).get(ProgramId.Shadow).orElseThrow();
                var output=TransformPatcher.patchSodium("shadow",program.getVertexSource().orElseThrow(),
                    program.getGeometrySource().orElse(null),program.getTessControlSource().orElse(null),
                    program.getTessEvalSource().orElse(null),program.getFragmentSource().orElseThrow(),AlphaTest.ALWAYS,new Object2ObjectOpenHashMap<>());
                require(output.get(PatchShaderType.VERTEX).contains("grassrise_NoShadowsMarker"),"Actual Iris transformed marker");
                require(output.get(PatchShaderType.VERTEX).contains("0x7fffffff"),"Native material decoder masking");
                require(output.get(PatchShaderType.FRAGMENT).contains("discard"),"Native marked shadow exclusion");
                require(RuntimeGate.shouldMarkGrassriseGeometry(),"Successful native transform admits marker emission");
                if(selection[1].equals("photon")) {
                    var ids=net.irisshaders.iris.shaderpack.materialmap.BlockMaterialMapping.createBlockStateIdMap(
                        pack.getIdMap().getBlockProperties(),pack.getIdMap().getTagEntries());
                    int grass=ids.getOrDefault(net.minecraft.world.level.block.Blocks.GRASS_BLOCK.defaultBlockState(),-1);
                    require(grass==-1 && ShaderTargetIdCache.getBladePayload(grass)==0,"Exact native Photon grass/default material");
                    require(ShaderTargetIdCache.getBladePayload(10132)==10132,"Mapped native materials remain unchanged");
                    row.addProperty("nativeGrassBlockId",grass);
                    row.addProperty("photonDefaultGrassMaterial",true);
                }
                row.addProperty("nativeTransformAndActivation",true);
                Iris.reload();require(!RuntimeGate.shouldMarkGrassriseGeometry(),"Same-family reload clears previous activation");
                Iris.getIrisConfig().setShadersEnabled(false);Iris.getIrisConfig().save();Iris.reload();
                require(GrassriseRecipes.family(selection[0])==null && !RuntimeGate.shouldMarkGrassriseGeometry(),"Disable clears owned identity and activation");
                row.addProperty("reloadAndDisable",true);
            }
            require(ShaderPackAdapter.byFileName("unrelated + Skyweave.zip")==null,"Unknown output remains native unsupported");
            report.addProperty("passed",true);
            report.addProperty("scope","Native Iris generated files, preprocessing and Sodium AST transform with original Grassrise guards; no GL shader link, rendered Sable blades, visual/performance or Photon GPU claim");
        }catch(Throwable failure){failure.printStackTrace();report.addProperty("passed",false);report.addProperty("error",failure.toString());}
        finally {
            try{Files.writeString(FMLPaths.GAMEDIR.get().resolve("grassrise-proof.json"),report.toString());}
            catch(Exception failure){throw new IllegalStateException(failure);}mc.stop();
        }
    }
    private static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
}
