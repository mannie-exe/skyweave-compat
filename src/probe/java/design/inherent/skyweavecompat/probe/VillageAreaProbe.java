package design.inherent.skyweavecompat.probe;

import com.google.gson.*;
import design.inherent.skyweavecompat.mixin.VillageTemplateAccess;
import design.inherent.skyweavecompat.terrain.*;
import design.inherent.skyweavecompat.terrain.structure.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.*;
import net.minecraft.world.level.levelgen.structure.pools.SinglePoolElement;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.sathrek.sky_archipelago.worldgen.generator.core.SkyIslandChunkGenerator;
import org.sathrek.sky_archipelago.worldgen.generator.field.model.IslandDescriptor;

/** Read-only, stage-labelled coverage estimate. A footprint lower bound is not a whole-village certificate. */
final class VillageAreaProbe {
    private record Pose(ResourceLocation template,BlockPos origin,Rotation rotation) {}
    private record Roof(int y,boolean usable,boolean unknown) {}
    private static final int MAX_ENTRIES=262144,MAX_QUERIES=131072;
    private static final class Limit extends RuntimeException {}
    private VillageAreaProbe() {}

    static JsonObject run(ServerLevel level,StructureStart start) {
        var result=new JsonObject();long began=System.nanoTime();
        result.addProperty("scope","Verified processed structural-footprint lower bound with exact native pose XYZ attribution on one contributor, divided by a deterministic sampled intrinsic usable-roof estimate; projection-displaced feet are omitted. Not final developed area, global frequency or a template-fit promise");
        result.addProperty("terrainStage","Intrinsic pre-watershed geometry: solid roof, no intrinsic water, two blocks of natural headroom; rivers, vegetation, final decoration and slope suitability are not resolved by this denominator");
        int loaded=level.getChunkSource().getLoadedChunksCount();
        try {
            var generator=(SkyIslandChunkGenerator)level.getChunkSource().getGenerator();
            var settings=generator.resolvedSkyIslandSettings();
            var field=(InteriorField)(Object)((GeologicalGenerator)(Object)generator).skyweave$field(level.getChunkSource().randomState());
            int[] contextQueries={0};
            java.util.function.BiFunction<Integer,Integer,IslandContext> contexts=(x,z)->{
                if(contextQueries[0]++>=MAX_QUERIES)throw new Limit();
                return field.skyweave$contextAt(x,z,settings);
            };
            var poses=new ArrayList<Pose>();Pose court=null;
            for(var piece:start.getPieces()) {
                if(!(piece instanceof PoolElementStructurePiece pool))continue;
                var element=pool.getElement();
                for(int i=0;element instanceof VillageDelegate delegate && i<8;i++)element=delegate.skyweave$delegate();
                if(!(element instanceof SinglePoolElement single))continue;
                var id=((VillageTemplateAccess)single).skyweave$template().left().orElse(null);if(id==null)continue;
                var pose=new Pose(id,pool.getPosition(),pool.getRotation());poses.add(pose);
                if(id.getNamespace().equals("skyweave_compat") && id.getPath().contains("/town_centers/"))court=pose;
            }
            if(court==null)throw new IllegalStateException("Missing original court for area attribution");
            var bounds=start.getBoundingBox();
            var verified=new ArrayList<GroundSupportProofs.Entry>();var states=new TreeMap<String,Integer>();
            var outcomes=new TreeMap<String,Integer>();var refusals=new JsonArray();
            int entries=0,proofChunks=0,missingChunks=0;
            for(int cx=bounds.minX()>>4;cx<=bounds.maxX()>>4;cx++)for(int cz=bounds.minZ()>>4;cz<=bounds.maxZ()>>4;cz++) {
                var chunk=level.getChunkSource().getChunkNow(cx,cz);
                if(chunk==null) {missingChunks++;continue;}
                var proof=GroundSupportProofs.existing(level,chunk);if(proof==null)continue;proofChunks++;
                for(var entry:proof.entries()) {
                    if(++entries>MAX_ENTRIES)throw new Limit();
                    var foot=entry.foot();
                    boolean matches=poses.stream().filter(p->p.template().equals(foot.template())).anyMatch(p->{
                        var at=StructureTemplate.transform(foot.original(),Mirror.NONE,p.rotation(),BlockPos.ZERO).offset(p.origin());
                        return at.equals(foot.position());
                    });
                    if(!matches)continue;
                    var current=GroundSupportProofs.inspect(entry,chunk);
                    states.merge(entry.state()+"/"+current,1,Integer::sum);
                    outcomes.merge(entry.outcome().name(),1,Integer::sum);
                    if(current!=GroundSupportProofs.State.VERIFIED && refusals.size()<16) {
                        var row=new JsonObject();row.addProperty("template",foot.template().toString());row.addProperty("at",foot.position().toShortString());
                        row.addProperty("outcome",entry.outcome().name());row.addProperty("state",current.name());refusals.add(row);
                    }
                    if(entry.state()==GroundSupportProofs.State.VERIFIED && current==GroundSupportProofs.State.VERIFIED)verified.add(entry);
                }
            }
            result.addProperty("proofChunks",proofChunks);result.addProperty("missingLoadedChunks",missingChunks);
            result.add("attributedFootStates",new Gson().toJsonTree(states));
            result.add("attributedFootOutcomes",new Gson().toJsonTree(outcomes));result.add("refusalExamples",refusals);
            Pose selectedCourt=court;
            var anchor=verified.stream().filter(e->e.foot().template().equals(selectedCourt.template())).findFirst().orElse(null);
            if(anchor==null) {result.addProperty("status","NO_VERIFIED_COURT_SOURCE");return result;}
            var source=anchor.required().get(1).position();
            var context=contexts.apply(source.getX(),source.getZ());
            var body=context.solidOwner(source.getY());if(body==null)body=context.owner(source.getY(),8);
            if(body==null) {result.addProperty("status","NO_HEIGHT_QUALIFIED_CONTRIBUTOR");return result;}
            IslandDescriptor host=body.island();result.add("contributor",new Gson().toJsonTree(host));
            var roofs=new HashMap<Long,Roof>();
            java.util.function.BiFunction<Integer,Integer,Roof> roof=(x,z)->{
                long key=net.minecraft.world.level.ChunkPos.asLong(x,z);var cached=roofs.get(key);if(cached!=null)return cached;
                if(roofs.size()==MAX_QUERIES)throw new Limit();
                var at=contexts.apply(x,z);
                var own=at.bodies().stream().filter(b->b.island().equals(host) && !b.fringe()).findFirst().orElse(null);
                Roof value;
                if(own==null)value=new Roof(0,false,false);
                else {
                    int top=own.exterior().topY();boolean unknown=false;
                    boolean usable=at.naturalAt(own,top)==IslandContext.Occupancy.SOLID;
                    if(at.naturalAt(own,top)==IslandContext.Occupancy.UNKNOWN)unknown=true;
                    for(var other:at.bodies())for(int dy=1;dy<=2;dy++) {
                        var occupied=at.naturalAt(other,top+dy);
                        if(occupied==IslandContext.Occupancy.UNKNOWN)unknown=true;
                        if(occupied==IslandContext.Occupancy.SOLID || occupied==IslandContext.Occupancy.WATER || occupied==IslandContext.Occupancy.UNKNOWN)usable=false;
                    }
                    value=new Roof(top,usable,unknown);
                }
                roofs.put(key,value);return value;
            };
            int radius=GenerationProfile.supportRadius(host.maxRadius()),spacing=16;
            while(Math.pow(2L*radius/spacing+1,2)>16384)spacing*=2;
            int samples=0,usable=0,unknown=0;
            for(int x=host.centerX()-radius+spacing/2;x<=host.centerX()+radius;x+=spacing)
                for(int z=host.centerZ()-radius+spacing/2;z<=host.centerZ()+radius;z+=spacing) {
                    var sample=roof.apply(x,z);samples++;if(sample.unknown())unknown++;if(sample.usable())usable++;
                }
            var footprint=new HashSet<Long>();
            var omittedSources=new TreeMap<String,Integer>();
            for(var entry:verified) {
                var naturalSource=entry.required().get(1).position();var at=contexts.apply(naturalSource.getX(),naturalSource.getZ());
                var contributor=at.solidOwner(naturalSource.getY());
                if(contributor==null || !contributor.island().equals(host) || contributor.fringe()) {
                    omittedSources.merge("other_or_unavailable_contributor",1,Integer::sum);continue;
                }
                var above=at.naturalAt(contributor,naturalSource.getY()+1);
                if(above==IslandContext.Occupancy.CAVITY || above==IslandContext.Occupancy.WATER || above==IslandContext.Occupancy.UNKNOWN) {
                    omittedSources.merge("non_exterior_source",1,Integer::sum);continue;
                }
                var point=entry.foot().position();var sample=roof.apply(point.getX(),point.getZ());
                if(sample.usable())footprint.add(net.minecraft.world.level.ChunkPos.asLong(point.getX(),point.getZ()));
            }
            result.addProperty("status",unknown==0 && usable>0?"ESTIMATED":"UNKNOWN_OR_EMPTY_DENOMINATOR");
            result.addProperty("gridSpacing",spacing);result.addProperty("gridSamples",samples);result.addProperty("unknownGridSamples",unknown);
            result.addProperty("queriedColumns",roofs.size());result.addProperty("verifiedFootprintColumns",footprint.size());
            result.addProperty("contextQueries",contextQueries[0]);result.add("omittedSourceCounts",new Gson().toJsonTree(omittedSources));
            long denominator=(long)usable*spacing*spacing;result.addProperty("usableRoofAreaEstimate",denominator);
            if(unknown==0 && denominator>0)result.addProperty("verifiedFootprintShareOfRoofEstimate",(double)footprint.size()/denominator);
            return result;
        } catch(Limit exhausted) {
            // No numeric area credit from an incomplete budgeted assessment.
            result=new JsonObject();result.addProperty("status","BUDGET");return result;
        } finally {
            if(loaded!=level.getChunkSource().getLoadedChunksCount())throw new AssertionError("Area inspection loaded chunks");
            result.addProperty("milliseconds",(System.nanoTime()-began)/1e6);
        }
    }
}
