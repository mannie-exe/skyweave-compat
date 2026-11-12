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
            new Witness("savanna",10155,6419),new Witness("north",8846,12728),new Witness("lagoon",9375,14868),
            new Witness("fortified",1904,-1488));
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
        var selected=System.getProperty("neighbors.probe.villageWitness","");
        var witnesses=WITNESSES.stream().filter(w->selected.isEmpty()?!w.name().equals("fortified"):w.name().equals(selected)).toList();
        if(witnesses.isEmpty())throw new IllegalArgumentException("Unknown village witness");
        report.addProperty("selectedWitness",selected);
        for(var witness:witnesses) {
            var row=new JsonObject();rows.add(row);row.addProperty("witness",witness.name());
            var owner=distribution.owner(witness.x(),witness.z());row.add("owner",new Gson().toJsonTree(owner));
            var sky=(org.sathrek.sky_archipelago.worldgen.generator.core.SkyIslandChunkGenerator)level.getChunkSource().getGenerator();
            var field=((design.inherent.skyweavecompat.terrain.GeologicalGenerator)(Object)sky).skyweave$field(level.getChunkSource().randomState());
            row.add("ownerMembers",new Gson().toJsonTree(((design.inherent.skyweavecompat.terrain.ClusterField)(Object)field)
                    .skyweave$members(owner.cellX(),owner.cellZ(),sky.resolvedSkyIslandSettings())));
            long began=System.nanoTime();
            var plan=(StructureDistribution.Plan)method.invoke(distribution,owner,StructureFamily.VILLAGES);
            row.addProperty("planningMillis",(System.nanoTime()-began)/1e6);
            row.add("selectionDiagnostics",selectionDiagnostics(distribution,owner));
            var layouts=plan.layouts().getOrDefault(StructureFamily.VILLAGES,List.of());
            row.addProperty("fittedVillages",layouts.size());write(output,report);
            if(layouts.isEmpty()) {
                if(witness.name().equals("lagoon"))row.add("fineRootSurvey",fineRootSurvey(level,distribution,owner));
                write(output,report);continue;
            }
            var layout=layouts.getFirst();row.addProperty("structure",layout.id().toString());
            if (candidate) row.addProperty("inhabitedQuality", layout.quality());
            row.add("site",new Gson().toJsonTree(layout.site()));
            var chunks=new LinkedHashSet<ChunkPos>();
            for(var box:layout.pieces())for(int x=Math.floorDiv(box.minX(),16);x<=Math.floorDiv(box.maxX(),16);x++)
                for(int z=Math.floorDiv(box.minZ(),16);z<=Math.floorDiv(box.maxZ(),16);z++)chunks.add(new ChunkPos(x,z));
            if(chunks.size()>256)throw new AssertionError("Native witness exceeds 256 realization chunks");
            began=System.nanoTime();
            var source=level.getChunk(layout.site().chunkX(),layout.site().chunkZ(),ChunkStatus.FULL);
            var realizationOrder=new ArrayList<>(chunks);
            if(Boolean.getBoolean("neighbors.probe.reverseStructures"))Collections.reverse(realizationOrder);
            for(var pos:realizationOrder)level.getChunk(pos.x,pos.z,ChunkStatus.FULL);
            row.addProperty("fullChunks",chunks.size());row.addProperty("realizationMillis",(System.nanoTime()-began)/1e6);
            var structure=server.registryAccess().registryOrThrow(Registries.STRUCTURE).get(layout.id());
            var start=source.getStartForStructure(structure);
            if(start==null || !start.isValid())throw new AssertionError("Fitted village was not committed: "+layout.id());
            row.addProperty("actualPieces",start.getPieces().size());
            row.add("doorNeighborhoodPreparation",VillageAccessProbe.prepareNeighborhoods(level,start));
            var foundations=new JsonArray();row.add("foundations",foundations);
            for(var piece:start.getPieces())if(piece instanceof PoolElementStructurePiece pool) audit(level,pool,foundations);
            row.add("processedFootEvidence",proofs(level,chunks));
            row.add("localDoorPaths",VillageAccessProbe.inspect(level,start));
            row.add("localConnectorPaths",VillageAccessProbe.inspectConnections(level,start));
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
        if(!selected.isEmpty()) {
            report.add("exactTerrainWrites",VillageTerrainTrace.snapshot());

        }
        if (candidate && selected.isEmpty() && java.util.stream.StreamSupport.stream(rows.spliterator(), false)
                .map(JsonElement::getAsJsonObject).filter(row -> row.has("aliasDiscovery"))
                .noneMatch(row -> row.getAsJsonObject("aliasDiscovery").getAsJsonObject("locator")
                        .get("boundarySourceOutsideRadius").getAsBoolean()))
            failures.add("no outside-source/inside-marker radius boundary exercised");
        if (candidate && selected.isEmpty() && java.util.stream.StreamSupport.stream(rows.spliterator(), false)
                .map(JsonElement::getAsJsonObject).filter(row -> row.has("terminators"))
                .noneMatch(row -> row.getAsJsonObject("terminators").get("positiveCaps").getAsInt() > 0))
            failures.add("no supported native terminator exercised");
        boolean passed=candidate ? failures.isEmpty() : !failures.isEmpty();
        if(!candidate)report.addProperty("baselineDefectReproduced",passed);
        report.addProperty("complete",passed);write(output,report);
        if(!passed)throw new AssertionError("Native village foundation qualification failed: "+failures);
    }
    @SuppressWarnings("unchecked")
    private static JsonObject selectionDiagnostics(StructureDistribution distribution,StructureSelection.Owner owner) throws Exception {
        var choose=StructureDistribution.class.getDeclaredMethod("choice",StructureSelection.Owner.class,StructureFamily.class);choose.setAccessible(true);
        var choice=choose.invoke(distribution,owner,StructureFamily.VILLAGES);
        var result=new Gson().toJsonTree(choice).getAsJsonObject();
        var sampled=StructureDistribution.class.getDeclaredMethod("samples",StructureSelection.Owner.class);sampled.setAccessible(true);
        var samples=(java.util.Map<StructureSelection.Site,?>)sampled.invoke(distribution,owner);
        var environmentField=StructureDistribution.class.getDeclaredField("environment");environmentField.setAccessible(true);
        var environment=(org.sathrek.sky_archipelago.worldgen.generator.structure.StructurePlacementPassContext)environmentField.get(distribution);
        if(environment.biomeSource()!=environment.generator().projectedBiomeSource())
            throw new AssertionError("Early assembly froze a biome source different from native createStructures");
        result.addProperty("nativeAssemblyBiomeSourceMatches",true);
        var rows=new JsonArray();result.add("rankedRootColumns",rows);
        var nativePlacement=environment.registryAccess().registryOrThrow(Registries.STRUCTURE).holders()
                .flatMap(holder->environment.structureState().getPlacementsForStructure(holder).stream())
                .filter(placement->placement instanceof ArchipelagoStructurePlacement adapted && adapted.family()==StructureFamily.VILLAGES)
                .map(placement->(ArchipelagoStructurePlacement)placement).findFirst().orElseThrow();
        var ranked=choice.getClass().getDeclaredMethod("ranked");ranked.setAccessible(true);
        for(var site:((List<StructureSelection.Site>)ranked.invoke(choice)).stream().limit(32).toList()) {
            if(!nativePlacement.getPotentialStructureChunk(environment.levelSeed(),site.chunkX(),site.chunkZ())
                    .equals(new ChunkPos(site.chunkX(),site.chunkZ())))throw new AssertionError("Candidate disagrees with native village lattice");
            var sample=samples.get(site);var top=sample.getClass().getDeclaredMethod("topY");top.setAccessible(true);
            int y=(int)top.invoke(sample)+1,x=site.chunkX()*16+8,z=site.chunkZ()*16+8;
            var context=((design.inherent.skyweavecompat.terrain.InteriorField)(Object)environment.islandField())
                    .skyweave$contextAt(x,z,environment.settings());
            var row=new JsonObject();rows.add(row);row.add("site",new Gson().toJsonTree(site));row.addProperty("footY",y);
            var topColumn=((design.inherent.skyweavecompat.terrain.InteriorField)(Object)environment.islandField())
                    .skyweave$planningColumn(x,z,environment.settings(),design.inherent.skyweavecompat.terrain.TerrainStage.EXTERIOR);
            row.addProperty("exteriorArchetype",topColumn.dominantDescriptor()==null?null:topColumn.dominantDescriptor().archetype().name());
            row.addProperty("localWater",topColumn.waterArchetype().water());
            row.addProperty("biome",environment.biomeSource().getNoiseBiome(x>>2,y>>2,z>>2,environment.randomState().sampler()).getRegisteredName());
            row.addProperty("wet",context.contributions(y).stream().anyMatch(value->value.occupancy()==design.inherent.skyweavecompat.terrain.IslandContext.Occupancy.WATER));
            int dry=0,existing=0;
            for(int dx=-16;dx<=16;dx+=8)for(int dz=-16;dz<=16;dz+=8) {
                var point=((design.inherent.skyweavecompat.terrain.InteriorField)(Object)environment.islandField())
                        .skyweave$planningColumn(x+dx,z+dz,environment.settings(),design.inherent.skyweavecompat.terrain.TerrainStage.EXTERIOR);
                if(point.segments().isEmpty())continue;
                existing++;
                if(!point.waterArchetype().containsWaterAt(point.segments().getFirst().topY()+1))dry++;
            }
            row.addProperty("nearbyExistingOf25",existing);row.addProperty("nearbyDryOf25",dry);
        }
        result.addProperty("nativeLatticeMatches",true);
        return result;
    }
    /** Research only: find whether sparse roots miss a viable shore before changing production candidacy. */
    @SuppressWarnings("unchecked")
    private static JsonObject fineRootSurvey(ServerLevel level,StructureDistribution distribution,StructureSelection.Owner owner) throws Exception {
        var sampleMethod=StructureDistribution.class.getDeclaredMethod("samples",StructureSelection.Owner.class);sampleMethod.setAccessible(true);
        var coarse=(java.util.Map<StructureSelection.Site,?>)sampleMethod.invoke(distribution,owner);
        var sampleType=Class.forName("design.inherent.skyweavecompat.terrain.structure.StructureDistribution$Sample");
        var constructor=sampleType.getDeclaredConstructors()[0];constructor.setAccessible(true);
        var eligible=StructureDistribution.class.getDeclaredMethod("eligible",StructureFamily.class,StructureSelection.Site.class,sampleType);eligible.setAccessible(true);
        var environmentField=StructureDistribution.class.getDeclaredField("environment");environmentField.setAccessible(true);
        var environment=(org.sathrek.sky_archipelago.worldgen.generator.structure.StructurePlacementPassContext)environmentField.get(distribution);
        var field=(design.inherent.skyweavecompat.terrain.InteriorField)(Object)environment.islandField();
        var queried=new java.util.HashMap<Long,org.sathrek.sky_archipelago.worldgen.generator.field.model.ColumnData>();
        java.util.function.BiFunction<Integer,Integer,org.sathrek.sky_archipelago.worldgen.generator.field.model.ColumnData> column=(x,z)->
                queried.computeIfAbsent(FoundationSupport.key(x,z),key->field.skyweave$planningColumn(x,z,environment.settings(),design.inherent.skyweavecompat.terrain.TerrainStage.EXTERIOR));
        record Score(int dry,int existing,int relief) {}
        var scores=new java.util.LinkedHashMap<StructureSelection.Site,Score>();
        int loaded=level.getChunkSource().getLoadedChunksCount();
        for(var entry:coarse.entrySet()) {
            if(!(boolean)eligible.invoke(distribution,StructureFamily.VILLAGES,entry.getKey(),entry.getValue()))continue;
            int cx=Math.floorDiv(entry.getKey().chunkX(),8)*8,cz=Math.floorDiv(entry.getKey().chunkZ(),8)*8;
            for(int dx=0;dx<8;dx++)for(int dz=0;dz<8;dz++) {
                var site=new StructureSelection.Site(cx+dx,cz+dz);int x=site.chunkX()*16+8,z=site.chunkZ()*16+8;
                var center=column.apply(x,z);if(center.segments().isEmpty() || center.dominantDescriptor()==null
                        || !owner.equals(distribution.owner(center.dominantDescriptor())))continue;
                int top=center.segments().getFirst().topY();
                if(center.waterArchetype().containsWaterAt(top+1)
                        || !(boolean)eligible.invoke(distribution,StructureFamily.VILLAGES,site,constructor.newInstance(top,center.dominantDescriptor())))continue;
                int dry=0,existing=0,min=Integer.MAX_VALUE,max=Integer.MIN_VALUE;
                for(int ox=-16;ox<=16;ox+=8)for(int oz=-16;oz<=16;oz+=8) {
                    var point=column.apply(x+ox,z+oz);if(point.segments().isEmpty())continue;
                    existing++;int y=point.segments().getFirst().topY();
                    if(!point.waterArchetype().containsWaterAt(y+1)){dry++;min=Math.min(min,y);max=Math.max(max,y);}
                }
                scores.put(site,new Score(dry,existing,dry==0?Integer.MAX_VALUE:max-min));
            }
        }
        var result=new JsonObject();result.addProperty("eligibleDryChunkRoots",scores.size());result.addProperty("metadataColumns",queried.size());
        var ranked=StructureSelection.ranked(environment.levelSeed(),owner,StructureFamily.VILLAGES,scores.keySet()).stream()
                .sorted(java.util.Comparator.<StructureSelection.Site>comparingInt(site->scores.get(site).dry()).reversed()
                        .thenComparingInt(site->scores.get(site).relief())).limit(8).toList();
        var entriesField=StructureDistribution.class.getDeclaredField("familyEntries");entriesField.setAccessible(true);
        var entries=(java.util.Map<StructureFamily,List<net.minecraft.world.level.levelgen.structure.StructureSet.StructureSelectionEntry>>)entriesField.get(distribution);
        var planner=Class.forName("design.inherent.skyweavecompat.terrain.structure.VillageLayoutPlanning");
        var fit=planner.getDeclaredMethod("fit",StructureDistribution.class,org.sathrek.sky_archipelago.worldgen.generator.structure.StructurePlacementPassContext.class,StructureSelection.Site.class,List.class);fit.setAccessible(true);
        var attempts=new JsonArray();result.add("fitTrials",attempts);
        for(var site:ranked) {
            var row=new JsonObject();attempts.add(row);row.add("site",new Gson().toJsonTree(site));row.add("score",new Gson().toJsonTree(scores.get(site)));
            var layout=(StructureLayout)fit.invoke(null,distribution,environment,site,entries.get(StructureFamily.VILLAGES));
            row.addProperty("accepted",layout!=null);
            if(layout!=null){row.addProperty("id",layout.id().toString());row.addProperty("pieces",layout.pieces().size());row.addProperty("quality",layout.quality());break;}
        }
        result.addProperty("scope","Metadata and uncommitted native layout fits only; no starts, references, chunks or blocks generated");
        if(loaded!=level.getChunkSource().getLoadedChunksCount())throw new AssertionError("Fine root research loaded chunks");
        result.addProperty("loadedChunksUnchanged",true);
        return result;
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
    private static JsonObject proofs(ServerLevel level,Set<ChunkPos> chunks) {
        var result=new JsonObject();var states=new TreeMap<String,Integer>();var examples=new JsonArray();int absent=0;
        var observed=new ArrayList<String>();
        var exampleCounts=new EnumMap<VillageFoundation.Outcome,Integer>(VillageFoundation.Outcome.class);
        var generator=(org.sathrek.sky_archipelago.worldgen.generator.core.SkyIslandChunkGenerator)level.getChunkSource().getGenerator();
        var field=((design.inherent.skyweavecompat.terrain.GeologicalGenerator)(Object)generator).skyweave$field(level.getChunkSource().randomState());
        for(var cp:chunks) {
            var chunk=level.getChunkSource().getChunkNow(cp.x,cp.z);
            if(chunk==null)throw new AssertionError("Realized witness chunk unexpectedly unavailable");
            var proof=GroundSupportProofs.existing(level,chunk);if(proof==null) { absent++;continue; }
            for(var entry:proof.entries()) {
                var state=GroundSupportProofs.inspect(entry,chunk);states.merge(state+":"+entry.outcome(),1,Integer::sum);
                observed.add(cp+":"+entry+":"+state+":"+chunk.getBlockState(entry.foot().position()));
                for(var required:entry.required())observed.add(cp+":"+required.position()+":"+chunk.getBlockState(required.position()));
                if(state!=GroundSupportProofs.State.VERIFIED && exampleCounts.getOrDefault(entry.outcome(),0)<4) {
                    exampleCounts.merge(entry.outcome(),1,Integer::sum);var row=new JsonObject();row.addProperty("entry",entry.toString());
                    row.addProperty("actualFoot",chunk.getBlockState(entry.foot().position()).toString());var below=new JsonArray();
                    var context=((design.inherent.skyweavecompat.terrain.InteriorField)(Object)field).skyweave$contextAt(
                            entry.foot().position().getX(),entry.foot().position().getZ(),generator.resolvedSkyIslandSettings());
                    var bodies=new JsonArray();
                    for(var body:context.bodies()) {
                        var owner=new JsonObject();owner.addProperty("bottom",body.exterior().bottomY());owner.addProperty("top",body.exterior().topY());
                        owner.addProperty("climateDatum",body.climateDatum());bodies.add(owner);
                    }
                    row.add("intrinsicBodies",bodies);
                    for(int depth=1;depth<=8 && entry.foot().position().getY()-depth>=chunk.getMinBuildHeight();depth++) {
                        var at=entry.foot().position().below(depth);var cell=new JsonObject();cell.addProperty("y",at.getY());cell.addProperty("actual",chunk.getBlockState(at).toString());
                        cell.add("intrinsic",new Gson().toJsonTree(context.contributions(at.getY()).stream().map(c->c.occupancy().name()).toList()));below.add(cell);
                    }
                    row.add("below",below);examples.add(row);
                }
            }
        }
        result.add("perFootStates",new Gson().toJsonTree(states));result.addProperty("chunksWithoutPreparedFootEvidence",absent);
        observed.sort(String::compareTo);
        try {
            var digest=java.security.MessageDigest.getInstance("SHA-256");
            for(var value:observed) {digest.update(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));digest.update((byte)'\n');}
            result.addProperty("savedFootingDigest",HexFormat.of().formatHex(digest.digest()));
        } catch(java.security.NoSuchAlgorithmException impossible) {throw new AssertionError(impossible);}
        result.add("unverifiedExamples",examples);result.addProperty("wholeLayoutCertified",false);return result;
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
        int base=columns.values().stream().mapToInt(BlockPos::getY).min().orElse(0), supported=0, count=0, missing=0, deepest=0,contact=0;
        var placement=new net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings().setRotation(piece.getRotation());
        var gaps=new JsonArray();var immediateGaps=new JsonArray();
        for(var local:columns.values())if(local.getY()<=base+2) {
            var pos=net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.calculateRelativePosition(placement,local).offset(piece.getPosition());
            count++;if(level.getBlockState(pos).isAir())missing++;
            int gap=0;while(gap<64) {
                var under=level.getBlockState(pos.below(gap+1));
                if(under.blocksMotion() && under.getFluidState().isEmpty() && !under.is(net.minecraft.tags.BlockTags.LEAVES))break;
                gap++;
            }
            if(gap<=3)supported++;deepest=Math.max(deepest,gap);
            if(!level.getBlockState(pos).isAir() && level.getBlockState(pos.below()).isFaceSturdy(level,pos.below(),net.minecraft.core.Direction.UP))contact++;
            else if(immediateGaps.size()<4) {
                var missingContact=new JsonObject();missingContact.addProperty("position",pos.toShortString());
                missingContact.addProperty("foot",level.getBlockState(pos).toString());missingContact.addProperty("below",level.getBlockState(pos.below()).toString());
                missingContact.addProperty("rawY",local.getY());missingContact.addProperty("declaredFootPlane",single.getGroundLevelDelta()-1);
                immediateGaps.add(missingContact);
            }
            if(gap>3 && gaps.size()<4)gaps.add(new Gson().toJsonTree(List.of(pos.getX(),pos.getY(),pos.getZ(),gap)));
        }
        var row=new JsonObject();row.addProperty("template",id);row.addProperty("columns",count);row.addProperty("supportedWithin3",supported);
        row.addProperty("missingFloor",missing);row.addProperty("maximumGap",deepest);row.add("gapExamples",gaps);rows.add(row);
        row.addProperty("rawImmediateContacts",contact);
        row.add("rawImmediateGapExamples",immediateGaps);
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
