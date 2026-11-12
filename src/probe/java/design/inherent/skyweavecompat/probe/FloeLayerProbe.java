package design.inherent.skyweavecompat.probe;

import com.AdamNew.dynamicwaters.HydrologyManager;
import com.google.gson.*;
import design.inherent.skyweavecompat.Feature;
import design.inherent.skyweavecompat.OptionalIntegrations;
import design.inherent.skyweavecompat.terrain.GeologicalGenerator;
import java.nio.file.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.*;
import net.minecraft.world.phys.AABB;
import org.sathrek.sky_archipelago.worldgen.generator.core.SkyIslandChunkGenerator;
import org.sathrek.sky_archipelago.worldgen.generator.field.model.IslandFamily;

/** Native cached route and spawner with authored water layers; not natural encounter frequency. */
final class FloeLayerProbe {
    private FloeLayerProbe() {}
    private static void check(boolean value,String message) {if(!value)throw new AssertionError(message);}
    @SuppressWarnings("unchecked")
    static void run(MinecraftServer server,Path output) throws Exception {
        var report=new JsonObject();report.addProperty("complete",false);
        var level=server.overworld();var generator=(SkyIslandChunkGenerator)level.getChunkSource().getGenerator();
        var field=((GeologicalGenerator)(Object)generator).skyweave$field(level.getChunkSource().randomState());
        var anchor=field.collectIslandPreviewsInRadius(0,0,8000,generator.resolvedSkyIslandSettings()).stream()
                .filter(a->a.family()==IslandFamily.ANCHOR_PLATEAU && !a.archetype().waterBased())
                .min(Comparator.comparingDouble(a->Math.hypot(a.x(),a.z()))).orElseThrow();
        BlockPos point=null;
        outer:for(int dx=-1;dx<=1;dx++)for(int dz=-1;dz<=1;dz++) {
            var paths=HydrologyManager.getMountainRegionPaths(Math.floorDiv(anchor.x(),256)+dx,
                    Math.floorDiv(anchor.z(),256)+dz,generator,level,level.getChunkSource().randomState());
            for(var path:paths)for(int i=0;i<path.validNodeCount;i+=2) {
                int x=(int)Math.floor(path.segmentsX[i]),y=path.segmentsY[i],z=(int)Math.floor(path.segmentsZ[i]);
                if(y<128 || y>level.getMaxBuildHeight()-48 || !HydrologyManager.isMountainRiverWater(x,y,z))continue;
                point=new BlockPos(x,y,z);break outer;
            }
        }
        check(point!=null,"No bounded real cached mountain-river node");
        final var at=point;
        for(int x=(at.getX()-8)>>4;x<=(at.getX()+8)>>4;x++)for(int z=(at.getZ()-8)>>4;z<=(at.getZ()+8)>>4;z++)level.getChunk(x,z);
        var biome=level.registryAccess().registryOrThrow(Registries.BIOME).getHolderOrThrow(Biomes.SNOWY_PLAINS);
        for(int x=(at.getX()-8)>>4;x<=(at.getX()+8)>>4;x++)for(int z=(at.getZ()-8)>>4;z<=(at.getZ()+8)>>4;z++)
            level.getChunk(x,z).fillBiomesFromNoise((qx,qy,qz,s)->biome,level.getChunkSource().randomState().sampler());
        for(int x=at.getX()-6;x<=at.getX()+6;x++)for(int z=at.getZ()-6;z<=at.getZ()+6;z++) {
            level.setBlock(new BlockPos(x,at.getY()-1,z),Blocks.STONE.defaultBlockState(),2);
            level.setBlock(new BlockPos(x,at.getY(),z),Blocks.WATER.defaultBlockState(),2);
            for(int y=at.getY()+1;y<level.getMaxBuildHeight();y++)level.setBlock(new BlockPos(x,y,z),Blocks.AIR.defaultBlockState(),2);
            level.setBlock(new BlockPos(x,at.getY()+32,z),Blocks.STONE.defaultBlockState(),2);
            level.setBlock(new BlockPos(x,at.getY()+33,z),Blocks.WATER.defaultBlockState(),2);
        }
        var type=(Class<? extends Entity>)Class.forName("com.AdamNew.dynamicwaters.entity.IceFloe");
        var area=new AABB(at).inflate(9,64,9);
        var spawner=Class.forName("com.AdamNew.dynamicwaters.events.IceFloeSpawner");
        var group=spawner.getDeclaredMethod("setGroupAfloat",ServerLevel.class,int.class,int.class,int.class,RandomSource.class);group.setAccessible(true);
        var rate=Class.forName("com.AdamNew.dynamicwaters.events.IceFloeSpawner$Rate");
        var constructor=rate.getDeclaredConstructor(int.class,int.class,int.class,int.class);constructor.setAccessible(true);
        var spawn=spawner.getDeclaredMethod("spawnNear",ServerLevel.class,double.class,double.class,rate);spawn.setAccessible(true);
        double centerX=at.getX()+32.5,centerZ=at.getZ()+.5;
        var nodes=HydrologyManager.mountainRiverNodesNear(centerX,centerZ,16,64);
        var framed=design.inherent.skyweavecompat.water.IslandWaterFloes.nodesNear(centerX,centerZ,16,64);
        check(framed.size()==nodes.size(),"Framed nodes changed native candidate weight");
        for(int i=0;i<nodes.size();i++)check(nodes.get(i)[0]==framed.get(i)[0] && nodes.get(i)[1]==framed.get(i)[1],
                "Framed nodes changed native order/coordinates");
        int index=-1;for(int i=0;i<nodes.size();i++)if((int)Math.floor(nodes.get(i)[0])==at.getX() && (int)Math.floor(nodes.get(i)[1])==at.getZ()){index=i;break;}
        check(index>=0,"Native node query did not retain chosen actual route");
        long seed=0;for(;seed<65536;seed++)if(RandomSource.create(seed).nextInt(nodes.size())==index)break;
        check(seed<65536,"No bounded native candidate draw");
        int loaded=level.getChunkSource().getLoadedChunksCount();
        try {
            report.addProperty("node",at.toShortString());report.addProperty("nativeNodes",nodes.size());
            report.addProperty("drawSeed",seed);report.addProperty("nodeIsRiver",HydrologyManager.isMountainRiverWater(at.getX(),at.getY(),at.getZ()));
            report.addProperty("upperLayerIsRiver",HydrologyManager.isMountainRiverWater(at.getX(),at.getY()+33,at.getZ()));
            report.addProperty("nativeHighestY",level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING,at.getX(),at.getZ())-1);
            boolean enabled=OptionalIntegrations.enabled(Feature.WATER_SPAWNS);report.addProperty("enabled",enabled);
            group.invoke(null,level,at.getX(),at.getY(),at.getZ(),RandomSource.create(42));
            var direct=level.getEntitiesOfClass(type,area);report.addProperty("directGroupCount",direct.size());
            for(var entity:direct)entity.discard();
            level.getRandom().setSeed(seed);
            spawn.invoke(null,level,centerX,centerZ,constructor.newInstance(1,1,1,64));
            var actual=level.getEntitiesOfClass(type,area);report.addProperty("nativeSpawnNearCount",actual.size());
            var ys=new JsonArray();for(var entity:actual)ys.add(entity.getY());report.add("spawnedY",ys);
            check(enabled?direct.size()>0 && !actual.isEmpty():direct.isEmpty() && actual.isEmpty(),"Native layered-water admission did not match selection: "+report);
            check(actual.stream().allMatch(e->Math.abs(e.getY()-at.getY())<2),"Spawner selected unrelated upper water");
            if(enabled) {
                var drifting=actual.getFirst();for(var entity:actual)if(entity!=drifting)entity.discard();
                var age=type.getDeclaredField("age");age.setAccessible(true);
                var lifetime=type.getDeclaredField("lifetime");lifetime.setAccessible(true);lifetime.setInt(drifting,100000);
                var before=drifting.position();
                var flow=HydrologyManager.getRiverFlow(before.x,(int)before.y,before.z,level);
                check(flow.horizontalDistanceSqr()>.0001,"Actual cold floe is outside native river flow");
                for(int i=0;i<40;i++)level.tickNonPassenger(drifting);
                var displacement=drifting.position().subtract(before);
                report.addProperty("coldDrift",displacement.toString());report.addProperty("initialFlow",flow.toString());
                check(!drifting.isRemoved() && displacement.horizontalDistanceSqr()>.000001
                        && displacement.x*flow.x+displacement.z*flow.z>0,"Native cold floe did not drift with the real current");
                int coldAge=age.getInt(drifting);report.addProperty("coldAgeAfter40Ticks",coldAge);
                var warm=level.registryAccess().registryOrThrow(Registries.BIOME).getHolderOrThrow(Biomes.TAIGA);
                check(warm.value().coldEnoughToSnow(at),"Warm-transition control does not reproduce native altitude cooling");
                report.addProperty("transitionBiome",warm.getRegisteredName());
                report.addProperty("nativeTransitionBiomeColdAtPhysicalY",true);
                for(int x=(at.getX()-8)>>4;x<=(at.getX()+8)>>4;x++)for(int z=(at.getZ()-8)>>4;z<=(at.getZ()+8)>>4;z++)
                    level.getChunk(x,z).fillBiomesFromNoise((qx,qy,qz,s)->warm,level.getChunkSource().randomState().sampler());
                lifetime.setInt(drifting,coldAge+100);
                int ticks=0;for(;ticks<40 && !drifting.isRemoved();ticks++)level.tickNonPassenger(drifting);
                report.addProperty("warmTicksToRemoval",ticks);report.addProperty("warmFinalAge",age.getInt(drifting));
                boolean climate=OptionalIntegrations.enabled(Feature.CLIMATE);report.addProperty("climateAdaptation",climate);
                check(drifting.isRemoved()==climate,"Floe lifetime did not match selected climate policy");
                if(!climate) {
                    check(age.getInt(drifting)==coldAge+40,"Disabled climate changed native cold aging");
                    drifting.discard();
                }
                for(int x=(at.getX()-8)>>4;x<=(at.getX()+8)>>4;x++)for(int z=(at.getZ()-8)>>4;z<=(at.getZ()+8)>>4;z++)
                    level.getChunk(x,z).fillBiomesFromNoise((qx,qy,qz,s)->biome,level.getChunkSource().randomState().sampler());
            }
            for(var entity:level.getEntitiesOfClass(type,area))entity.discard();
            // A solid block immediately over the selected fluid remains a physical obstruction.
            for(int x=at.getX()-6;x<=at.getX()+6;x++)for(int z=at.getZ()-6;z<=at.getZ()+6;z++)
                level.setBlock(new BlockPos(x,at.getY()+1,z),Blocks.STONE.defaultBlockState(),2);
            group.invoke(null,level,at.getX(),at.getY(),at.getZ(),RandomSource.create(42));
            level.getRandom().setSeed(seed);spawn.invoke(null,level,centerX,centerZ,constructor.newInstance(1,1,1,64));
            check(level.getEntitiesOfClass(type,area).isEmpty(),"Floe spawned in a blocked water cell");
            report.addProperty("immediateRoofRefused",true);
            for(int x=at.getX()-6;x<=at.getX()+6;x++)for(int z=at.getZ()-6;z<=at.getZ()+6;z++) {
                level.setBlock(new BlockPos(x,at.getY()+1,z),Blocks.AIR.defaultBlockState(),2);
                level.setBlock(new BlockPos(x,at.getY(),z),Blocks.WATER.defaultBlockState().setValue(LiquidBlock.LEVEL,8),2);
            }
            group.invoke(null,level,at.getX(),at.getY(),at.getZ(),RandomSource.create(42));
            level.getRandom().setSeed(seed);spawn.invoke(null,level,centerX,centerZ,constructor.newInstance(1,1,1,64));
            check(level.getEntitiesOfClass(type,area).isEmpty(),"Floe spawned in falling water");report.addProperty("fallingWaterRefused",true);
            check(design.inherent.skyweavecompat.water.IslandWaterFloes.nodesNear(Double.NaN,centerZ,16,64).isEmpty()
                    && design.inherent.skyweavecompat.water.IslandWaterFloes.nodesNear(centerX,centerZ,16,65).isEmpty(),"Unbounded node query was admitted");
            check(design.inherent.skyweavecompat.terrain.LocalWater.surfaceNear(level,10000000,10000000,at.getY(),20)==null,
                    "Unloaded water column was admitted");report.addProperty("unavailableAndInvalidRefused",true);
            report.addProperty("chunksBefore",loaded);report.addProperty("chunksAfter",level.getChunkSource().getLoadedChunksCount());
            check(loaded==level.getChunkSource().getLoadedChunksCount(),"Floe query created chunks");
            if(OptionalIntegrations.enabled(Feature.DYNAMIC_WATERS_SABLE)) {
                report.addProperty("beforeExplicitShipFixtureChunks",level.getChunkSource().getLoadedChunksCount());
                report.add("sableCurrents",HydrologySableProbe.observe(level,at));
                report.addProperty("afterExplicitShipFixtureChunks",level.getChunkSource().getLoadedChunksCount());
            }
            var foreign=server.getLevel(net.minecraft.world.level.Level.END);foreign.getChunk(0,0);
            for(int x=2;x<=14;x++)for(int z=2;z<=14;z++) {
                foreign.setBlock(new BlockPos(x,63,z),Blocks.STONE.defaultBlockState(),2);
                foreign.setBlock(new BlockPos(x,64,z),Blocks.WATER.defaultBlockState(),2);
                for(int y=65;y<foreign.getMaxBuildHeight();y++)foreign.setBlock(new BlockPos(x,y,z),Blocks.AIR.defaultBlockState(),2);
                foreign.setBlock(new BlockPos(x,96,z),Blocks.STONE.defaultBlockState(),2);
                foreign.setBlock(new BlockPos(x,97,z),Blocks.WATER.defaultBlockState(),2);
            }
            int foreignLoaded=foreign.getChunkSource().getLoadedChunksCount();
            group.invoke(null,foreign,8,64,8,RandomSource.create(42));
            check(foreign.getEntitiesOfClass(type,new AABB(0,60,0,16,110,16)).isEmpty(),"Foreign dimension lost native highest-column policy");
            check(foreignLoaded==foreign.getChunkSource().getLoadedChunksCount(),"Foreign floe query loaded chunks");
            report.addProperty("foreignHighestColumnPreserved",true);
            report.addProperty("scope","Actual cached native route/spawnNear/group, 40 cold native entity ticks and bounded warm lifetime; authored two-layer water/biomes, not natural encounter frequency or hydrology rewrite");
            report.addProperty("complete",true);
        } catch(Exception | AssertionError failure) {report.addProperty("error",failure.toString());throw failure;}
        finally {
            for(var entity:level.getEntitiesOfClass(type,area))entity.discard();
            Files.writeString(output,new GsonBuilder().setPrettyPrinting().create().toJson(report));
        }
    }
}
