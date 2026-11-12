package design.inherent.skyweavecompat.probe;

import com.google.gson.*;
import design.inherent.skyweavecompat.mixin.VillageTemplateAccess;
import design.inherent.skyweavecompat.terrain.structure.*;
import design.inherent.skyweavecompat.terrain.IslandContext;
import design.inherent.skyweavecompat.terrain.GeologicalFrame;
import java.nio.file.*;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.pools.*;
import net.minecraft.world.level.levelgen.structure.templatesystem.*;

/** Native template placement versus compiled feet on a deliberately unsupported diagnostic platform. */
final class VillageFootprintProbe {
    private VillageFootprintProbe() {}
    private static void check(boolean valid,String message) { if(!valid)throw new AssertionError(message); }
    static void run(MinecraftServer server,Path output) throws Exception {
        var report=new JsonObject();var rows=new JsonArray();report.add("placements",rows);
        try {
            var level=server.overworld();var manager=server.getStructureManager();
            report.addProperty("processorGraphControls",VillageProcessorControls.run(level.registryAccess()));
            var receipt=server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).resolve("village-support-probe.json");
            if(Files.isRegularFile(receipt)) {
                check(Files.size(receipt)<4194304,"Oversized owned support receipt");
                var saved=JsonParser.parseString(Files.readString(receipt)).getAsJsonObject();
                check(saved.get("schema").getAsInt()==1,"Unknown owned support receipt");
                report=saved.getAsJsonObject("report").deepCopy();
                var blocks=report.getAsJsonArray("persistedBlocks");check(blocks.size()>0 && blocks.size()<=8192,"Invalid receipt count");
                var ops=net.minecraft.resources.RegistryOps.create(com.mojang.serialization.JsonOps.INSTANCE,level.registryAccess());
                for(var value:blocks) {
                    var block=value.getAsJsonObject();var at=new BlockPos(block.get("x").getAsInt(),block.get("y").getAsInt(),block.get("z").getAsInt());
                    check(Math.abs(at.getX())<=128 && Math.abs(at.getZ())<=128 && at.getY()>=-128 && at.getY()<768,"Receipt left fixture bounds");
                    var expected=net.minecraft.world.level.block.state.BlockState.CODEC.parse(ops,block.get("state")).getOrThrow();
                    check(level.getBlockState(at).equals(expected),"Saved footing state changed after real reopening: "+at);
                }
                report.addProperty("reopened",true);return;
            }
            var normal=StructurePoolElement.single("minecraft:village/plains/houses/plains_small_house_1")
                    .apply(StructureTemplatePool.Projection.RIGID);
            var damage=new RuleProcessor(List.of(
                    new ProcessorRule(new RandomBlockMatchTest(Blocks.COBBLESTONE,.5F),AlwaysTrueTest.INSTANCE,Blocks.COBWEB.defaultBlockState()),
                    new ProcessorRule(new RandomBlockMatchTest(Blocks.OAK_PLANKS,.5F),AlwaysTrueTest.INSTANCE,Blocks.COBWEB.defaultBlockState())));
            var damaged=StructurePoolElement.single("minecraft:village/plains/houses/plains_small_house_1",
                    Holder.direct(new StructureProcessorList(List.of(damage)))).apply(StructureTemplatePool.Projection.RIGID);
            var elements=new ArrayList<StructurePoolElement>(List.of(normal,damaged));
            var pool=server.registryAccess().registryOrThrow(Registries.TEMPLATE_POOL).get(ResourceLocation.parse("ctov:village/plains/house"));
            if(pool!=null) {
                for(var candidate:pool.getShuffledTemplates(RandomSource.create(42))) {
                    var leaf=candidate;
                    for(int i=0;leaf instanceof VillageDelegate d && i<8;i++)leaf=d.skyweave$delegate();
                    if(leaf instanceof SinglePoolElement single && ((VillageTemplateAccess)single).skyweave$template().left()
                            .map(id->id.toString().equals("ctov:village/plains/house/small_1")).orElse(false)) {
                        elements.add(candidate);break;
                    }
                }
                check(elements.size()==3,"Actual CTOV house/processor input was not selected");
            }
            int damagedFeet=0,compared=0,unsupportedGaps=0,foundationWrites=0,negativeControls=0;
            boolean viewControls=false,budgetRefused=false;
            var retained=new LinkedHashMap<BlockPos,net.minecraft.world.level.block.state.BlockState>();
            for(int e=0;e<elements.size();e++)for(var rotation:Rotation.values()) {
                var element=elements.get(e);var origin=new BlockPos(8,700,8);
                var bounds=element.getBoundingBox(manager,origin,rotation);
                int minX=bounds.minX()-2,maxX=bounds.maxX()+2,minZ=bounds.minZ()-2,maxZ=bounds.maxZ()+2;
                var chunks=new ArrayList<ChunkAccess>();
                for(int cx=Math.floorDiv(minX,16);cx<=Math.floorDiv(maxX,16);cx++)
                    for(int cz=Math.floorDiv(minZ,16);cz<=Math.floorDiv(maxZ,16);cz++)chunks.add(level.getChunk(cx,cz));
                for(int x=minX;x<=maxX;x++)for(int z=minZ;z<=maxZ;z++) {
                    var chunk=level.getChunk(x>>4,z>>4);
                    chunk.setBlockState(new BlockPos(x,696,z),Blocks.DIRT.defaultBlockState(),false);
                    for(int y=697;y<level.getMaxBuildHeight();y++)chunk.setBlockState(new BlockPos(x,y,z),Blocks.AIR.defaultBlockState(),false);
                }
                for(var chunk:chunks)Heightmap.primeHeightmaps(chunk,EnumSet.allOf(Heightmap.Types.class));
                var view=StructureTerrainView.capture(level.getSeed(),level.registryAccess(),chunks);
                if(e==0 && rotation==Rotation.NONE)report.add("rawIntentControls",VillageRawIntentControls.run(level,view));
                var source=new BlockPos(minX,696,minZ);
                if(!viewControls) {
                    level.setBlock(source,Blocks.BRICKS.defaultBlockState(),2);
                    check(view.state(source).is(Blocks.DIRT),"Terrain snapshot followed a live mutation");
                    boolean denied=false;
                    try { view.reader().setBlock(source,Blocks.STONE.defaultBlockState(),2); }
                    catch(StructureTerrainView.Unavailable expected) { denied=true; }
                    check(denied && level.getBlockState(source).is(Blocks.BRICKS),"Processor view admitted a world write");
                    int loaded=level.getChunkSource().getLoadedChunksCount();denied=false;
                    try { view.state(new BlockPos(100000,700,100000)); }
                    catch(StructureTerrainView.Unavailable expected) { denied=true; }
                    check(denied && loaded==level.getChunkSource().getLoadedChunksCount(),"Missing terrain input created a chunk or became air");
                    level.setBlock(source,Blocks.DIRT.defaultBlockState(),2);viewControls=true;
                }
                var piece=new PoolElementStructurePiece(manager,element,origin,element.getGroundLevelDelta(),rotation,bounds,LiquidSettings.APPLY_WATERLOGGING);
                var center=bounds.getCenter();var reference=new BlockPos(center.getX(),bounds.minY(),center.getZ());
                var result=VillageFootprint.compile(piece,e,reference,manager,view,new VillageFootprint.Budget(),null);
                check(result.qualified(),"Footprint refused: "+element+" "+rotation+" "+result.reason());
                if(e==0)check(!result.feet().isEmpty(),"Native house has no qualified feet");
                var repeat=VillageFootprint.compile(piece,e,reference,manager,view,new VillageFootprint.Budget(),null);
                check(result.equals(repeat),"Repeated processed footprint changed");
                for(int groundDelta:new int[]{-8,20}) {
                    var adaptedPiece=new PoolElementStructurePiece(manager,element,origin,groundDelta,rotation,bounds,LiquidSettings.APPLY_WATERLOGGING);
                    var adapted=VillageFootprint.compile(adaptedPiece,e,reference,manager,view,new VillageFootprint.Budget(),null);
                    check(result.equals(adapted),"Derived piece ground delta changed template support intent: "+groundDelta);
                }
                check(element.place(manager,level,level.structureManager(),level.getChunkSource().getGenerator(),origin,reference,
                        rotation,bounds,RandomSource.create(42),LiquidSettings.APPLY_WATERLOGGING,false),"Actual template placement failed");
                for(var foot:result.feet()) {
                    check(level.getBlockState(foot.position()).equals(foot.expected()),"Compiled foot differs from actual native output: "+foot);
                    check(!level.getBlockState(foot.position()).is(Blocks.COBWEB),"Damaged floor was promoted to support");
                    compared++;
                    if(level.getBlockState(foot.position().below()).isAir())unsupportedGaps++;
                }
                damagedFeet+=result.damagedFeet();
                var realization=foundations(level,view,chunks,result,negativeControls==0);
                foundationWrites+=realization[0];negativeControls+=realization[1];
                retained.clear();
                for(var foot:result.feet()) {
                    retained.put(foot.position(),level.getBlockState(foot.position()));
                    retained.put(foot.position().below(),level.getBlockState(foot.position().below()));
                }
                int unfilledDamage=0;
                for(int x=bounds.minX();x<=bounds.maxX();x++)for(int z=bounds.minZ();z<=bounds.maxZ();z++) {
                    var at=new BlockPos(x,700,z);
                    if(level.getBlockState(at).is(Blocks.COBWEB)) {
                        check(level.getBlockState(at.below()).isAir(),"Authored damaged foot was filled");
                        retained.put(at,level.getBlockState(at));retained.put(at.below(),level.getBlockState(at.below()));unfilledDamage++;
                    }
                }
                var row=new JsonObject();row.addProperty("element",element.toString());row.addProperty("rotation",rotation.name());
                row.addProperty("rawFeet",result.rawFeet());row.addProperty("processedFeet",result.feet().size());
                row.addProperty("damagedFeet",result.damagedFeet());row.addProperty("physicalDamageLeftUnfilled",unfilledDamage);rows.add(row);
                if(!budgetRefused) {
                    var budget=new VillageFootprint.Budget();VillageFootprint.Result last=null;
                    for(int n=0;n<=VillageFootprint.Budget.MAX_PIECES;n++) {
                        last=VillageFootprint.compile(piece,e,reference,manager,view,budget,null);
                        if(last.status()==VillageFootprint.Status.BUDGET)break;
                        check(last.qualified(),"Budget control encountered an unrelated refusal");
                    }
                    check(last.status()==VillageFootprint.Status.BUDGET && last.feet().isEmpty(),"Shared budget published partial feet");
                    budgetRefused=true;
                }
            }
            check(damagedFeet>0,"Actual RuleProcessor damage control did not remove any feet");
            check(unsupportedGaps>0,"Diagnostic fixture did not retain the original shallow-contact failure");
            report.addProperty("comparedPhysicalFeet",compared);report.addProperty("damagedFeetNotPromoted",damagedFeet);
            report.addProperty("originalUnsupportedGaps",unsupportedGaps);report.addProperty("readOnlySnapshotControls",viewControls);
            report.addProperty("sharedBudgetRefused",budgetRefused);
            report.addProperty("foundationWrites",foundationWrites);report.addProperty("foundationNegativeControls",negativeControls);
            report.addProperty("pieceGroundDeltaControls",elements.size()*Rotation.values().length*2);
            report.addProperty("scope","Native processed feet and current-chunk shallow writer with authored source ownership. Does not qualify natural fitting, full layout, lifecycle capture or feature reservations.");
            var persisted=new JsonArray();var ops=net.minecraft.resources.RegistryOps.create(com.mojang.serialization.JsonOps.INSTANCE,level.registryAccess());
            for(var entry:retained.entrySet()) {
                var row=new JsonObject();row.addProperty("x",entry.getKey().getX());row.addProperty("y",entry.getKey().getY());row.addProperty("z",entry.getKey().getZ());
                row.add("state",net.minecraft.world.level.block.state.BlockState.CODEC.encodeStart(ops,entry.getValue()).getOrThrow());persisted.add(row);
            }
            report.add("persistedBlocks",persisted);report.addProperty("reopened",false);
            report.addProperty("complete",true);
            var saved=new JsonObject();saved.addProperty("schema",1);saved.add("report",report.deepCopy());Files.writeString(receipt,saved.toString());
        } catch(Throwable failure) { report.addProperty("complete",false);report.addProperty("failure",failure.toString());throw failure; }
        finally { Files.writeString(output,new GsonBuilder().setPrettyPrinting().create().toJson(report)); }
    }

    private static IslandContext context(int top,boolean cavity) {
        var island=new org.sathrek.sky_archipelago.worldgen.generator.field.model.IslandDescriptor(
                org.sathrek.sky_archipelago.worldgen.generator.field.model.IslandFamily.ANCHOR_PLATEAU,8,top-28,8,
                200,200,200,0,org.sathrek.sky_archipelago.worldgen.generator.field.IslandShapeArchetype.CLASSIC,
                28,top-664,48,0,0,16,16,.5,42L);
        var body=new IslandContext.Body(island,new org.sathrek.sky_archipelago.worldgen.generator.field.TerrainColumn(664,top),
                new GeologicalFrame(664,top+1),top,false);
        return new IslandContext(List.of(body),(owner,y)->!owner.contains(y)?IslandContext.Occupancy.OUTSIDE:
                cavity && y>696 && y<top?IslandContext.Occupancy.CAVITY:IslandContext.Occupancy.SOLID);
    }
    private static int[] foundations(net.minecraft.server.level.ServerLevel level,StructureTerrainView view,
            List<ChunkAccess> chunks,VillageFootprint.Result result,boolean negatives) {
        var natural=context(696,false);
        var plan=VillageFoundation.plan(result.feet(),view,(x,z)->natural,3);
        check(plan.bindings().stream().allMatch(VillageFoundation.Binding::supported),"Authored shallow fixture was not supported");
        if(plan.bindings().isEmpty())return new int[]{0,0};
        int controls=0,writes=0;var first=plan.bindings().getFirst();var at=first.foot().position();var chunk=level.getChunk(at);
        var one=new VillageFoundation.Plan(List.of(first));
        if(negatives) {
            var expected=level.getBlockState(at);chunk.setBlockState(at,Blocks.AIR.defaultBlockState(),false);
            var refusal=VillageFoundation.realize(one,chunk);check(refusal.writes()==0 && refusal.outcomes().get(first.foot())==VillageFoundation.Outcome.CHANGED_FOOT,"Changed foot acquired support");
            chunk.setBlockState(at,expected,false);controls++;
            for(var post:List.of(Blocks.OAK_FENCE.defaultBlockState(),Blocks.COBBLESTONE_WALL.defaultBlockState())) {
                var postFoot=new VillageFootprint.Foot(first.foot().template(),104,at,first.foot().original(),post);
                chunk.setBlockState(at,post,false);
                var postPlan=VillageFoundation.plan(List.of(postFoot),view,(x,z)->natural,3);
                var supported=VillageFoundation.realize(postPlan,chunk);
                check(supported.writes()==3 && supported.outcomes().values().stream().allMatch(VillageFoundation::successful)
                        && chunk.getBlockState(at).equals(post),"Narrow native structural post lost contact or changed shape");
                for(var reset:first.fills())chunk.setBlockState(reset.position(),reset.original(),false);controls++;
            }
            boolean rejected=false;
            try {new VillageFootprint.Foot(first.foot().template(),104,at,first.foot().original(),Blocks.LANTERN.defaultBlockState()
                    .setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.HANGING,true));}
            catch(IllegalArgumentException expectedRejection) {rejected=true;}
            check(rejected,"A hanging fixture became an architectural support post");controls++;
            chunk.setBlockState(at,expected,false);
            chunk.setBlockState(first.source(),Blocks.BRICKS.defaultBlockState(),false);
            refusal=VillageFoundation.realize(one,chunk);check(refusal.writes()==0 && refusal.outcomes().get(first.foot())==VillageFoundation.Outcome.CHANGED_SOURCE,"Foreign source acquired support");
            chunk.setBlockState(first.source(),first.sourceState(),false);controls++;
            var fill=first.fills().getFirst();chunk.setBlockState(fill.position(),Blocks.BRICKS.defaultBlockState(),false);
            refusal=VillageFoundation.realize(one,chunk);check(refusal.writes()==0 && refusal.outcomes().get(first.foot())==VillageFoundation.Outcome.CHANGED_GAP,"Changed gap was overwritten");
            chunk.setBlockState(fill.position(),fill.original(),false);controls++;
            chunk.setBlockState(fill.position(),Blocks.GRASS_BLOCK.defaultBlockState(),false);
            refusal=VillageFoundation.realize(one,chunk);
            check(refusal.writes()==0 && refusal.outcomes().get(first.foot())==VillageFoundation.Outcome.CHANGED_GAP,"Unattributed natural-looking gap acquired write permission");controls++;
            var raised=view.overlay(List.of(new VillageGrading.Cell(fill.position(),fill.original(),Blocks.GRASS_BLOCK.defaultBlockState())));
            var prepared=VillageFoundation.afterGrading(one,raised);
            var completed=VillageFoundation.realize(prepared,chunk);
            check(completed.writes()==3 && completed.outcomes().get(first.foot())==VillageFoundation.Outcome.SHALLOW_GAP,
                    "Own prepared grading was mistaken for a foreign gap write");
            for(var reset:first.fills())chunk.setBlockState(reset.position(),reset.original(),false);controls++;
            var overlayFoot=new VillageFootprint.Foot(first.foot().template(),101,at,first.foot().original(),Blocks.BRICKS.defaultBlockState());
            var overlayBinding=new VillageFoundation.Binding(overlayFoot,first.outcome(),first.source(),first.sourceState(),first.owner(),first.fills());
            var overlapping=new VillageFoundation.Plan(List.of(first,overlayBinding));
            var output=new VillageFoundation.NativeOutput("own",101,Blocks.BRICKS.defaultBlockState());
            var resolved=VillageFoundation.finalNativeFeet(overlapping,Map.of(at,output),piece->"own");
            check(resolved.bindings().stream().allMatch(binding->binding.foot().expected().is(Blocks.BRICKS)),"Final native foot did not resolve same-start overlap");
            chunk.setBlockState(at,Blocks.BRICKS.defaultBlockState(),false);
            var overlapResult=VillageFoundation.realize(resolved,chunk);
            check(overlapResult.writes()==3 && overlapResult.outcomes().size()==2
                    && overlapResult.outcomes().values().stream().allMatch(VillageFoundation::successful),"Resolved co-owned native feet lost physical contact");
            for(var reset:first.fills())chunk.setBlockState(reset.position(),reset.original(),false);
            chunk.setBlockState(at,expected,false);controls++;
            for(var invalid:List.of(new VillageFoundation.NativeOutput("foreign",101,Blocks.BRICKS.defaultBlockState()),
                    new VillageFoundation.NativeOutput("own",102,Blocks.BRICKS.defaultBlockState()),
                    new VillageFoundation.NativeOutput("own",101,Blocks.COBBLESTONE.defaultBlockState()))) {
                var retained=VillageFoundation.finalNativeFeet(overlapping,Map.of(at,invalid),piece->"own");
                check(retained.bindings().getFirst().foot().equals(first.foot()),"Foreign/non-foot/unmatched overlay changed required footing");controls++;
            }
            check(VillageFoundation.finalNativeFeet(one,Map.of(at,output),piece->"own").equals(one),
                    "Uncompiled writer acquired final-foot authority");controls++;
            var clearOutput=new VillageFoundation.NativeOutput("own",101,Blocks.AIR.defaultBlockState(),true);
            var cleared=VillageFoundation.finalNativeFeet(one,Map.of(at,clearOutput),piece->"own");
            check(cleared.bindings().getFirst().outcome()==VillageFoundation.Outcome.AUTHORED_CLEAR
                    && cleared.bindings().getFirst().fills().isEmpty() && cleared.bindings().getFirst().source()==null,
                    "Authored clearing retained support/write authority");
            chunk.setBlockState(at,Blocks.AIR.defaultBlockState(),false);
            var clearResult=VillageFoundation.realize(cleared,chunk);
            check(clearResult.writes()==0 && clearResult.outcomes().get(first.foot())==VillageFoundation.Outcome.AUTHORED_CLEAR
                    && !VillageFoundation.successful(clearResult.outcomes().get(first.foot())),"Cleared foot became physical support");controls++;
            var clearEntry=new GroundSupportProofs.Entry(first.foot(),VillageFoundation.Outcome.AUTHORED_CLEAR,GroundSupportProofs.State.RETIRED,
                    List.of(new GroundSupportProofs.Expected(at,Blocks.AIR.defaultBlockState())),null);
            check(GroundSupportProofs.inspect(clearEntry,chunk)==GroundSupportProofs.State.RETIRED,"Authored air disposition not retained");
            chunk.setBlockState(at,expected,false);
            check(GroundSupportProofs.inspect(clearEntry,chunk)==GroundSupportProofs.State.INVALIDATED,"Changed authored air was not invalidated");controls++;
            for(var invalid:List.of(new VillageFoundation.NativeOutput("foreign",101,Blocks.AIR.defaultBlockState(),true),
                    new VillageFoundation.NativeOutput("own",first.foot().piece(),Blocks.AIR.defaultBlockState(),true),
                    new VillageFoundation.NativeOutput("own",101,Blocks.AIR.defaultBlockState()))) {
                check(VillageFoundation.finalNativeFeet(one,Map.of(at,invalid),piece->"own").equals(one),
                        "Foreign/same-piece/unattributed air retired a required footing");controls++;
            }
            var changedClear=VillageFoundation.realize(cleared,chunk);
            check(changedClear.writes()==0 && changedClear.outcomes().get(first.foot())==VillageFoundation.Outcome.CHANGED_FOOT,
                    "Changed physical clearing retained disposition");controls++;
            var path=Blocks.DIRT_PATH.defaultBlockState();var pathOutput=new VillageFoundation.NativeOutput("own",102,path);
            chunk.setBlockState(first.source(),path,false);
            for(var invalid:List.of(new VillageFoundation.NativeOutput("foreign",102,path),
                    new VillageFoundation.NativeOutput("own",102,Blocks.GRASS_BLOCK.defaultBlockState()))) {
                var denied=VillageFoundation.realize(one,chunk,Map.of(first.source(),invalid),piece->"own");
                check(denied.writes()==0 && denied.outcomes().get(first.foot())==VillageFoundation.Outcome.CHANGED_SOURCE,
                        "Foreign or unmatched path became a foundation source");controls++;
            }
            var roadFoot=new VillageFootprint.Foot(first.foot().template(),102,first.source(),BlockPos.ZERO,path);
            var underRoad=first.source().below();var oldUnderRoad=chunk.getBlockState(underRoad);chunk.setBlockState(underRoad,Blocks.DIRT.defaultBlockState(),false);
            var road=new VillageFoundation.Binding(roadFoot,VillageFoundation.Outcome.CONTACT,underRoad,Blocks.DIRT.defaultBlockState(),first.owner(),List.of());
            var pathPlan=new VillageFoundation.Plan(List.of(first,road));
            var protectedRoad=VillageFoundation.realize(pathPlan,chunk,Map.of(first.source(),pathOutput),piece->piece==102?"other":"own");
            check(protectedRoad.writes()==0 && chunk.getBlockState(first.source()).is(Blocks.DIRT_PATH),"Covering support erased a foreign start's road footing");controls++;
            var covered=VillageFoundation.realize(pathPlan,chunk,Map.of(first.source(),pathOutput),piece->"own");
            check(covered.writes()==4 && covered.outcomes().values().stream().allMatch(VillageFoundation::successful)
                    && covered.finalFeet().get(roadFoot).is(Blocks.DIRT) && chunk.getBlockState(first.source()).is(Blocks.DIRT),
                    "Covered native path did not realize dirt with matching final road requirements");controls++;
            for(var reset:first.fills())chunk.setBlockState(reset.position(),reset.original(),false);
            chunk.setBlockState(first.source(),path,false);
            var snow=Blocks.SNOW.defaultBlockState().setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.LAYERS,8);
            var exposedFoot=new VillageFootprint.Foot(first.foot().template(),103,first.source().above(),BlockPos.ZERO,snow);
            chunk.setBlockState(exposedFoot.position(),snow,false);
            check(!snow.isSolid() && path.canSurvive(level,first.source()),"Uncovered-path control disagrees with native survival policy");
            var exposed=new VillageFoundation.Binding(exposedFoot,VillageFoundation.Outcome.CONTACT,first.source(),first.sourceState(),first.owner(),List.of());
            var unburied=VillageFoundation.realize(new VillageFoundation.Plan(List.of(exposed)),chunk,Map.of(first.source(),pathOutput),piece->"own");
            check(unburied.writes()==0 && unburied.outcomes().get(exposedFoot)==VillageFoundation.Outcome.CHANGED_SOURCE,
                    "An uncovered path was treated as a full-height sturdy source");controls++;
            chunk.setBlockState(exposedFoot.position(),Blocks.AIR.defaultBlockState(),false);
            chunk.setBlockState(first.source(),first.sourceState(),false);chunk.setBlockState(underRoad,oldUnderRoad,false);
            var cavity=VillageFoundation.plan(List.of(first.foot()),view,(x,z)->context(704,true),3);
            check(cavity.bindings().getFirst().outcome()==VillageFoundation.Outcome.CAVITY,"Cavity floor became a foundation pillar");controls++;
            var unknown=VillageFoundation.plan(List.of(first.foot()),view,(x,z)->IslandContext.EMPTY,3);
            check(unknown.bindings().getFirst().outcome()==VillageFoundation.Outcome.UNKNOWN,"Missing owner became valid terrain");controls++;
            var clipped=VillageFoundation.plan(List.of(first.foot()),view,(x,z)->context(704,false),3);
            check(clipped.bindings().getFirst().supported(),"Captured open cap below nominal exterior was refused");controls++;
            var nativeCap=view.overlay(List.of(new VillageGrading.Cell(at,Blocks.AIR.defaultBlockState(),Blocks.STONE.defaultBlockState())));
            var capped=VillageFoundation.plan(List.of(first.foot()),nativeCap,(x,z)->context(at.getY(),false),3);
            check(capped.bindings().getFirst().supported(),"Exact native footing replacement was mistaken for an untouched cave roof");controls++;
            var roofed=view.overlay(List.of(new VillageGrading.Cell(first.source().above(7),Blocks.AIR.defaultBlockState(),Blocks.STONE.defaultBlockState())));
            var closed=VillageFoundation.plan(List.of(first.foot()),roofed,(x,z)->context(704,false),3);
            check(closed.bindings().getFirst().outcome()==VillageFoundation.Outcome.UNKNOWN,"Physical cave roof was mistaken for open exterior");controls++;
            var bodies=new ArrayList<>(context(704,false).bodies());bodies.addAll(context(705,false).bodies());
            var overlap=new IslandContext(bodies,(body,y)->body.contains(y)?IslandContext.Occupancy.SOLID:IslandContext.Occupancy.OUTSIDE);
            var competing=VillageFoundation.plan(List.of(first.foot()),view,(x,z)->overlap,3);
            check(competing.bindings().getFirst().outcome()==VillageFoundation.Outcome.OCCUPIED,"Other contributor's nominal cap was erased");controls++;
            int sourceY=first.source().getY(),footY=first.foot().position().getY();
            check(design.inherent.skyweavecompat.terrain.structure.VillageSupportEnvelope.gap(natural,sourceY,footY)
                    ==design.inherent.skyweavecompat.terrain.structure.VillageSupportEnvelope.Gap.OPEN,"Open exterior gap lost eligibility");controls++;
            check(design.inherent.skyweavecompat.terrain.structure.VillageSupportEnvelope.gap(context(704,false),sourceY,footY)
                    ==design.inherent.skyweavecompat.terrain.structure.VillageSupportEnvelope.Gap.CAP,"Nominal cap became unconditional fill authority");controls++;
            var waterContext=new IslandContext(context(704,false).bodies(),(body,y)->y>sourceY && body.contains(y)
                    ?IslandContext.Occupancy.WATER:body.contains(y)?IslandContext.Occupancy.SOLID:IslandContext.Occupancy.OUTSIDE);
            check(design.inherent.skyweavecompat.terrain.structure.VillageSupportEnvelope.gap(waterContext,sourceY,footY)
                    ==design.inherent.skyweavecompat.terrain.structure.VillageSupportEnvelope.Gap.WATER,"Natural shallow water became supported ground");controls++;
            check(design.inherent.skyweavecompat.terrain.structure.VillageSupportEnvelope.gap(context(704,true),sourceY,footY)
                    ==design.inherent.skyweavecompat.terrain.structure.VillageSupportEnvelope.Gap.CAVITY,"Cavity received early support credit");controls++;
            check(design.inherent.skyweavecompat.terrain.structure.VillageSupportEnvelope.gap(overlap,sourceY,footY)
                    ==design.inherent.skyweavecompat.terrain.structure.VillageSupportEnvelope.Gap.OCCUPIED,"Overlapping contributor received early support credit");controls++;
            check(design.inherent.skyweavecompat.terrain.structure.VillageSupportEnvelope.gap(IslandContext.EMPTY,sourceY,footY)
                    ==design.inherent.skyweavecompat.terrain.structure.VillageSupportEnvelope.Gap.UNKNOWN,"Unknown envelope received fill authority");controls++;
            chunk.setBlockState(fill.position(),Blocks.WATER.defaultBlockState(),false);
            var wetView=StructureTerrainView.capture(level.getSeed(),level.registryAccess(),chunks);
            var wet=VillageFoundation.plan(List.of(first.foot()),wetView,(x,z)->natural,3);
            check(wet.bindings().getFirst().outcome()==VillageFoundation.Outcome.WATER,"Water was eligible for filling");
            chunk.setBlockState(fill.position(),fill.original(),false);controls++;
            var other=new VillageFootprint.Foot(first.foot().template(),101,at,first.foot().original(),first.foot().expected());
            var badFills=first.fills().stream().map(f->new VillageFoundation.Fill(f.position(),f.original(),Blocks.STONE.defaultBlockState())).toList();
            var conflict=new VillageFoundation.Binding(other,first.outcome(),first.source(),first.sourceState(),first.owner(),badFills);
            refusal=VillageFoundation.realize(new VillageFoundation.Plan(List.of(first,conflict)),chunk);
            check(refusal.writes()==0 && refusal.outcomes().values().stream().allMatch(v->v==VillageFoundation.Outcome.CONFLICT),"Conflicting owners wrote a partial fill");controls++;
            var shared=new VillageFoundation.Binding(other,first.outcome(),first.source(),first.sourceState(),first.owner(),first.fills());
            var coOwned=VillageFoundation.realize(new VillageFoundation.Plan(List.of(first,shared)),chunk);
            check(coOwned.writes()==3 && coOwned.outcomes().size()==2,"Compatible co-owners did not share one write");writes+=coOwned.writes();controls++;
        }
        for(var local:chunks) {
            var realized=VillageFoundation.realize(plan,local);writes+=realized.writes();
            check(realized.outcomes().values().stream().allMatch(v->v==VillageFoundation.Outcome.CONTACT || v==VillageFoundation.Outcome.SHALLOW_GAP),"Native realization refused an unchanged fixture");
        }
        check(writes==result.feet().size()*3,"Unexpected shallow fill extent: "+writes+" / "+result.feet().size());
        for(var foot:result.feet())check(level.getBlockState(foot.position().below()).isFaceSturdy(level,foot.position().below(),Direction.UP),"Native foot has no physical contact after realization");
        if(negatives) {
            var actual=StructureTerrainView.capture(level.getSeed(),level.registryAccess(),chunks).height(Heightmap.Types.WORLD_SURFACE_WG,at.getX(),at.getZ());
            var stale=chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.WORLD_SURFACE_WG);stale.setRawData(chunk,Heightmap.Types.WORLD_SURFACE_WG,new long[stale.getRawData().length]);
            var replay=VillageFoundation.realize(plan,chunk);check(replay.writes()==0,"Idempotent realization rewrote matching fills");
            check(chunk.getHeight(Heightmap.Types.WORLD_SURFACE_WG,at.getX()&15,at.getZ()&15)+1==actual,"Replay retained a stale heightmap");controls++;
        }
        return new int[]{writes,controls};
    }
}
