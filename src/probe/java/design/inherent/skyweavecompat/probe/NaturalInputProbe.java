package design.inherent.skyweavecompat.probe;

import com.google.gson.*;
import design.inherent.skyweavecompat.terrain.structure.*;
import java.nio.file.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.*;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.storage.LevelResource;

/** Actual CARVERS capture, saved partial chunk, and a second JVM that never recaptures its source. */
final class NaturalInputProbe {
    private NaturalInputProbe() {}
    private static void check(boolean valid,String message) { if(!valid)throw new AssertionError(message); }
    static void run(MinecraftServer server,Path output) throws Exception {
        var report=new JsonObject();
        try {
            var level=server.overworld();
            var receipt=server.getWorldPath(LevelResource.ROOT).resolve("natural-input-probe.json");
            ChunkPos position;
            if(Files.isRegularFile(receipt)) {
                var saved=JsonParser.parseString(Files.readString(receipt)).getAsJsonObject();
                position=new ChunkPos(Math.floorDiv(saved.get("x").getAsInt(),16),Math.floorDiv(saved.get("z").getAsInt(),16));
            } else {
                var generator=(org.sathrek.sky_archipelago.worldgen.generator.core.SkyIslandChunkGenerator)level.getChunkSource().getGenerator();
                var field=((design.inherent.skyweavecompat.terrain.GeologicalGenerator)(Object)generator).skyweave$field(level.getChunkSource().randomState());
                var clusters=(design.inherent.skyweavecompat.terrain.ClusterField)(Object)field;
                var members=new ArrayList<org.sathrek.sky_archipelago.worldgen.generator.field.model.IslandDescriptor>();
                for(int x=8;x<=16 && members.isEmpty();x++)for(int z=-16;z<=-8 && members.isEmpty();z++)
                    members.addAll(clusters.skyweave$members(x,z,generator.resolvedSkyIslandSettings()));
                check(!members.isEmpty(),"No distant metadata island in fixture domain");
                var body=members.getFirst();position=null;
                var interiors=(design.inherent.skyweavecompat.terrain.InteriorField)(Object)field;
                for(int dx=-3;dx<=3 && position==null;dx++)for(int dz=-3;dz<=3 && position==null;dz++) {
                    int x=body.centerX()+body.maxRadius()*dx/4,z=body.centerZ()+body.maxRadius()*dz/4;
                    var column=interiors.skyweave$exteriorOf(body,x,z,generator.resolvedSkyIslandSettings());
                    if(column.exists() && column.topY()-column.bottomY()>=32)position=new ChunkPos(Math.floorDiv(x,16),Math.floorDiv(z,16));
                }
                check(position!=null,"Distant descriptor has no bounded solid witness");
            }
            var chunk=level.getChunkSource().getChunk(position.x,position.z,ChunkStatus.CARVERS,true);
            check(chunk!=null,"Native CARVERS chunk unavailable");
            check(chunk.getPersistedStatus()==ChunkStatus.CARVERS,"Fixture did not stop at CARVERS: "+chunk.getPersistedStatus()+" "+chunk.getClass().getName());
            if(!design.inherent.skyweavecompat.OptionalIntegrations.enabled(design.inherent.skyweavecompat.Feature.GROUND_SUPPORT)) {
                check(!NaturalStructureInputs.owned(level) && NaturalStructureInputs.existing(level,chunk)==null,
                        "Disabled structure adaptation exposed retained input authority");
                check(net.neoforged.neoforge.registries.NeoForgeRegistries.ATTACHMENT_TYPES
                        .get(net.minecraft.resources.ResourceLocation.parse("skyweave_compat:natural_structure_inputs"))==null,
                        "Disabled structure adaptation registered a source attachment");
                boolean refused=false;try { NaturalStructureInputs.view(level,List.of(chunk)); }
                catch(StructureTerrainView.Unavailable expected) { refused=true; }
                check(refused,"Disabled source query did not refuse");
                report.addProperty("disabled",true);report.addProperty("complete",true);return;
            }
            var snapshot=NaturalStructureInputs.existing(level,chunk);
            check(snapshot!=null,"Native CARVERS task did not capture natural input");
            if(Files.isRegularFile(receipt)) {
                report=JsonParser.parseString(Files.readString(receipt)).getAsJsonObject();
                var at=new BlockPos(report.get("x").getAsInt(),report.get("y").getAsInt(),report.get("z").getAsInt());
                var expected=BlockState.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE,report.get("naturalState")).getOrThrow();
                check(chunk.getBlockState(at).is(Blocks.BRICKS),"Partial chunk mutation did not persist");
                check(snapshot.state(at).equals(expected),"Retained natural source changed on reopening");
                var view=NaturalStructureInputs.view(level,List.of(chunk));
                check(view.state(at).equals(expected),"Reopened processor view read live changed terrain");
                var journal=snapshot.grading();
                check(journal!=null && journal.layout().equals("a".repeat(64)) && journal.cells().size()==1
                        && journal.cells().getFirst().position().equals(at) && journal.cells().getFirst().original().equals(expected)
                        && journal.cells().getFirst().target().is(Blocks.DIRT),"Pending grading intent changed/lost on reopen");
                var completed=level.getChunk(position.x,position.z);
                check(!completed.hasData(NaturalStructureInputs.TYPE),"Completed native chunk retained full natural palettes");
                report.addProperty("pendingJournalReopened",true);report.addProperty("sourceRetiredAtLight",true);
                report.addProperty("reopened",true);report.addProperty("complete",true);return;
            }
            BlockPos source=null;
            outer:for(int y=chunk.getMaxBuildHeight()-1;y>=chunk.getMinBuildHeight();y--)
                for(int x=0;x<16;x++)for(int z=0;z<16;z++) {
                    var at=new BlockPos(position.getMinBlockX()+x,y,position.getMinBlockZ()+z);var state=chunk.getBlockState(at);
                    if(design.inherent.skyweavecompat.terrain.NaturalMaterials.skin(state)) { source=at;break outer; }
                }
            check(source!=null,"Fixture contained no natural terrain");
            var original=chunk.getBlockState(source);var view=NaturalStructureInputs.view(level,List.of(chunk));
            NaturalStructureInputs.bindGrading(level,chunk,"a".repeat(64),List.of(new VillageGrading.Cell(source,original,Blocks.DIRT.defaultBlockState())));
            boolean earlyRetirement=false;try { NaturalStructureInputs.retire(level,chunk); }
            catch(IllegalStateException expected) { earlyRetirement=true; }
            check(earlyRetirement && chunk.hasData(NaturalStructureInputs.TYPE),"Source retired while neighboring FEATURES could still read it");
            chunk.setBlockState(source,Blocks.BRICKS.defaultBlockState(),false);chunk.setUnsaved(true);
            check(snapshot.state(source).equals(original) && view.state(source).equals(original),"Captured input followed a later write");
            var tags=chunk.writeAttachmentsToNBT(level.registryAccess());check(tags!=null,"Natural attachment did not serialize");
            var key="skyweave_compat:natural_structure_inputs";
            check(tags.contains(key,Tag.TAG_COMPOUND),"Natural attachment missing from native chunk output");
            int controls=1;
            for(int mode=0;mode<13;mode++) {
                var copy=tags.copy();var own=copy.getCompound(key);
                switch(mode) {
                    case 0 -> own.putLong("seed",level.getSeed()+1);
                    case 1 -> own.putString("identity","0".repeat(64));
                    case 2 -> own.putString("dimension","minecraft:the_nether");
                    case 3 -> own.putInt("schema",Integer.MAX_VALUE);
                    case 4 -> own.putInt("x",position.x+1);
                    case 5 -> own.putInt("minY",0);
                    case 6 -> own.put("sections",new ListTag());
                    case 7 -> own.getList("sections",Tag.TAG_COMPOUND).getCompound(0).put("palette",new ListTag());
                    case 8 -> own.remove("seed");
                    case 9 -> own.putString("unknown","unexpected");
                    case 10 -> own.getList("sections",Tag.TAG_COMPOUND).getCompound(0).getList("palette",Tag.TAG_COMPOUND)
                            .getCompound(0).putString("Name","skyweave_compat:missing_block");
                    case 11 -> own.getList("sections",Tag.TAG_COMPOUND).getCompound(0).putLongArray("data",new long[1025]);
                    case 12 -> own.putString("height","896");
                    default -> throw new AssertionError();
                }
                var detached=new ProtoChunk(position,UpgradeData.EMPTY,level,level.registryAccess().registryOrThrow(Registries.BIOME),null);
                detached.readAttachmentsFromNBT(level.registryAccess(),copy);
                check(NaturalStructureInputs.existing(level,detached)==null,"Malformed/mismatched source became authoritative: "+mode);
                boolean refused=false;try { NaturalStructureInputs.view(level,List.of(detached)); }
                catch(StructureTerrainView.Unavailable expected) { refused=true; }
                check(refused,"Unavailable natural source fell back to current terrain");controls++;
            }
            var detached=new ProtoChunk(position,UpgradeData.EMPTY,level,level.registryAccess().registryOrThrow(Registries.BIOME),null);
            check(!detached.hasData(NaturalStructureInputs.TYPE) && NaturalStructureInputs.existing(level,detached)==null,"Missing source created an attachment");
            boolean late=false;try { NaturalStructureInputs.capture(level,chunk); }
            catch(IllegalStateException expected) { late=true; }
            // Existing authentic data may be reused; late capture without data may not.
            check(!late,"An authentic existing capture was rejected");
            detached.setPersistedStatus(ChunkStatus.CARVERS);
            try { NaturalStructureInputs.capture(level,detached);throw new AssertionError("Late capture was accepted"); }
            catch(IllegalStateException expected) { controls++; }
            report.addProperty("x",source.getX());report.addProperty("y",source.getY());report.addProperty("z",source.getZ());
            report.add("naturalState",BlockState.CODEC.encodeStart(com.mojang.serialization.JsonOps.INSTANCE,original).getOrThrow());
            report.addProperty("negativeControls",controls);report.addProperty("serializedBytes",nbtBytes(tags));
            report.addProperty("stage",chunk.getPersistedStatus().toString());report.addProperty("reopened",false);report.addProperty("complete",true);
            Files.writeString(receipt,report.toString());server.saveEverything(true,true,true);
        } catch(Throwable failure) {
            report.addProperty("complete",false);report.addProperty("failure",failure.toString());throw failure;
        } finally { Files.writeString(output,new GsonBuilder().setPrettyPrinting().create().toJson(report)+"\n"); }
    }
    private static int nbtBytes(CompoundTag tag) throws Exception {
        var bytes=new java.io.ByteArrayOutputStream();NbtIo.writeCompressed(tag,bytes);return bytes.size();
    }
}
