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
        result.addProperty("above_floor_decoration_excluded",true);result.addProperty("actual_native_outputs_match",true);return result;
    }
}
