package design.inherent.skyweavecompat.probe;

import com.google.gson.*;
import design.inherent.skyweavecompat.terrain.*;
import java.nio.file.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import org.sathrek.sky_archipelago.worldgen.generator.core.SkyIslandChunkGenerator;
import org.sathrek.sky_archipelago.worldgen.generator.field.internal.IslandDensityEvaluator;
import org.sathrek.sky_archipelago.worldgen.generator.field.model.ResolvedContributor;

/** Parent-frame equivalence and real native exposure; no terrain writes or synthetic hollow fill. */
final class FracturedHollowProbe {
    static void run(MinecraftServer server, Path output) throws Exception {
        long began=System.nanoTime();
        var level=server.overworld();
        var generator=(SkyIslandChunkGenerator)level.getChunkSource().getGenerator();
        var random=level.getChunkSource().randomState();
        var field=((GeologicalGenerator)(Object)generator).skyweave$field(random);
        var settings=generator.resolvedSkyIslandSettings();
        var planning=(InteriorField)(Object)field;
        var styles=((StyledTerrain)(Object)field).skyweave$styles();
        var profile=InteriorConfiguration.profile();
        var current=new IslandInteriors(random,profile,styles);
        var parentProfile=new InteriorProfile(0,1,profile.shellThickness(),profile.noiseHorizontalScale(),
                profile.noiseVerticalScale(),profile.passageWidth(),profile.chamberThreshold(),profile.hollowRadius(),
                profile.hollowHeight(),profile.openingChance(),profile.nativeCarvers(),profile.skylights());
        var parent=new IslandInteriors(random,parentProfile);
        var openParent=new IslandInteriors(random,new InteriorProfile(0,1,profile.shellThickness(),
                profile.noiseHorizontalScale(),profile.noiseVerticalScale(),profile.passageWidth(),
                profile.chamberThreshold(),profile.hollowRadius(),profile.hollowHeight(),1,
                profile.nativeCarvers(),profile.skylights()));
        var report=new JsonObject();var witnesses=new JsonArray();report.add("witnesses",witnesses);
        long checked=0,exposed=0,nearBoundary=0,cutCells=0,internalCells=0;
        int positive=0,negative=0,zero=0,removed=0,nativeAir=0,noiseStages=0,capChecks=0,bedGuards=0;
        int storedBiomeChecks=0,cavityOwnerChecks=0,finalCapChecks=0;
        record Shift(long seed,int height) {}
        var seen=new HashSet<Shift>();var protectedFrames=new HashSet<Shift>();
        var nativeChunks=new HashSet<Long>();
        for(int cx=-3;cx<=3;cx++)for(int cz=-3;cz<=3;cz++) {
            var members=((ClusterField)(Object)field).skyweave$members(cx,cz,settings);
            if(members.isEmpty())continue;
            var island=members.getFirst();
            if(island.archetype().waterBased() || styles.style(island).kind()==IslandStyles.Kind.NATIVE
                    || IslandInteriors.kind(island,profile,styles.style(island).kind())!=IslandInteriors.Kind.HOLLOW)continue;
            int reach=Math.min(96,(int)(island.maxRadius()*.45));
            for(int dx=-reach;dx<=reach;dx+=8)for(int dz=-reach;dz<=reach;dz+=8) {
                int x=island.centerX()+dx,z=island.centerZ()+dz;
                var exterior=planning.skyweave$planningColumn(x,z,settings,TerrainStage.EXTERIOR);
                var interior=planning.skyweave$planningColumn(x,z,settings,TerrainStage.INTERIOR);
                var contributors=((LayeredColumn)(Object)exterior).skyweave$contributors();
                for(var contributor:contributors) {
                    if(contributor.descriptor().seed()!=island.seed())continue;
                    var body=contributor.preparedDensity().landBody();
                    if(!body.exists()){removed++;continue;}
                    var frame=((ParentLandBody)(Object)contributor.preparedDensity()).skyweave$parentLandBody();
                    if(frame==null)throw new AssertionError("Missing admitted parent frame");
                    var identity=new Shift(island.seed(),frame.shift());
                    if(seen.add(identity)) {
                        if(frame.shift()>0)positive++;else if(frame.shift()<0)negative++;else zero++;
                    }
                    var unshifted=new ResolvedContributor(contributor.terrainPlan(),contributor.horizontal(),
                            contributor.terrainProfile(),new IslandDensityEvaluator.PreparedDensity(
                                    contributor.preparedDensity().waterArchetype(),frame.parent()),
                            contributor.densityEnvelope(),frame.parentY(contributor.minY()),frame.parentY(contributor.maxY()));
                    // Deep Dark always admits its existing entrances, independently of
                    // the ordinary HOLLOW opening roll. Compare the same parent policy.
                    var expected=(styles.style(island).kind()==IslandStyles.Kind.DEEP_DARK?openParent:parent)
                            .slice(x,z,unshifted,null);
                    var actual=current.slice(x,z,contributor,null);
                    var remnant=FragmentedExterior.sample(island.seed(),contributor.horizontal().localX(),
                            contributor.horizontal().localZ(),styles.profile().fractures());
                    boolean cutNearby=false;
                    for(int offset:new int[]{-8,-4,-2,2,4,8}) {
                        if(!FragmentedExterior.sample(island.seed(),contributor.horizontal().localX()+offset,
                                contributor.horizontal().localZ(),styles.profile().fractures()).exists()
                                || !FragmentedExterior.sample(island.seed(),contributor.horizontal().localX(),
                                contributor.horizontal().localZ()+offset,styles.profile().fractures()).exists())cutNearby=true;
                    }
                    for(int y=Math.max(GenerationProfile.MIN_Y,body.bottomY());y<=Math.min(GenerationProfile.MAX_Y-1,body.topY());y++) {
                        checked++;
                        if(actual.retains(y)!=expected.retains(frame.parentY(y)))
                            throw new AssertionError("Parent/remnant cavity disagreement at "+x+","+y+","+z);
                        if(actual.retains(y))continue;
                        exposed++;
                        if(protectedFrames.add(identity)) {
                            final int floor=y;
                            var guarded=current.slice(x,z,contributor,(host,bx,bz,outer)->floor);
                            if(!guarded.retains(y) || guarded.retains(y-1)!=actual.retains(y-1)
                                    || guarded.retains(body.topY()+1))
                                throw new AssertionError("Physical water-bed protection moved into the parent frame");
                            bedGuards++;
                        }
                        if(remnant.conservativeClearance()>=profile.shellThickness())continue;
                        nearBoundary++;
                        if(cutNearby)cutCells++;else internalCells++;
                        final int py=y;
                        boolean otherSolid=contributors.stream().anyMatch(other -> {
                            if(other==contributor)return false;
                            var slice=current.slice(x,z,other,null);return slice!=null&&slice.retains(py);
                        });
                        if(otherSolid || !cutNearby)continue;
                        if(interior.segments().stream().anyMatch(span->span.contains(py)))
                            throw new AssertionError("Union filled an exposed parent chamber");
                        long chunk=net.minecraft.world.level.ChunkPos.asLong(x>>4,z>>4);
                        if(nativeChunks.size()>=6 || !nativeChunks.add(chunk))continue;
                        var pos=new BlockPos(x,y,z);
                        var stages=new JsonObject();
                        var finalSolid=field.sampleSolidSegments(x,z,settings);
                        for(var status:List.of(ChunkStatus.NOISE,ChunkStatus.SURFACE,ChunkStatus.FULL)) {
                            var generated=level.getChunk(x>>4,z>>4,status);
                            var observed=new JsonObject();stages.add(status.toString(),observed);
                            observed.addProperty("actualStatus",generated.getPersistedStatus().toString());
                            observed.addProperty("atCut",generated.getBlockState(pos).toString());
                            observed.addProperty("floor",generated.getBlockState(new BlockPos(x,body.bottomY(),z)).toString());
                            observed.addProperty("roof",generated.getBlockState(new BlockPos(x,body.topY(),z)).toString());
                            if(!generated.getBlockState(pos).isAir())
                                throw new AssertionError("Native "+status+" resealed fracture: "+pos);
                            var storedBiome=generated.getNoiseBiome(x>>2,y>>2,z>>2);
                            var projectedBiome=generator.projectedBiomeSource().getNoiseBiome(x>>2,y>>2,z>>2,random.sampler());
                            if(!storedBiome.equals(projectedBiome))
                                throw new AssertionError("Stored/projected biome disagreement at fracture: "+pos);
                            observed.addProperty("storedBiome",storedBiome.getRegisteredName());storedBiomeChecks++;
                            if(status==ChunkStatus.NOISE && generated.getPersistedStatus()==ChunkStatus.NOISE) {
                                noiseStages++;
                                for(int cap:new int[]{body.bottomY(),body.topY()}) {
                                    if(!actual.retains(cap) || finalSolid.stream().noneMatch(span->span.contains(cap)))continue;
                                    if(generated.getBlockState(new BlockPos(x,cap,z)).isAir())
                                        throw new AssertionError("Native NOISE lost retained parent cap at "+x+","+cap+","+z);
                                    capChecks++;
                                }
                            }
                            if(status==ChunkStatus.FULL)for(int cap:new int[]{body.bottomY(),body.topY()}) {
                                if(!actual.retains(cap) || finalSolid.stream().noneMatch(span->span.contains(cap)))continue;
                                if(generated.getBlockState(new BlockPos(x,cap,z)).isAir())
                                    throw new AssertionError("Native FULL lost retained parent cap at "+x+","+cap+","+z);
                                finalCapChecks++;
                            }
                        }
                        var context=planning.skyweave$contextAt(x,z,settings);
                        if(context.solidOwner(y)!=null)
                            throw new AssertionError("Context still closes the exposed chamber");
                        if(context.contributions(y).stream().noneMatch(value->value.body().island().seed()==island.seed()
                                && value.occupancy()==IslandContext.Occupancy.CAVITY))
                            throw new AssertionError("Exposed chamber lost its cavity contributor");
                        cavityOwnerChecks++;
                        nativeAir++;
                        var row=new JsonObject();row.addProperty("x",x);row.addProperty("y",y);row.addProperty("z",z);
                        row.addProperty("shift",frame.shift());row.addProperty("clearance",remnant.conservativeClearance());
                        row.addProperty("nearActualRemoval",true);
                        row.addProperty("fullBlock",level.getBlockState(pos).toString());
                        row.add("stages",stages);
                        witnesses.add(row);
                    }
                }
            }
            if(nativeAir>=6 && positive>0 && negative>0 && checked>100000)break;
        }
        report.addProperty("checkedParentFrameCells",checked);report.addProperty("cavityCells",exposed);
        report.addProperty("cavityCellsNearCellBoundaries",nearBoundary);report.addProperty("positiveShifts",positive);
        report.addProperty("cavityCellsNearPhysicalCuts",cutCells);report.addProperty("cavityCellsNearUncutBoundaries",internalCells);
        report.addProperty("negativeShifts",negative);report.addProperty("zeroShifts",zero);
        report.addProperty("nativeAirWitnesses",nativeAir);report.addProperty("emptyContributors",removed);
        report.addProperty("actualNoiseWitnesses",noiseStages);report.addProperty("nativeRetainedCapChecks",capChecks);
        report.addProperty("physicalBedGuardChecks",bedGuards);
        report.addProperty("storedBiomeChecks",storedBiomeChecks);report.addProperty("cavityOwnerChecks",cavityOwnerChecks);
        report.addProperty("nativeFinalCapChecks",finalCapChecks);
        if(!witnesses.isEmpty()) {
            var witness=witnesses.get(0).getAsJsonObject();
            report.add("contextCache",ContextCacheProbe.run(level,witness.get("x").getAsInt(),witness.get("z").getAsInt()));
        }
        report.addProperty("elapsedSeconds",(System.nanoTime()-began)/1e9);
        report.addProperty("complete",cutCells>0&&internalCells>0&&nativeAir>0&&positive>0&&negative>0);
        Files.writeString(output,new GsonBuilder().setPrettyPrinting().create().toJson(report)+"\n");
        if(!report.get("complete").getAsBoolean())throw new AssertionError("Missing non-vacuous fracture controls: "+report);
    }
}
