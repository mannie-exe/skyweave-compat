package design.inherent.skyweavecompat.probe;

import com.google.gson.*;
import design.inherent.skyweavecompat.terrain.structure.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;

/** Native palette growth in isolated section data; no live terrain or synthetic footing authority. */
final class TerrainOverlayControls {
    private TerrainOverlayControls() {}
    private static void check(boolean valid,String message) {if(!valid)throw new AssertionError(message);}
    static JsonArray run(ServerLevel level) {
        var rows=new JsonArray();
        for(int entries:new int[]{1,16,257}) {
            var chunk=new LevelChunk(level,new ChunkPos(0,0));
            var section=chunk.getSection(chunk.getSectionIndex(700));var states=section.getStates();
            for(int i=1;i<entries;i++)section.setBlockState(i&15,(i>>8)&15,(i>>4)&15,Block.BLOCK_STATE_REGISTRY.byId(i));
            int encoded=states.getSerializedSize();
            var base=StructureTerrainView.capture(level.getSeed(),level.registryAccess(),List.of(chunk));
            check(base.reader().holderLookup(net.minecraft.core.registries.Registries.BLOCK)
                    ==level.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.BLOCK),"Read-only native registry lookup changed");
            try {base.reader().getChunkSource();throw new AssertionError("Snapshot exposed a live chunk source");}
            catch(StructureTerrainView.Unavailable expected) {}
            var at=new BlockPos(15,703,15);var next=new BlockPos(14,704,14);
            int before=base.height(Heightmap.Types.WORLD_SURFACE_WG,at.getX(),at.getZ());
            var edits=List.of(new VillageGrading.Cell(at,base.state(at),Blocks.DEEPSLATE.defaultBlockState()),
                    new VillageGrading.Cell(next,base.state(next),Blocks.DIRT.defaultBlockState()));
            for(int y=688;y<720;y++)for(int x=0;x<16;x++)for(int z=0;z<16;z++) {
                var point=new BlockPos(x,y,z);
                check(base.state(point).equals(chunk.getBlockState(point)),"Native input differs before overlay: "+entries+" "+point);
            }
            var overlay=base.overlay(edits);
            for(int y=688;y<720;y++)for(int x=0;x<16;x++)for(int z=0;z<16;z++) {
                var point=new BlockPos(x,y,z);var original=chunk.getBlockState(point);
                var expected=point.equals(at)?Blocks.DEEPSLATE.defaultBlockState():point.equals(next)?Blocks.DIRT.defaultBlockState():original;
                check(base.state(point).equals(original),"Overlay changed captured input: "+entries+" "+point);
                check(overlay.state(point).equals(expected),"Palette growth changed an unrelated state: "+entries+" "+point);
            }
            check(states.getSerializedSize()==encoded,"Overlay resized the native source palette");
            check(base.height(Heightmap.Types.WORLD_SURFACE_WG,at.getX(),at.getZ())==before,"Overlay changed base height cache");
            check(overlay.height(Heightmap.Types.WORLD_SURFACE_WG,15,15)==704 && overlay.height(Heightmap.Types.WORLD_SURFACE_WG,14,14)==705,
                    "Independent overlay height cache missed new material");
            var nested=overlay.overlay(List.of(new VillageGrading.Cell(at,Blocks.DEEPSLATE.defaultBlockState(),Blocks.STONE.defaultBlockState())));
            check(nested.state(at).is(Blocks.STONE) && overlay.state(at).is(Blocks.DEEPSLATE) && base.state(at).isAir(),"Nested overlay shared mutable palette state");
            var row=new JsonObject();row.addProperty("sourcePaletteEntries",entries);row.addProperty("checkedStates",8192);row.addProperty("nativeSourceUnchanged",true);rows.add(row);
        }
        return rows;
    }
}
