package design.inherent.skyweavecompat.clientprobe;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import com.seibel.distanthorizons.api.DhApi;
import com.seibel.distanthorizons.api.enums.rendering.EDhApiRendererMode;
import com.seibel.distanthorizons.api.methods.events.DhApiEventRegister;
import com.seibel.distanthorizons.api.methods.events.abstractEvents.DhApiBeforeBufferRenderEvent;
import com.seibel.distanthorizons.api.methods.events.sharedParameterObjects.DhApiEventParam;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.irisshaders.iris.Iris;
import net.irisshaders.iris.shadows.ShadowRenderer;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.FlatLevelSource;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.flat.FlatLevelGeneratorSettings;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;

/** Equal-material plane and height controls; isolated diagnostics, never part of the product JAR. */
final class ClientShadowRingProbe {
    private static final String WORLD="shadow-ring-review";
    private static final List<String> STAGES=List.of("current-low","current-mid","current-high","current-above-range",
            "dh-shadow-off-high","dh-off-high","shader-off-high");
    private final Path root=FMLPaths.GAMEDIR.get();
    private final String corrected=System.getProperty("skyweave_compat.probe.shadowRing.shader");
    private final String noDhShadow=System.getProperty("skyweave_compat.probe.shadowRing.noDhShadow");
    private final boolean followup=Boolean.getBoolean("skyweave_compat.probe.shadowRing.followup");
    private final boolean variantOnly=Boolean.getBoolean("skyweave_compat.probe.shadowRing.variantOnly");
    private final List<String> matrix=matrix();
    private final List<String> stages=matrix!=null?java.util.stream.IntStream.range(0,matrix.size()).mapToObj(i->"variant-"+i).toList():followup?List.of("current-low","current-mid","current-high","current-above-range",
            "candidate-high","candidate-occluder","reference-occluder","candidate-range2","reference-range2",
            "dh-off-occluder","shader-off-occluder"):STAGES;
    private final JsonObject report=new JsonObject();
    private final JsonArray scenes=new JsonArray();
    private final ClientFrameTiming timing=new ClientFrameTiming();
    private CompletableFuture<?> pending;
    private boolean opened,prepared,stopped;
    private int tick,stage=-1,stageTick;
    private long buffers,shadowBuffers;
    private String shadowFrustum;

    ClientShadowRingProbe() {
        if(!Files.isRegularFile(root.resolve("rendering-review-fixture.json")))throw new IllegalStateException("Missing disposable rendering marker");
        var archives=new java.util.ArrayList<String>();archives.add(corrected);archives.add(noDhShadow);
        if(followup)archives.add(System.getProperty("skyweave_compat.probe.shadowRing.control"));
        if(matrix!=null){archives.clear();archives.addAll(matrix);archives.add(corrected);}
        for(String name:archives){
            if(name==null || !(name.startsWith("Complementary ") || name.startsWith("BSL_")))
                throw new IllegalArgumentException("Expected explicit supported shader archive: "+name);
            try{design.inherent.skyweavecompat.shader.ShaderRecipe.filename(name);}
            catch(java.io.IOException failure){throw new IllegalArgumentException("Invalid shader archive: "+name,failure);}
            if(!Files.isRegularFile(root.resolve("shaderpacks").resolve(name)))throw new IllegalArgumentException("Missing shader archive: "+name);
        }
        report.add("scenes",scenes);
        if(matrix!=null)report.add("matrix",new com.google.gson.Gson().toJsonTree(matrix));
        report.addProperty("variantOnly",variantOnly);
        report.addProperty("scope","Native flat smooth-stone top Y80; fixed sun/weather and empty hands; altitude, cost and low-main-render-distance controls. Low main range does not itself isolate DH shadows.");
        DhApiEventRegister.on(DhApiBeforeBufferRenderEvent.class,new DhApiBeforeBufferRenderEvent(){
            @Override public void beforeRender(DhApiEventParam<EventParam> event){buffers++;if(ShadowRenderer.ACTIVE){shadowBuffers++;if(shadowFrustum==null && ShadowRenderer.FRUSTUM!=null)shadowFrustum=ShadowRenderer.FRUSTUM.getClass().getName();}}
        });
        NeoForge.EVENT_BUS.addListener(this::tick);
    }

    private void tick(ClientTickEvent.Post event) {
        var mc=Minecraft.getInstance();
        if(stopped || mc.getOverlay()!=null)return;
        try {
            if(!opened && mc.screen instanceof TitleScreen) {
                opened=true;mc.options.pauseOnLostFocus=false;mc.options.hideGui=true;
                mc.options.renderDistance().set(12);mc.options.simulationDistance().set(4);
                mc.options.fov().set(70);mc.options.framerateLimit().set(260);mc.options.enableVsync().set(false);
                org.lwjgl.glfw.GLFW.glfwFocusWindow(mc.getWindow().getWindow());
                mc.options.setCameraType(CameraType.FIRST_PERSON);
                if(Files.exists(root.resolve("saves/"+WORLD)))throw new IllegalStateException("Expected fresh disposable flat world");
                select(corrected);
                mc.createWorldOpenFlows().createFreshLevel(WORLD,
                        new LevelSettings("Shadow ring review",GameType.CREATIVE,false,Difficulty.PEACEFUL,true,new GameRules(),WorldDataConfiguration.DEFAULT),
                        new WorldOptions(42,false,false),registries->{
                            var dimensions=registries.registryOrThrow(Registries.WORLD_PRESET).get(ResourceLocation.withDefaultNamespace("flat")).createWorldDimensions();
                            var json=JsonParser.parseString("{\"biome\":\"minecraft:plains\",\"lakes\":false,\"features\":false,\"structure_overrides\":[],\"layers\":[{\"block\":\"minecraft:stone\",\"height\":144},{\"block\":\"minecraft:smooth_stone\",\"height\":1}]}");
                            var settings=FlatLevelGeneratorSettings.CODEC.parse(RegistryOps.create(JsonOps.INSTANCE,registries),json).getOrThrow();
                            return dimensions.replaceOverworldGenerator(registries,new FlatLevelSource(settings));
                        },mc.screen);return;
            }
            if(mc.player==null || mc.level==null || mc.getSingleplayerServer()==null)return;
            if(pending!=null){if(!pending.isDone())return;pending.join();pending=null;}
            mc.setScreen(null);mc.player.setYRot(0);mc.player.yRotO=0;mc.player.setXRot(62);mc.player.xRotO=62;
            if(!prepared) {
                prepared=true;Files.writeString(root.resolve("saves/"+WORLD+"/.rendering-review-world"),"owned shadow-ring fixture\n");
                report.addProperty("gpu",org.lwjgl.opengl.GL11.glGetString(org.lwjgl.opengl.GL11.GL_RENDERER));
                report.addProperty("shader",corrected);report.addProperty("noDhShadow",noDhShadow);
                DhApi.Delayed.configs.worldGenerator().enableDistantWorldGeneration().setValue(false);
                DhApi.Delayed.configs.graphics().renderingEnabled().setValue(true);
                DhApi.Delayed.configs.graphics().renderingMode().setValue(EDhApiRendererMode.DEFAULT);
                server(player->{var level=player.serverLevel();player.setGameMode(GameType.SPECTATOR);player.getInventory().clearContent();
                    for(String rule:List.of("doDaylightCycle","doWeatherCycle","doMobSpawning"))
                        player.getServer().getCommands().performPrefixedCommand(player.createCommandSourceStack().withPermission(4),"gamerule "+rule+" false");
                    level.setDayTime(4500);level.setWeatherParameters(6000,0,false,false);player.teleportTo(.5,145,.5);});
                return;
            }
            tick++;
            if(stage<0) {
                if(tick<=360 && tick%40==0){int cell=tick/40-1;server(player->player.teleportTo((cell%3-1)*192+.5,145,(cell/3-1)*192+.5));}
                if(tick==400)server(player->player.teleportTo(.5,177,.5));
                if(tick>=520){stage=followup && variantOnly?4:0;prepareStage();}return;
            }
            stageTick++;
            if(stageTick==50){buffers=shadowBuffers=0;timing.begin(stages.get(stage));}
            if(stageTick==150) {
                if((matrix!=null || stage<stages.size()-1) && !Iris.isPackInUseQuick())throw new IllegalStateException("Expected active shader for "+stages.get(stage));
                if(matrix!=null && !matrix.get(stage).equals(Iris.getCurrentPackName()))throw new IllegalStateException("Unexpected matrix shader: "+Iris.getCurrentPackName());
                var row=timing.finish();row.addProperty("stage",stages.get(stage));row.addProperty("position",mc.player.position().toString());
                row.addProperty("eye",mc.gameRenderer.getMainCamera().getPosition().toString());
                row.addProperty("shaderEnabled",Iris.isPackInUseQuick());row.addProperty("renderDistance",mc.options.getEffectiveRenderDistance());
                row.addProperty("loadedShader",Iris.getCurrentPackName());
                row.addProperty("windowFocused",org.lwjgl.glfw.GLFW.glfwGetWindowAttrib(mc.getWindow().getWindow(),org.lwjgl.glfw.GLFW.GLFW_FOCUSED)!=0);
                row.addProperty("dhEnabled",DhApi.Delayed.configs.graphics().renderingEnabled().getValue());
                row.addProperty("dhBuffers",buffers);row.addProperty("dhShadowBuffers",shadowBuffers);
                row.addProperty("shadowFrustum",shadowFrustum);
                row.addProperty("shadowDistance",ShadowRenderer.renderDistance);row.addProperty("shadowResolution",ShadowRenderer.RESOLUTION);
                row.addProperty("sections",mc.levelRenderer.getSectionStatistics());row.addProperty("glError",org.lwjgl.opengl.GL11.glGetError());
                scenes.add(row);save();
                Screenshot.grab(mc.gameDirectory,"shadow-ring-"+stages.get(stage)+".png",mc.getMainRenderTarget(),ignored->{});
            }
            if(stageTick>=180){if(++stage==stages.size()){report.addProperty("complete",true);stop();}else prepareStage();}
        } catch(Throwable failure){failure.printStackTrace();report.addProperty("failure",failure.toString());stop();}
    }
    private void prepareStage()throws Exception {
        stageTick=0;
        shadowFrustum=null;
        if(matrix!=null){select(matrix.get(stage));server(player->player.teleportTo(.5,177,.5));return;}
        String name=stages.get(stage);
        if(stage==4 || name.equals("candidate-range2"))select(noDhShadow);
        if(name.equals("dh-off-occluder") || name.equals("dh-off-high"))DhApi.Delayed.configs.graphics().renderingEnabled().setValue(false);
        if(stage==stages.size()-1){Iris.getIrisConfig().setShadersEnabled(false);Iris.getIrisConfig().save();Iris.reload();}
        if(followup && stage==5)server(player->{
            for(int x=-12;x<=12;x++)for(int z=24;z<=40;z++)
                player.serverLevel().setBlock(new net.minecraft.core.BlockPos(x,96,z),net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(),2);
            // Bake the remote roof while it is still natively visible; later drop to two chunks.
            for(int x=-8;x<=8;x++)for(int z=64;z<=88;z++)
                player.serverLevel().setBlock(new net.minecraft.core.BlockPos(x,100,z),net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(),2);
        });
        if(name.equals("reference-occluder") || name.equals("reference-range2"))select(System.getProperty("skyweave_compat.probe.shadowRing.control"));
        if(name.equals("candidate-range2"))Minecraft.getInstance().options.renderDistance().set(2);
        int height=followup && stage>=7?64:switch(stage){case 0->16;case 1->64;case 3->128;default->96;};
        server(player->player.teleportTo(.5,81+height,.5));
    }
    private static List<String> matrix(){
        String text=System.getProperty("skyweave_compat.probe.shadowRing.matrix");if(text==null)return null;
        if(text.length()>8192)throw new IllegalArgumentException("Shadow matrix exceeds fixture budget");
        var files=new java.util.ArrayList<String>();for(var name:JsonParser.parseString(text).getAsJsonArray())files.add(name.getAsString());
        if(files.size()<2 || files.size()>8)throw new IllegalArgumentException("Expected two to eight shadow variants");
        return List.copyOf(files);
    }
    private void select(String shader)throws Exception{var config=Iris.getIrisConfig();config.setShaderPackName(shader);config.setShadersEnabled(true);config.save();Iris.reload();}
    private void server(java.util.function.Consumer<ServerPlayer> action){var mc=Minecraft.getInstance();var server=mc.getSingleplayerServer();var id=mc.player.getUUID();pending=server.submit(()->action.accept(server.getPlayerList().getPlayer(id)));}
    private void save()throws Exception{ProbeStateFiles.write(root.resolve("shadow-ring-results.json"),new GsonBuilder().setPrettyPrinting().create().toJson(report));}
    private void stop(){stopped=true;timing.close();try{save();}catch(Exception failure){throw new IllegalStateException(failure);}Minecraft.getInstance().stop();}
}
