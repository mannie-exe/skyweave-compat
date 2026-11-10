package design.inherent.skyweavecompat.probe;

import com.google.gson.*;
import java.nio.file.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.pieces.PiecesContainer;
import net.minecraft.world.level.levelgen.structure.pools.SinglePoolElement;
import design.inherent.skyweavecompat.terrain.structure.*;

/** Realize layouts in the reported clusters and inspect their physical foundations. */
final class VillageRepairProbe {
    private record Witness(String name,int x,int z) {}
    private static final List<Witness> WITNESSES=List.of(new Witness("west",-601,13280),
            new Witness("savanna",10155,6419),new Witness("north",8846,12728),new Witness("lagoon",9375,14868));
    private VillageRepairProbe() {}
    static void run(MinecraftServer server,Path output) throws Exception {
        var level=server.overworld();
        var distribution=((StructurePlacementState)level.getChunkSource().getGeneratorState()).skyweave$structureDistribution();
        var report=new JsonObject();report.addProperty("seed",level.getSeed());report.addProperty("complete",false);
        boolean candidate=candidate();report.addProperty("candidate",candidate);
        if (candidate) {
            var envelopes = searchEnvelopes(level, distribution);
            report.add("searchEnvelopes", envelopes);
            if (envelopes.get("unqualifiedVillages").getAsInt() != 0) {
                write(output, report);
                throw new AssertionError("Village search envelopes were not qualified; inspect searchEnvelopes");
            }
        }
        report.add("effectiveStructurePolicy",new Gson().toJsonTree(StructureConfiguration.policy()));
        var rows=new JsonArray();report.add("witnesses",rows);
        var method=StructureDistribution.class.getDeclaredMethod("plan",StructureSelection.Owner.class,StructureFamily.class);method.setAccessible(true);
        for(var witness:WITNESSES) {
            var row=new JsonObject();rows.add(row);row.addProperty("witness",witness.name());
            var owner=distribution.owner(witness.x(),witness.z());row.add("owner",new Gson().toJsonTree(owner));
            long began=System.nanoTime();
            var plan=(StructureDistribution.Plan)method.invoke(distribution,owner,StructureFamily.VILLAGES);
            row.addProperty("planningMillis",(System.nanoTime()-began)/1e6);
            var layouts=plan.layouts().getOrDefault(StructureFamily.VILLAGES,List.of());
            row.addProperty("fittedVillages",layouts.size());write(output,report);
            if(layouts.isEmpty())continue;
            var layout=layouts.getFirst();row.addProperty("structure",layout.id().toString());
            if (candidate) row.addProperty("inhabitedQuality", layout.quality());
            row.add("site",new Gson().toJsonTree(layout.site()));
            var chunks=new LinkedHashSet<ChunkPos>();
            for(var box:layout.pieces())for(int x=Math.floorDiv(box.minX(),16);x<=Math.floorDiv(box.maxX(),16);x++)
                for(int z=Math.floorDiv(box.minZ(),16);z<=Math.floorDiv(box.maxZ(),16);z++)chunks.add(new ChunkPos(x,z));
            if(chunks.size()>256)throw new AssertionError("Native witness exceeds 256 realization chunks");
            began=System.nanoTime();
            var source=level.getChunk(layout.site().chunkX(),layout.site().chunkZ(),ChunkStatus.FULL);
            for(var pos:chunks)level.getChunk(pos.x,pos.z,ChunkStatus.FULL);
            row.addProperty("fullChunks",chunks.size());row.addProperty("realizationMillis",(System.nanoTime()-began)/1e6);
            var structure=server.registryAccess().registryOrThrow(Registries.STRUCTURE).get(layout.id());
            var start=source.getStartForStructure(structure);
            if(start==null || !start.isValid())throw new AssertionError("Fitted village was not committed: "+layout.id());
            row.addProperty("actualPieces",start.getPieces().size());
            var foundations=new JsonArray();row.add("foundations",foundations);
            for(var piece:start.getPieces())if(piece instanceof PoolElementStructurePiece pool) audit(level,pool,foundations);
            row.add("aliasDiscovery",aliases(level,start,candidate));write(output,report);
            if (candidate) {
                row.add("terminators", VillageTerminatorProbe.inspect(distribution, start));
                write(output, report);
            }
        }
        var failures=new JsonArray();
        for(var item:rows) {
            var row=item.getAsJsonObject();
            if(row.get("fittedVillages").getAsInt()==0)failures.add(row.get("witness").getAsString()+": no replacement village");
            if(row.has("foundations"))for(var itemFloor:row.getAsJsonArray("foundations")) {
                var floor=itemFloor.getAsJsonObject();int count=floor.get("columns").getAsInt();
                if(count==0 || floor.get("supportedWithin3").getAsDouble()/count<.9)
                    failures.add(row.get("witness").getAsString()+": unsupported "+floor.get("template").getAsString());
            }
        }
        report.add("villageCounters",new Gson().toJsonTree(org.sathrek.sky_archipelago.worldgen.benchmark.StructureBenchmarkRecorder
                .report("witness",level.getSeed(),List.of(),Map.of()).summary().counters().entrySet().stream()
                .filter(entry->entry.getKey().startsWith("skyweaveVillage")).collect(java.util.stream.Collectors.toMap(Map.Entry::getKey,Map.Entry::getValue))));
        report.add("failures",failures);
        if (candidate && java.util.stream.StreamSupport.stream(rows.spliterator(), false)
                .map(JsonElement::getAsJsonObject).filter(row -> row.has("aliasDiscovery"))
                .noneMatch(row -> row.getAsJsonObject("aliasDiscovery").getAsJsonObject("locator")
                        .get("boundarySourceOutsideRadius").getAsBoolean()))
            failures.add("no outside-source/inside-marker radius boundary exercised");
        if (candidate && java.util.stream.StreamSupport.stream(rows.spliterator(), false)
                .map(JsonElement::getAsJsonObject).filter(row -> row.has("terminators"))
                .noneMatch(row -> row.getAsJsonObject("terminators").get("positiveCaps").getAsInt() > 0))
            failures.add("no supported native terminator exercised");
        boolean passed=candidate ? failures.isEmpty() : !failures.isEmpty();
        if(!candidate)report.addProperty("baselineDefectReproduced",passed);
        report.addProperty("complete",passed);write(output,report);
        if(!passed)throw new AssertionError("Native village foundation qualification failed: "+failures);
    }

    static void bounds(MinecraftServer server, Path output) throws Exception {
        var level = server.overworld();
        var distribution = ((StructurePlacementState)level.getChunkSource().getGeneratorState()).skyweave$structureDistribution();
        var report = searchEnvelopes(level, distribution);
        report.addProperty("scope", "Metadata qualification only; no candidate enumeration or physical structure verification");
        boolean passed = report.get("unqualifiedVillages").getAsInt() == 0;
        report.addProperty("complete", passed);
        write(output, report);
        if (!passed) throw new AssertionError("Village search envelopes were not qualified; inspect metadata receipt");
    }

    private static JsonObject searchEnvelopes(ServerLevel level, StructureDistribution distribution) throws Exception {
        var type = Class.forName("design.inherent.skyweavecompat.terrain.structure.StructureSearchBounds");
        var constructor = type.getDeclaredConstructor(ServerLevel.class);constructor.setAccessible(true);
        var measure = type.getDeclaredMethod("displacement", net.minecraft.world.level.levelgen.structure.Structure.class);measure.setAccessible(true);
        var metadata = constructor.newInstance(level);
        var result = new JsonObject();var qualified = new JsonObject();var unknown = new JsonArray();
        int villages = 0, unqualifiedVillages = 0;
        for (var holder : level.registryAccess().registryOrThrow(Registries.STRUCTURE).holders().toList()) {
            var family = distribution.family(holder.key().location());
            if (family == null) continue;
            var shift = (java.util.OptionalLong)measure.invoke(metadata, holder.value());
            if (shift.isPresent()) qualified.addProperty(holder.key().location().toString(), shift.getAsLong());
            else unknown.add(holder.key().location() + " (" + holder.value().getClass().getName() + ")");
            if (family == StructureFamily.VILLAGES) {
                villages++;
                if (shift.isEmpty()) unqualifiedVillages++;
            }
        }
        if (villages == 0) throw new AssertionError("No native village metadata control");
        result.addProperty("qualifiedVillages", villages);
        result.addProperty("unqualifiedVillages", unqualifiedVillages);
        result.addProperty("templatePoolCount", level.registryAccess().registryOrThrow(Registries.TEMPLATE_POOL).size());
        result.addProperty("allManagedTypesQualified", unknown.isEmpty());
        result.add("layoutDisplacementBlocks", qualified);
        result.add("unknownManagedTypes", unknown);
        return result;
    }
    static void material(MinecraftServer server,Path output)throws Exception {
        var level=server.overworld();
        for(int x=-807;x<=-805;x++)for(int z=200;z<=201;z++)level.getChunk(x,z,ChunkStatus.FULL);
        var writes=FoundationBlendWrites.snapshot();
        var report=new JsonObject();report.addProperty("seed",level.getSeed());report.add("effectiveStructurePolicy",new Gson().toJsonTree(StructureConfiguration.policy()));report.add("foundationWrites",new Gson().toJsonTree(writes));
        report.addProperty("biome",level.getBiome(new BlockPos(-12892,466,3216)).getRegisteredName());
        boolean patched;
        try{Class.forName("design.inherent.skyweavecompat.terrain.structure.VillageFitSettings");patched=true;}
        catch(ClassNotFoundException baseline){patched=false;}
        report.addProperty("candidate",patched);
        if (patched) report.add("paletteIsolation", FoundationMaterialProbe.inspect(level,
                ((StructurePlacementState)level.getChunkSource().getGeneratorState()).skyweave$structureDistribution()));
        long grass=writes.getOrDefault("minecraft:grass_block",0L),sand=writes.getOrDefault("minecraft:sand",0L);
        boolean passed=!writes.isEmpty() && (patched ? grass==0 && sand>0 : grass>0);
        report.addProperty("complete",passed);write(output,report);
        if(!passed)throw new AssertionError("Native foundation material witness did not qualify: "+writes);
    }
    private static void write(Path output,JsonObject report)throws Exception {
        Files.writeString(output,new GsonBuilder().setPrettyPrinting().create().toJson(report));
    }
    private static void audit(ServerLevel level,PoolElementStructurePiece piece,JsonArray rows)throws Exception {
        Object element=piece.getElement();
        for(int depth=0;!(element instanceof SinglePoolElement) && depth<8;depth++) {
            try{element=element.getClass().getMethod("delegate").invoke(element);}catch(NoSuchMethodException absent){return;}
        }
        if(!(element instanceof SinglePoolElement single))return;
        var locationField=SinglePoolElement.class.getDeclaredField("template");locationField.setAccessible(true);
        var either=(com.mojang.datafixers.util.Either<?,?>)locationField.get(single);
        String id=either.left().map(Object::toString).orElse("");
        if(!id.contains("/house") && !id.contains("/jobsite/"))return;
        var getter=SinglePoolElement.class.getDeclaredMethod("getTemplate",net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager.class);getter.setAccessible(true);
        var template=(net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate)getter.invoke(single,level.getStructureManager());
        var palettes=template.getClass().getDeclaredField("palettes");palettes.setAccessible(true);
        var columns=new HashMap<Long,BlockPos>();
        for(var palette:(List<net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.Palette>)palettes.get(template))for(var info:palette.blocks()) {
            var state=info.state();if(!state.blocksMotion() || !state.getFluidState().isEmpty() || state.is(net.minecraft.world.level.block.Blocks.JIGSAW)
                    || state.is(net.minecraft.world.level.block.Blocks.STRUCTURE_BLOCK) || state.is(net.minecraft.world.level.block.Blocks.STRUCTURE_VOID)
                    || state.is(net.minecraft.tags.BlockTags.LEAVES))continue;
            var p=info.pos();long key=BlockPos.asLong(p.getX(),0,p.getZ());var old=columns.get(key);
            if(old==null || old.getY()>p.getY())columns.put(key,p);
        }
        int base=columns.values().stream().mapToInt(BlockPos::getY).min().orElse(0), supported=0, count=0, missing=0, deepest=0;
        var placement=new net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings().setRotation(piece.getRotation());
        var gaps=new JsonArray();
        for(var local:columns.values())if(local.getY()<=base+2) {
            var pos=net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.calculateRelativePosition(placement,local).offset(piece.getPosition());
            count++;if(level.getBlockState(pos).isAir())missing++;
            int gap=0;while(gap<64) {
                var under=level.getBlockState(pos.below(gap+1));
                if(under.blocksMotion() && under.getFluidState().isEmpty() && !under.is(net.minecraft.tags.BlockTags.LEAVES))break;
                gap++;
            }
            if(gap<=3)supported++;deepest=Math.max(deepest,gap);
            if(gap>3 && gaps.size()<4)gaps.add(new Gson().toJsonTree(List.of(pos.getX(),pos.getY(),pos.getZ(),gap)));
        }
        var row=new JsonObject();row.addProperty("template",id);row.addProperty("columns",count);row.addProperty("supportedWithin3",supported);
        row.addProperty("missingFloor",missing);row.addProperty("maximumGap",deepest);row.add("gapExamples",gaps);rows.add(row);
    }
    private static boolean candidate() {
        try{Class.forName("design.inherent.skyweavecompat.terrain.structure.VillageFitSettings");return true;}
        catch(ClassNotFoundException baseline){return false;}
    }
    private static JsonObject aliases(ServerLevel level,StructureStart original,boolean candidate)throws Exception {
        var registry=level.registryAccess().registryOrThrow(Registries.STRUCTURE);var id=registry.getKey(original.getStructure());
        var serialization=net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext.fromLevel(level);
        var tags=original.getPieces().stream().map(p->p.createTag(serialization)).toList();
        var result=new JsonObject();
        var clone=new StructureStart(original.getStructure(),new ChunkPos(original.getChunkPos().x+1,original.getChunkPos().z+1),7,new PiecesContainer(original.getPieces()));
        if(candidate) {
            var key=PhysicalStructureIdentity.of(id,tags);var reversed=new ArrayList<>(tags);Collections.reverse(reversed);
            if(!key.equals(PhysicalStructureIdentity.of(id,reversed)) || !key.equals(PhysicalStructureIdentity.of(id,clone,serialization)))
                throw new AssertionError("Alias or piece order changed physical identity");
            var reloadedPieces = original.getPieces().stream().map(piece ->
                    piece.getClass() == PoolElementStructurePiece.class
                            ? (StructurePiece)new PoolElementStructurePiece(serialization,piece.createTag(serialization)) : piece).toList();
            var reloaded = new StructureStart(original.getStructure(),clone.getChunkPos(),7,new PiecesContainer(reloadedPieces));
            if (!key.equals(PhysicalStructureIdentity.of(id,reloaded,serialization))
                    || !PhysicalStructureIdentity.referenceBounds(original,serialization).equals(PhysicalStructureIdentity.referenceBounds(reloaded,serialization)))
                throw new AssertionError("Native jigsaw reload changed physical identity or reference bounds");
            result.addProperty("nativeReloadIdentityAndBoundsStable",true);
            result.addProperty("physicalAliasesEqual",true);
            result.add("locator",locatorAliases(level,original,clone));
        }
        var type=Class.forName("folk.sisby.surveyor.structure.WorldStructures");
        var records=type.getMethod("of",net.minecraft.world.level.Level.class).invoke(null,level);
        if(records!=null) {
            var put=type.getMethod("put",ServerLevel.class,StructureStart.class);put.invoke(records,level,original);put.invoke(records,level,clone);
            var exploration=Class.forName("folk.sisby.surveyor.SurveyorExploration");
            var map=(Map<?,?>)type.getMethod("asMap",exploration).invoke(records,new Object[]{null});
            var structureKey=registry.getResourceKey(original.getStructure()).orElseThrow();
            var raw=type.getMethod("get",net.minecraft.resources.ResourceKey.class,ChunkPos.class)
                    .invoke(records,structureKey,original.getChunkPos());
            var expected=summaryTags(raw);
            int aliases=0;
            for(var summary:((Map<?,?>)map.get(structureKey)).values())if(expected.equals(summaryTags(summary)))aliases++;
            if(candidate && aliases!=1 || !candidate && aliases<2)
                throw new AssertionError("Unexpected recorder alias presentation: "+aliases);
            result.addProperty("surveyorAliasesVisible",aliases);
        }
        return result;
    }
    private static List<net.minecraft.nbt.CompoundTag> summaryTags(Object summary)throws Exception {
        var result=new ArrayList<net.minecraft.nbt.CompoundTag>();
        for(var piece:(List<?>)summary.getClass().getMethod("getChildren").invoke(summary))
            result.add((net.minecraft.nbt.CompoundTag)piece.getClass().getMethod("toNbt").invoke(piece));
        result.sort(java.util.Comparator.comparing(Object::toString));return result;
    }

    private static JsonObject locatorAliases(ServerLevel level,StructureStart original,StructureStart alias)throws Exception {
        var locator=Class.forName("design.inherent.skyweavecompat.terrain.structure.StructureLocator");
        var jobType=Class.forName(locator.getName()+"$Job");var candidateType=Class.forName(locator.getName()+"$Candidate");
        var source=level.getServer().createCommandSourceStack().withLevel(level).withSuppressedOutput()
                .withPosition(net.minecraft.world.phys.Vec3.atCenterOf(original.getBoundingBox().getCenter()));
        var targets=locator.getDeclaredMethod("targets",net.minecraft.commands.CommandSourceStack.class,String.class);targets.setAccessible(true);
        var selection=targets.invoke(null,source,"structure="+level.registryAccess().registryOrThrow(Registries.STRUCTURE).getKey(original.getStructure()));
        var targetList=selection.getClass().getDeclaredMethod("targets");targetList.setAccessible(true);
        var selected=(List<?>)targetList.invoke(selection);
        var placementGetter=selected.getFirst().getClass().getDeclaredMethod("placements");placementGetter.setAccessible(true);
        var placements=(List<?>)placementGetter.invoke(selected.getFirst());
        var jobConstructor=jobType.getDeclaredConstructor(net.minecraft.commands.CommandSourceStack.class,int.class,List.class);jobConstructor.setAccessible(true);
        var candidateConstructor=candidateType.getDeclaredConstructor(ChunkPos.class,List.class,long.class);candidateConstructor.setAccessible(true);
        var inspect=locator.getDeclaredMethod("inspect",jobType,candidateType,net.minecraft.world.level.chunk.ChunkAccess.class,boolean.class);inspect.setAccessible(true);
        var seen=jobType.getDeclaredField("seen");seen.setAccessible(true);
        var matches=jobType.getDeclaredField("matches");matches.setAccessible(true);
        var enumerate=locator.getDeclaredMethod("enumerate",jobType);enumerate.setAccessible(true);
        var range=locator.getDeclaredMethod("inRange",jobType,ChunkPos.class);range.setAccessible(true);
        var sourceShift=jobType.getDeclaredField("sourceShift");sourceShift.setAccessible(true);
        var unbounded=jobType.getDeclaredField("unboundedTargets");unbounded.setAccessible(true);
        BlockPos firstPosition=null;
        var result=new JsonObject();
        for(boolean reverse:new boolean[]{false,true}) {
            var job=jobConstructor.newInstance(source,1024,selected);
            for(var start:reverse ? List.of(alias,original):List.of(original,alias)) {
                var chunk=new net.minecraft.world.level.chunk.ProtoChunk(start.getChunkPos(),net.minecraft.world.level.chunk.UpgradeData.EMPTY,
                        level,level.registryAccess().registryOrThrow(Registries.BIOME),null);
                chunk.setStartForStructure(start.getStructure(),start);
                inspect.invoke(null,job,candidateConstructor.newInstance(start.getChunkPos(),placements,0L),chunk,false);
            }
            if(((Set<?>)seen.get(job)).size()!=1 || ((List<?>)matches.get(job)).size()!=1)throw new AssertionError("Locator exposed duplicate aliases");
            var match=((List<?>)matches.get(job)).getFirst();var position=match.getClass().getDeclaredMethod("position");position.setAccessible(true);
            var actual=(BlockPos)position.invoke(match);
            if(firstPosition!=null && !firstPosition.equals(actual))throw new AssertionError("Alias discovery order changed result coordinates");
            firstPosition=actual;
            var different=new ArrayList<>(original.getPieces());
            for(int i=1;i<different.size();i++) {
                var trial=new ArrayList<>(different);trial.remove(i);
                var distinct=new StructureStart(original.getStructure(),alias.getChunkPos(),0,new PiecesContainer(trial));
                if(!distinct.getBoundingBox().equals(original.getBoundingBox()))continue;
                var chunk=new net.minecraft.world.level.chunk.ProtoChunk(alias.getChunkPos(),net.minecraft.world.level.chunk.UpgradeData.EMPTY,
                        level,level.registryAccess().registryOrThrow(Registries.BIOME),null);
                chunk.setStartForStructure(distinct.getStructure(),distinct);
                inspect.invoke(null,job,candidateConstructor.newInstance(alias.getChunkPos(),placements,0L),chunk,false);
                if(((Set<?>)seen.get(job)).size()!=2)throw new AssertionError("Locator merged different equal-bounds layouts");
                break;
            }
            if(((Set<?>)seen.get(job)).size()!=2)throw new AssertionError("No equal-bounds native negative control");
        }
        result.addProperty("physicalAliases",1);result.addProperty("differentEqualBoundsLayouts",2);result.addProperty("discoveryOrderStable",true);
        var boundarySource = source.withPosition(new net.minecraft.world.phys.Vec3(firstPosition.getX(), firstPosition.getY(), firstPosition.getZ()));
        var boundary = jobConstructor.newInstance(boundarySource, 1, selected);
        var enumeration = enumerate.invoke(null, boundary);
        var candidates = enumeration.getClass().getDeclaredMethod("candidates");candidates.setAccessible(true);
        var positionGetter = candidateType.getDeclaredMethod("position");positionGetter.setAccessible(true);
        boolean included = false;
        for (var item : (List<?>)candidates.invoke(enumeration))
            if (original.getChunkPos().equals(positionGetter.invoke(item))) included = true;
        if (!((Set<?>)unbounded.get(boundary)).isEmpty()) throw new AssertionError("Village layout envelope was not qualified: " + unbounded.get(boundary));
        if (!included || !(Boolean)range.invoke(null, boundary, original.getChunkPos()))
            throw new AssertionError("Canonical marker inside radius lost its source outside radius");
        var nativePosition = ((net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement)placements.getFirst())
                .getLocatePos(original.getChunkPos());
        long dx = (long)nativePosition.getX() - firstPosition.getX(), dz = (long)nativePosition.getZ() - firstPosition.getZ();
        result.addProperty("boundarySourceOutsideRadius", dx * dx + dz * dz > 1);
        result.addProperty("boundarySourceEnumerated", included);
        result.addProperty("sourceEnvelopeBlocks", sourceShift.getLong(boundary));
        return result;
    }

}
