package design.inherent.skyweavecompat.clientprobe;

import com.google.gson.*;
import design.inherent.skyweavecompat.terrain.structure.VillageComposition;
import java.nio.file.*;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.*;
import net.minecraft.world.level.levelgen.flat.*;
import net.minecraft.world.level.levelgen.structure.*;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Bounded full-pack architectural showcase; terrain/support qualification belongs to headless native fixtures. */
final class ClientVillageProbe {
    private record Scene(int index,String id,BoundingBox bounds,BlockPos court,JsonObject metadata) {}
    private final Path root=FMLPaths.GAMEDIR.get().toAbsolutePath().normalize();
    private final JsonObject report=new JsonObject();private final JsonArray samples=new JsonArray();
    private volatile Scene scene;private volatile Throwable serverFailure;private volatile int requested;
    private boolean opened,finished;private int ticks,wait,view,building=-1,chunkIndex;
    private volatile List<ResourceLocation> ids;private List<ChunkPos> chunks;private StructureStart start;private long generationSeed;
    ClientVillageProbe() {
        if(!Files.isRegularFile(root.resolve("rendering-review-fixture.json")))throw new IllegalStateException("Missing owned village fixture marker");
        report.add("samples",samples);report.addProperty("scope","Manual native assembly/placement on a deep flat world for architectural/resource review; not natural island admission, density grading, rarity or whole-village navigation");
        NeoForge.EVENT_BUS.addListener(this::clientTick);NeoForge.EVENT_BUS.addListener(this::serverTick);
    }
    private void clientTick(ClientTickEvent.Post event) {
        if(finished)return;var mc=Minecraft.getInstance();
        try {
            if(++ticks>12000)throw new AssertionError("Village preview tick budget exhausted");
            if(serverFailure!=null)throw new AssertionError("Village showcase failed",serverFailure);
            if(!opened && mc.screen instanceof TitleScreen && mc.getOverlay()==null) {
                opened=true;mc.options.renderDistance().set(12);mc.options.hideGui=true;
                mc.createWorldOpenFlows().createFreshLevel("village-composition-review",
                        new LevelSettings("Village composition review",GameType.CREATIVE,false,Difficulty.PEACEFUL,true,new GameRules(),WorldDataConfiguration.DEFAULT),
                        new WorldOptions(42,false,false),registries->{
                            var flat=FlatLevelGeneratorSettings.getDefault(registries.registryOrThrow(Registries.BIOME).asLookup(),
                                    registries.registryOrThrow(Registries.STRUCTURE_SET).asLookup(),registries.registryOrThrow(Registries.PLACED_FEATURE).asLookup());
                            flat.getLayersInfo().clear();flat.getLayersInfo().addAll(List.of(new FlatLayerInfo(1,Blocks.BEDROCK),
                                    new FlatLayerInfo(96,Blocks.STONE),new FlatLayerInfo(3,Blocks.DIRT),new FlatLayerInfo(1,Blocks.GRASS_BLOCK)));flat.updateLayers();
                            return registries.registryOrThrow(Registries.WORLD_PRESET).get(ResourceLocation.parse("minecraft:flat"))
                                    .createWorldDimensions().replaceOverworldGenerator(registries,new FlatLevelSource(flat));
                        },mc.screen);return;
            }
            if(mc.player==null || mc.level==null || mc.getOverlay()!=null)return;
            if(mc.screen!=null && mc.screen.getClass().getName().equals("punchymoves.client.tutorial.TutorialScreen")) {
                Class.forName("punchymoves.client.tutorial.TutorialManager").getMethod("dismiss").invoke(null);mc.setScreen(null);return;
            }
            var current=scene;if(current==null || current.index()!=requested || mc.screen!=null)return;
            if(++wait<60)return;
            if(view==0) {
                ClientHandEffectsProbe.shaders("off");var packs=new JsonArray();mc.getResourcePackRepository().getSelectedIds().forEach(packs::add);report.add("selectedPacks",packs);
                camera(mc,current,0);view=1;wait=0;return;
            }
            int capture=view-1;mc.gui.getChat().clearMessages(true);mc.getToasts().clear();
            var row=current.metadata().deepCopy();row.addProperty("view",capture);row.addProperty("glError",org.lwjgl.opengl.GL11C.glGetError());samples.add(row);
            Screenshot.grab(mc.gameDirectory,"village-"+current.index()+"-"+capture+".png",mc.getMainRenderTarget(),ignored->{});
            if(view<3) {camera(mc,current,view);view++;wait=0;return;}
            requested++;scene=null;view=0;wait=0;if(ids!=null && requested>=ids.size())finish(mc,null);
        } catch(Throwable failure) {finish(mc,failure);}
    }
    private void camera(Minecraft mc,Scene scene,int view) {
        var b=scene.bounds();var c=scene.court();double tx=view==0?b.getCenter().getX():c.getX(),tz=view==0?b.getCenter().getZ():c.getZ();
        double distance=view==0?Math.max(b.getXSpan(),b.getZSpan())*.8:35;
        double ex=tx+(view==2?-distance:distance),ez=tz+distance*.7,ey=c.getY()+(view==0?90:view==1?22:10);
        double dx=tx-ex,dz=tz-ez,dy=(c.getY()+4)-ey;
        float yaw=(float)(Math.toDegrees(Math.atan2(dz,dx))-90),pitch=(float)-Math.toDegrees(Math.atan2(dy,Math.hypot(dx,dz)));
        mc.getSingleplayerServer().execute(()->{
            var actor=mc.getSingleplayerServer().getPlayerList().getPlayers().getFirst();actor.getAbilities().flying=true;actor.onUpdateAbilities();
            actor.connection.teleport(ex,ey,ez,yaw,pitch);
        });
    }
    private void serverTick(ServerTickEvent.Post event) {
        if(finished || serverFailure!=null || event.getServer().getPlayerCount()==0 || ids!=null && requested>=ids.size())return;
        try {
            var server=event.getServer();var level=server.overworld();var generator=level.getChunkSource().getGenerator();
            if(ids==null) {
                level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false,server);
                level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false,server);level.setDayTime(6000);
                ids=level.registryAccess().registryOrThrow(Registries.STRUCTURE).getTag(TagKey.create(Registries.STRUCTURE,
                        ResourceLocation.parse("skyweave_compat:managed/composed_villages"))).orElseThrow().stream()
                        .map(h->h.unwrapKey().orElseThrow().location()).sorted().toList();
                if(ids.isEmpty() || ids.size()>32)throw new AssertionError("Authored village showcase count outside1..32: "+ids);
                report.addProperty("profileCount",ids.size());
            }
            if(building!=requested) {
                building=requested;start=null;var id=ids.get(building);var structure=level.registryAccess().registryOrThrow(Registries.STRUCTURE).get(id);
                var source=new ChunkPos(building*48,0);
                for(long seed:new long[]{42,77,1,8675309}) {
                    var candidate=structure.generate(level.registryAccess(),generator,generator.getBiomeSource(),level.getChunkSource().randomState(),
                            server.getStructureManager(),seed,source,0,level,biome->true);
                    if(candidate.isValid() && VillageComposition.assess(id,candidate.getPieces()).qualified()) {start=candidate;generationSeed=seed;break;}
                }
                if(start==null)throw new AssertionError("No bounded complete showcase for "+id);
                var b=start.getBoundingBox();var positions=new ArrayList<ChunkPos>();
                for(int x=b.minX()>>4;x<=b.maxX()>>4;x++)for(int z=b.minZ()>>4;z<=b.maxZ()>>4;z++)positions.add(new ChunkPos(x,z));
                if(positions.size()>256 || start.getPieces().size()>2048)throw new AssertionError("Showcase geometry budget exhausted");
                chunks=List.copyOf(positions);chunkIndex=0;
            }
            if(chunkIndex<chunks.size()) {
                var cp=chunks.get(chunkIndex++);level.getChunk(cp.x,cp.z);
                start.placeInChunk(level,level.structureManager(),generator,RandomSource.create(cp.toLong()^generationSeed),
                        new BoundingBox(cp.getMinBlockX(),level.getMinBuildHeight(),cp.getMinBlockZ(),cp.getMaxBlockX(),level.getMaxBuildHeight()-1,cp.getMaxBlockZ()),cp);
                return;
            }
            if(scene==null) {
                var metadata=new JsonObject();metadata.addProperty("profile",ids.get(building).toString());metadata.addProperty("generationSeed",generationSeed);
                metadata.addProperty("pieces",start.getPieces().size());metadata.addProperty("chunks",chunks.size());
                metadata.add("requiredContent",new Gson().toJsonTree(VillageComposition.assess(ids.get(building),start.getPieces())));
                var court=start.getPieces().stream().filter(piece->VillageComposition.assess(ids.get(building),List.of(piece))
                        .counts().getOrDefault("authored_court",0)>0).toList();
                if(court.size()!=1)throw new AssertionError("Ambiguous original court camera target");
                metadata.addProperty("courtBounds",court.getFirst().getBoundingBox().toString());
                scene=new Scene(building,ids.get(building).toString(),start.getBoundingBox(),court.getFirst().getBoundingBox().getCenter(),metadata);
            }
        } catch(Throwable failure) {serverFailure=failure;}
    }
    private void finish(Minecraft mc,Throwable failure) {
        if(finished)return;finished=true;report.addProperty("complete",failure==null);
        if(failure!=null) {report.addProperty("failure",failure.toString());failure.printStackTrace();}
        try {Files.writeString(root.resolve("village-preview-results.json"),new GsonBuilder().setPrettyPrinting().create().toJson(report));}
        catch(Exception problem) {throw new RuntimeException(problem);}
        mc.stop();
    }
}
