package design.inherent.skyweavecompat.probe;

import com.google.gson.*;
import design.inherent.skyweavecompat.mixin.*;
import design.inherent.skyweavecompat.terrain.structure.*;
import design.inherent.skyweavecompat.Feature;
import design.inherent.skyweavecompat.OptionalIntegrations;
import java.lang.reflect.*;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.*;
import net.minecraft.world.level.levelgen.structure.*;
import net.minecraft.world.level.levelgen.structure.pools.*;
import net.minecraft.world.level.levelgen.structure.templatesystem.*;
import org.sathrek.sky_archipelago.worldgen.generator.core.SkyIslandChunkGenerator;

/** Native template consumers with retained-input fixtures; separate from natural village admission. */
final class PlacementFollowupControls {
    private PlacementFollowupControls() {}
    private static void check(boolean valid,String message) {if(!valid)throw new AssertionError(message);}
    static JsonObject run(MinecraftServer server) throws Exception {
        var level=server.overworld();var chunks=new ArrayList<ChunkAccess>();
        for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++)chunks.add(level.getChunk(x,z));
        var result=new JsonObject();var soils=new JsonArray();result.add("soilCases",soils);
        boolean soilEnabled=OptionalIntegrations.enabled(Feature.GROUND_SUPPORT);result.addProperty("soilRuntimeEnabled",soilEnabled);
        for(var material:List.of(Blocks.MUD,Blocks.GRASS_BLOCK,Blocks.SAND))for(var rotation:Rotation.values()) {
            prepare(chunks,material.defaultBlockState());
            var source=StructureTerrainView.capture(level.getSeed(),level.registryAccess(),chunks);
            var id=ResourceLocation.parse("ctov:village/swamp_fortified/town_center");
            var processors=level.registryAccess().registryOrThrow(Registries.PROCESSOR_LIST)
                    .getHolderOrThrow(net.minecraft.resources.ResourceKey.create(Registries.PROCESSOR_LIST,id));
            var element=StructurePoolElement.single(id.toString(),processors).apply(StructureTemplatePool.Projection.RIGID);
            var origin=new BlockPos(8,700,8);var bounds=element.getBoundingBox(server.getStructureManager(),origin,rotation);
            var access=(VillageTemplateAccess)element;
            var settings=access.skyweave$settings(rotation,bounds,LiquidSettings.APPLY_WATERLOGGING,false);
            var palette=settings.getRandomPalette(((VillagePaletteAccess)access.skyweave$template(server.getStructureManager())).skyweave$palettes(),origin);
            var predicted=StructureTemplate.processBlockInfos(source.reader(),origin,origin,settings,palette.blocks(),access.skyweave$template(server.getStructureManager()));
            var byPosition=new HashMap<BlockPos,StructureTemplate.StructureBlockInfo>();predicted.forEach(info->byPosition.put(info.pos(),info));
            // Later physical material cannot replace the retained source used by either consumer.
            for(var chunk:chunks)for(int x=0;x<16;x++)for(int z=0;z<16;z++)
                chunk.setBlockState(new BlockPos(chunk.getPos().getMinBlockX()+x,699,chunk.getPos().getMinBlockZ()+z),Blocks.BRICKS.defaultBlockState(),false);
            scoped(level,source,()->check(element.place(server.getStructureManager(),level,level.structureManager(),level.getChunkSource().getGenerator(),
                    origin,origin,rotation,bounds,RandomSource.create(42),LiquidSettings.APPLY_WATERLOGGING,false),"Native swamp center placement failed"));
            int soil=0,paving=0;
            for(var raw:palette.blocks()) {
                if(raw.pos().getY()!=0)continue;
                var at=StructureTemplate.calculateRelativePosition(settings,raw.pos()).offset(origin);
                if(raw.state().is(Blocks.GRASS_BLOCK)||raw.state().is(Blocks.DIRT)) {
                    var expected=material==Blocks.MUD && soilEnabled?Blocks.MUD.defaultBlockState():raw.state();
                    check(level.getBlockState(at).equals(expected),"Wrong actual ambient soil: "+material+" "+rotation+" "+at+" "+level.getBlockState(at));
                    check(byPosition.get(at).state().equals(expected),"Native preview and actual soil differ: "+at);soil++;
                }
                if(raw.state().is(Blocks.DIRT_PATH)) {
                    check(level.getBlockState(at).is(Blocks.DIRT_PATH),"Intentional path was repainted");paving++;
                }
            }
            check(soil==253 && paving==108,"Pinned center's ground intent changed");
            var row=new JsonObject();row.addProperty("host",net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(material).toString());
            row.addProperty("rotation",rotation.name());row.addProperty("actualSoilAndPreviewMatches",soil);row.addProperty("preservedPaths",paving);soils.add(row);
        }
        result.add("oasisCases",oases(level,chunks));
        result.add("unrelatedMangroves",mangroves(level,chunks));
        result.addProperty("scope","Actual native template writes and processed previews with an explicitly bound retained-input fixture; does not certify natural whole-village admission");
        return result;
    }
    private static JsonArray mangroves(ServerLevel level,List<ChunkAccess> chunks) throws Exception {
        var result=new JsonArray();var origin=new BlockPos(8,700,8);
        for(var name:List.of("mangrove","tall_mangrove")) {
            var feature=level.registryAccess().registryOrThrow(Registries.CONFIGURED_FEATURE)
                    .get(ResourceLocation.withDefaultNamespace(name));
            check(feature!=null,"Missing pinned native mangrove: "+name);
            boolean placed=false;long seed=0;Map<BlockPos,BlockState> expected=Map.of();
            for(;seed<8 && !placed;seed++) {
                prepare(chunks,Blocks.MUD.defaultBlockState());
                placed=feature.place(level,level.getChunkSource().getGenerator(),RandomSource.create(seed),origin);
                if(placed)expected=treeStates(chunks);
            }
            check(placed,"Unrelated native mangrove never placed: "+name);
            long selectedSeed=seed-1;var first=expected;
            prepare(chunks,Blocks.MUD.defaultBlockState());
            var source=StructureTerrainView.capture(level.getSeed(),level.registryAccess(),chunks);
            scoped(level,source,()->check(feature.place(level,level.getChunkSource().getGenerator(),RandomSource.create(selectedSeed),origin),
                    "Empty village protection suppressed unrelated mangrove: "+name));
            check(treeStates(chunks).equals(first),"Unrelated mangrove's native writes changed: "+name);
            long roots=first.values().stream().filter(state->state.is(Blocks.MANGROVE_ROOTS)||state.is(Blocks.MUDDY_MANGROVE_ROOTS)).count();
            long logs=first.values().stream().filter(state->state.is(Blocks.MANGROVE_LOG)).count();
            check(roots>0 && logs>0,"Mangrove control lacks native roots/trunk");
            var row=new JsonObject();row.addProperty("configuredFeature","minecraft:"+name);row.addProperty("seed",selectedSeed);
            row.addProperty("nativeRootCells",roots);row.addProperty("nativeLogCells",logs);row.addProperty("unchangedWrittenCells",first.size());
            var protectedBox=new BoundingBox(7,699,7,9,732,9);
            prepare(chunks,Blocks.MUD.defaultBlockState());
            var protectedSource=StructureTerrainView.capture(level.getSeed(),level.registryAccess(),chunks);boolean[] qualifiedPlaced={false};
            scoped(level,protectedSource,protectedBox,()->qualifiedPlaced[0]=feature.place(level,level.getChunkSource().getGenerator(),RandomSource.create(selectedSeed),origin));
            boolean enabled=OptionalIntegrations.enabled(Feature.GROUND_SUPPORT);
            check(qualifiedPlaced[0]!=enabled,"Native mangrove protection does not follow optional selection");
            if(enabled)check(treeStates(chunks).isEmpty(),"Refused mangrove wrote partial material");
            // Root-relative dressing extends the qualified envelope, rather than
            // acquiring a narrower partial-write guard.
            var ops=net.minecraft.resources.RegistryOps.create(com.mojang.serialization.JsonOps.INSTANCE,level.registryAccess());
            var definition=net.minecraft.world.level.levelgen.feature.configurations.TreeConfiguration.CODEC
                    .encodeStart(ops,(net.minecraft.world.level.levelgen.feature.configurations.TreeConfiguration)feature.config()).getOrThrow().getAsJsonObject();
            definition.getAsJsonArray("decorators").add(JsonParser.parseString("{\"type\":\"minecraft:alter_ground\",\"provider\":{\"type\":\"minecraft:simple_state_provider\",\"state\":{\"Name\":\"minecraft:podzol\"}}}"));
            var altered=net.minecraft.world.level.levelgen.feature.configurations.TreeConfiguration.CODEC.parse(ops,definition).getOrThrow();
            var dressed=new net.minecraft.world.level.levelgen.feature.ConfiguredFeature<>(net.minecraft.world.level.levelgen.feature.Feature.TREE,altered);
            prepare(chunks,Blocks.MUD.defaultBlockState());var dressedSource=StructureTerrainView.capture(level.getSeed(),level.registryAccess(),chunks);
            boolean[] dressedPlaced={false};
            scoped(level,dressedSource,protectedBox,()->dressedPlaced[0]=dressed.place(level,level.getChunkSource().getGenerator(),RandomSource.create(selectedSeed),origin));
            check(dressedPlaced[0]!=enabled,"Root-relative dressing bypassed whole-tree protection");
            if(enabled)check(treeStates(chunks).isEmpty(),"Refused dressed tree wrote partial material");
            // A different root/trunk combination is deliberately unqualified and native.
            definition.getAsJsonObject("trunk_placer").addProperty("type","minecraft:straight_trunk_placer");
            var unusual=net.minecraft.world.level.levelgen.feature.configurations.TreeConfiguration.CODEC.parse(ops,definition).getOrThrow();
            var unqualified=new net.minecraft.world.level.levelgen.feature.ConfiguredFeature<>(net.minecraft.world.level.levelgen.feature.Feature.TREE,unusual);
            prepare(chunks,Blocks.MUD.defaultBlockState());var unqualifiedSource=StructureTerrainView.capture(level.getSeed(),level.registryAccess(),chunks);
            scoped(level,unqualifiedSource,protectedBox,()->check(unqualified.place(level,level.getChunkSource().getGenerator(),RandomSource.create(selectedSeed),origin),
                    "Unqualified root/trunk combination was intercepted"));
            check(!treeStates(chunks).isEmpty(),"Native unsupported decorator control made no material");
            row.addProperty("protectedTreeRefused",enabled);row.addProperty("protectedRootDressingRefused",enabled);
            row.addProperty("unqualifiedRootTrunkNative",true);result.add(row);
        }
        return result;
    }
    private static Map<BlockPos,BlockState> treeStates(List<ChunkAccess> chunks) {
        var states=new HashMap<BlockPos,BlockState>();
        for(var chunk:chunks)for(int x=0;x<16;x++)for(int z=0;z<16;z++)for(int y=696;y<752;y++) {
            var position=new BlockPos(chunk.getPos().getMinBlockX()+x,y,chunk.getPos().getMinBlockZ()+z);
            var state=chunk.getBlockState(position);
            if(!state.equals(y==699?Blocks.MUD.defaultBlockState():y<699?Blocks.STONE.defaultBlockState():Blocks.AIR.defaultBlockState()))
                states.put(position,state);
        }
        return Map.copyOf(states);
    }
    private static void prepare(List<ChunkAccess> chunks,BlockState cap) {
        for(var chunk:chunks)for(int x=0;x<16;x++)for(int z=0;z<16;z++)for(int y=696;y<752;y++) {
            var position=new BlockPos(chunk.getPos().getMinBlockX()+x,y,chunk.getPos().getMinBlockZ()+z);
            chunk.setBlockState(position,y==699?cap:y<699?Blocks.STONE.defaultBlockState():Blocks.AIR.defaultBlockState(),false);
        }
    }
    @SuppressWarnings("unchecked")
    private static void scoped(ServerLevel level,StructureTerrainView source,Runnable action) throws Exception {
        scoped(level,source,null,action);
    }
    @SuppressWarnings("unchecked")
    private static void scoped(ServerLevel level,StructureTerrainView source,BoundingBox protection,Runnable action) throws Exception {
        var type=Class.forName("design.inherent.skyweavecompat.terrain.structure.GroundVillageTerrain$Phase");
        var constructor=type.getDeclaredConstructor(SkyIslandChunkGenerator.class,net.minecraft.world.level.WorldGenLevel.class,ChunkAccess.class);constructor.setAccessible(true);
        var phase=constructor.newInstance(level.getChunkSource().getGenerator(),level,level.getChunk(0,0));
        var field=type.getDeclaredField("naturalTerrain");field.setAccessible(true);field.set(phase,source);
        if(protection!=null) {
            var boxes=type.getDeclaredField("featureProtection");boxes.setAccessible(true);
            ((List<BoundingBox>)boxes.get(phase)).add(protection);
        }
        var active=GroundVillageTerrain.class.getDeclaredField("ACTIVE");active.setAccessible(true);var scope=(ThreadLocal<Object>)active.get(null);var before=scope.get();
        scope.set(phase);try{action.run();}finally{if(before==null)scope.remove();else scope.set(before);}
    }
    private static JsonArray oases(ServerLevel level,List<ChunkAccess> chunks) throws Exception {
        var inspect=Class.forName("design.inherent.skyweavecompat.terrain.structure.VillageDecorationConflicts")
                .getDeclaredMethod("inspect",List.class,StructureTemplateManager.class,int.class);inspect.setAccessible(true);
        var manager=level.getServer().getStructureManager();var result=new JsonArray();
        for(var turn:Rotation.values()) {
            prepare(chunks,Blocks.STONE.defaultBlockState());
            var houseElement=StructurePoolElement.legacy("minecraft:village/desert/houses/desert_cartographer_house_1").apply(StructureTemplatePool.Projection.RIGID);
            var oasisElement=StructurePoolElement.single("ctov:village/desert/deco/oasis").apply(StructureTemplatePool.Projection.RIGID);
            var pivot=new BlockPos(8,700,8);
            var house=piece(manager,houseElement,StructureTemplate.calculateRelativePosition(new StructurePlaceSettings().setRotation(turn),new BlockPos(8,0,-5)).offset(pivot),turn.getRotated(Rotation.CLOCKWISE_90));
            var oasis=piece(manager,oasisElement,StructureTemplate.calculateRelativePosition(new StructurePlaceSettings().setRotation(turn),new BlockPos(-4,-3,0)).offset(pivot),turn.getRotated(Rotation.COUNTERCLOCKWISE_90));
            var conflicts=(List<?>)inspect.invoke(null,List.of(house,oasis),manager,3);check(conflicts.size()==1,"Reported vertically-disjoint oasis conflict was missed: "+turn);
            check(!house.getBoundingBox().intersects(oasis.getBoundingBox()),"Fixture lost its disjoint occupied boxes");
            place(level,house);place(level,oasis);
            int unsupported=0;
            var houseTemplate=((VillageTemplateAccess)houseElement).skyweave$template(manager);
            var palette=((VillagePaletteAccess)houseTemplate).skyweave$palettes().getFirst();
            for(var raw:palette.blocks())if(raw.pos().getY()==0 && raw.state().is(Blocks.SAND)) {
                var at=StructureTemplate.calculateRelativePosition(new StructurePlaceSettings().setRotation(house.getRotation()),raw.pos()).offset(house.getPosition());
                if(level.getBlockState(at).is(Blocks.SAND) && level.getBlockState(at.below()).isAir())unsupported++;
            }
            check(unsupported>0,"Native red house/oasis writes did not reproduce unsupported sand");
            prepare(chunks,Blocks.STONE.defaultBlockState());place(level,house);
            // The guard's only chosen correction is local omission; it never fills or relocates the pool.
            var cut=conflicts.getFirst();var field=cut.getClass().getDeclaredMethod("cut");field.setAccessible(true);var cutPosition=(BlockPos)field.invoke(cut);
            check(!level.getBlockState(cutPosition).isAir() && level.getFluidState(cutPosition).isEmpty(),"Omitted cut did not retain original support");
            var safe=piece(manager,oasisElement,oasis.getPosition().offset(0,0,16),oasis.getRotation());
            check(((List<?>)inspect.invoke(null,List.of(house,safe),manager,3)).isEmpty(),"Safe separate oasis was rejected");
            place(level,safe);int water=0;
            var bounds=safe.getBoundingBox();for(var at:BlockPos.betweenClosed(bounds.minX(),bounds.minY(),bounds.minZ(),bounds.maxX(),bounds.maxY(),bounds.maxZ()))if(level.getBlockState(at).is(Blocks.WATER))water++;
            check(water==11,"Native safe oasis water was lost");
            var row=new JsonObject();row.addProperty("rotation",turn.name());row.addProperty("redUnsupportedSand",unsupported);row.addProperty("conflicts",conflicts.size());row.addProperty("safeOasisWater",water);result.add(row);
        }
        return result;
    }
    private static PoolElementStructurePiece piece(StructureTemplateManager manager,StructurePoolElement element,BlockPos position,Rotation rotation) {
        return new PoolElementStructurePiece(manager,element,position,element.getGroundLevelDelta(),rotation,element.getBoundingBox(manager,position,rotation),LiquidSettings.APPLY_WATERLOGGING);
    }
    private static void place(ServerLevel level,PoolElementStructurePiece piece) {
        piece.postProcess(level,level.structureManager(),level.getChunkSource().getGenerator(),RandomSource.create(42),piece.getBoundingBox(),new ChunkPos(piece.getPosition()),piece.getPosition());
    }
}
