package design.inherent.skyweavecompat.probe;

import com.google.gson.JsonObject;
import design.inherent.skyweavecompat.terrain.structure.FoundationSupport;
import design.inherent.skyweavecompat.terrain.structure.StructureTerrainView;
import design.inherent.skyweavecompat.terrain.structure.VillageFootprint;
import design.inherent.skyweavecompat.terrain.structure.VillageLayoutRepair;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.TagParser;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraft.world.level.levelgen.structure.templatesystem.LiquidSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;

/** Native alternate palettes with different feet and an above-floor decoration. */
final class VillageRawIntentControls {
    private VillageRawIntentControls() {}
    private static void check(boolean valid,String message) {if(!valid)throw new AssertionError(message);}
    @SuppressWarnings("unchecked")
    static JsonObject run(ServerLevel level,StructureTerrainView view) throws Exception {
        var manager=level.getServer().getStructureManager();
        var id=ResourceLocation.parse("skyweave_compat:probe/alternate_footings");
        var template=manager.getOrCreate(id);
        template.load(level.registryAccess().lookupOrThrow(Registries.BLOCK),TagParser.parseTag("""
                {size:[8,4,1],palettes:[
                  [{Name:"minecraft:stone"},{Name:"minecraft:air"},{Name:"minecraft:oak_fence"},{Name:"minecraft:stone"}],
                  [{Name:"minecraft:air"},{Name:"minecraft:stone"},{Name:"minecraft:oak_fence"},{Name:"minecraft:stone"}]
                ],blocks:[{pos:[0,0,0],state:0},{pos:[2,0,0],state:1},{pos:[4,1,0],state:2},{pos:[6,0,0],state:3}],entities:[]}
                """));
        var element=StructurePoolElement.single(id.toString()).apply(StructureTemplatePool.Projection.RIGID);
        var node=VillageLayoutRepair.class.getDeclaredMethod("node",StructurePiece.class,StructureTemplateManager.class);node.setAccessible(true);
        var variants=new HashSet<Integer>();int cases=0;
        var saved=new HashMap<BlockPos,BlockState>();
        try {
            for(int z=1;z<=8;z++)for(int x=1;x<=8;x++) {
                var origin=new BlockPos(x,700,z);var bounds=element.getBoundingBox(manager,origin,Rotation.NONE);
                for(var at:BlockPos.betweenClosed(bounds.minX(),bounds.minY(),bounds.minZ(),bounds.maxX(),bounds.maxY(),bounds.maxZ()))
                    saved.putIfAbsent(at.immutable(),level.getBlockState(at));
                var piece=new PoolElementStructurePiece(manager,element,origin,element.getGroundLevelDelta(),Rotation.NONE,bounds,LiquidSettings.APPLY_WATERLOGGING);
                var reference=new BlockPos(bounds.getCenter().getX(),bounds.minY(),bounds.getCenter().getZ());
                var compiled=VillageFootprint.compile(piece,0,reference,manager,view,new VillageFootprint.Budget(),null);
                check(compiled.qualified() && compiled.feet().size()==2,"Native alternate-palette footprint not discriminating");
                var description=node.invoke(null,piece,manager);var accessor=description.getClass().getDeclaredMethod("feet");accessor.setAccessible(true);
                var predicted=(List<FoundationSupport.Foot>)accessor.invoke(description);
                var positions=predicted.stream().map(foot->new BlockPos(foot.x(),foot.y(),foot.z())).collect(java.util.stream.Collectors.toSet());
                check(positions.equals(compiled.feet().stream().map(VillageFootprint.Foot::position).collect(java.util.stream.Collectors.toSet())),
                        "Early fitting differs from selected native palette/declared floor at "+origin);
                check(positions.stream().noneMatch(at->at.getY()!=700),"Above-floor decoration became early support intent");
                check(element.place(manager,level,level.structureManager(),level.getChunkSource().getGenerator(),origin,reference,
                        Rotation.NONE,bounds,RandomSource.create(42),LiquidSettings.APPLY_WATERLOGGING,false),"Native alternate-palette placement failed");
                for(var foot:compiled.feet())check(level.getBlockState(foot.position()).equals(foot.expected()),"Native output differs from selected footing palette");
                variants.add(compiled.feet().stream().mapToInt(foot->foot.original().getX()).min().orElseThrow());cases++;
            }
            check(variants.equals(java.util.Set.of(0,2)),"Both actual native palettes were not selected");
        } finally {
            saved.forEach((at,state)->level.getChunkAt(at).setBlockState(at,state,false));
        }
        var result=new JsonObject();result.addProperty("native_positions",cases);result.addProperty("selected_palette_variants",variants.size());
        result.addProperty("embedding_bearing_cases",embeddings(level,view,node));
        result.add("installedTemplateBearings",installedTemplates(level,node));
        result.addProperty("above_floor_decoration_excluded",true);result.addProperty("actual_native_outputs_match",true);return result;
    }

    @SuppressWarnings("unchecked")
    private static com.google.gson.JsonArray installedTemplates(ServerLevel level,java.lang.reflect.Method node) throws Exception {
        var rows=new com.google.gson.JsonArray();var manager=level.getServer().getStructureManager();
        var counts=java.util.Map.of("ctov:village/mountain/house/small_1",34,
                "ctov:village/mountain/house/small_2",43,"ctov:village/mountain/jobsite/butcher",11);
        for(var entry:counts.entrySet().stream().sorted(java.util.Map.Entry.comparingByKey()).toList()) {
            var id=ResourceLocation.parse(entry.getKey());if(manager.get(id).isEmpty())continue;
            for(var rotation:Rotation.values()) {
                var element=StructurePoolElement.single(id.toString()).apply(StructureTemplatePool.Projection.RIGID);var origin=new BlockPos(8,700,8);
                var piece=new PoolElementStructurePiece(manager,element,origin,element.getGroundLevelDelta(),rotation,
                        element.getBoundingBox(manager,origin,rotation),LiquidSettings.APPLY_WATERLOGGING);
                var bounds=piece.getBoundingBox();var chunks=new java.util.ArrayList<net.minecraft.world.level.chunk.ChunkAccess>();
                for(int cx=Math.floorDiv(bounds.minX()-2,16);cx<=Math.floorDiv(bounds.maxX()+2,16);cx++)
                    for(int cz=Math.floorDiv(bounds.minZ()-2,16);cz<=Math.floorDiv(bounds.maxZ()+2,16);cz++)chunks.add(level.getChunk(cx,cz));
                var captured=StructureTerrainView.capture(level.getSeed(),level.registryAccess(),chunks);
                var compiled=VillageFootprint.compile(piece,0,origin,manager,captured,new VillageFootprint.Budget(),null);
                check(compiled.qualified() && compiled.feet().size()==entry.getValue(),"Installed template bearing changed: "+id+" "+rotation+" "+compiled.reason());
                var description=node.invoke(null,piece,manager);var accessor=description.getClass().getDeclaredMethod("feet");accessor.setAccessible(true);
                var predicted=(List<FoundationSupport.Foot>)accessor.invoke(description);
                check(predicted.stream().map(f->new BlockPos(f.x(),f.y(),f.z())).collect(java.util.stream.Collectors.toSet())
                        .equals(compiled.feet().stream().map(VillageFootprint.Foot::position).collect(java.util.stream.Collectors.toSet())),
                        "Installed template early/processed bearing differs: "+id+" "+rotation);
                var row=new JsonObject();row.addProperty("template",id.toString());row.addProperty("rotation",rotation.name());
                row.addProperty("feet",compiled.feet().size());rows.add(row);
            }
        }
        return rows;
    }

    @SuppressWarnings("unchecked")
    private static int embeddings(ServerLevel level,StructureTerrainView view,java.lang.reflect.Method node) throws Exception {
        var manager=level.getServer().getStructureManager();var id=ResourceLocation.parse("skyweave_compat:probe/embedding_bearings");
        var template=manager.getOrCreate(id);int cases=0;
        var topStair=net.minecraft.world.level.block.Blocks.BIRCH_STAIRS.defaultBlockState().setValue(
                net.minecraft.world.level.block.state.properties.BlockStateProperties.HALF,net.minecraft.world.level.block.state.properties.Half.TOP);
        var topSlab=net.minecraft.world.level.block.Blocks.BIRCH_SLAB.defaultBlockState().setValue(
                net.minecraft.world.level.block.state.properties.BlockStateProperties.SLAB_TYPE,net.minecraft.world.level.block.state.properties.SlabType.TOP);
        var scaffold=net.minecraft.world.level.block.Blocks.SCAFFOLDING.defaultBlockState().setValue(net.minecraft.world.level.block.ScaffoldingBlock.DISTANCE,0);
        var states=List.of(net.minecraft.world.level.block.Blocks.CAMPFIRE.defaultBlockState(),net.minecraft.world.level.block.Blocks.SOUL_CAMPFIRE.defaultBlockState(),
                net.minecraft.world.level.block.Blocks.BARREL.defaultBlockState(),net.minecraft.world.level.block.Blocks.FURNACE.defaultBlockState(),
                net.minecraft.world.level.block.Blocks.OAK_FENCE.defaultBlockState(),net.minecraft.world.level.block.Blocks.COBBLESTONE_WALL.defaultBlockState(),topStair,scaffold,
                topSlab,scaffold.setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.WATERLOGGED,true),
                net.minecraft.world.level.block.Blocks.SHULKER_BOX.defaultBlockState());
        for(int index=0;index<states.size();index++)for(var rotation:Rotation.values()) {
            var state=states.get(index);var tag=TagParser.parseTag("""
                    {size:[4,2,1],palette:[],blocks:[{pos:[0,0,0],state:0},{pos:[2,0,0],state:1},{pos:[2,1,0],state:0}],entities:[]}
                    """);
            var palette=new ListTag();palette.add(NbtUtils.writeBlockState(net.minecraft.world.level.block.Blocks.STONE.defaultBlockState()));palette.add(NbtUtils.writeBlockState(state));tag.put("palette",palette);
            template.load(level.registryAccess().lookupOrThrow(Registries.BLOCK),tag);
            var element=StructurePoolElement.single(id.toString()).apply(StructureTemplatePool.Projection.RIGID);var origin=new BlockPos(8,700,8);
            var piece=new PoolElementStructurePiece(manager,element,origin,1,rotation,element.getBoundingBox(manager,origin,rotation),LiquidSettings.APPLY_WATERLOGGING);
            var compiled=VillageFootprint.compile(piece,0,origin,manager,view,new VillageFootprint.Budget(),null);
            var description=node.invoke(null,piece,manager);var accessor=description.getClass().getDeclaredMethod("feet");accessor.setAccessible(true);
            var predicted=(List<FoundationSupport.Foot>)accessor.invoke(description);
            var refusal=description.getClass().getDeclaredMethod("refusal");refusal.setAccessible(true);
            if(index>=9) {
                check(compiled.status()==VillageFootprint.Status.UNSUPPORTED && compiled.feet().isEmpty() && predicted.isEmpty() && refusal.invoke(description)!=null,
                        "Wet/unknown embedding acquired partial or promoted support: "+state+" "+compiled.reason());
            } else {
                check(refusal.invoke(description)==null,"Known native bearing acquired an early refusal");
                int expected=index==8?1:2;check(compiled.qualified() && compiled.feet().size()==expected,"Embedding bearing mismatch: "+state);
                var early=predicted.stream().map(f->new BlockPos(f.x(),f.y(),f.z())).collect(java.util.stream.Collectors.toSet());
                check(early.equals(compiled.feet().stream().map(VillageFootprint.Foot::position).collect(java.util.stream.Collectors.toSet())),"Raw/processed bearing policy diverged");
                check(compiled.feet().stream().allMatch(f->f.original().getY()==0),"An embedding exclusion promoted the roof");
            }
            cases++;
        }
        return cases;
    }
}
