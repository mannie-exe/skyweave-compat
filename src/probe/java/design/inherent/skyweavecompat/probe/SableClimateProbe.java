package design.inherent.skyweavecompat.probe;

import com.google.gson.*;
import design.inherent.skyweavecompat.climate.IslandClimate;
import design.inherent.skyweavecompat.terrain.*;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason;
import java.nio.file.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.block.Blocks;
import org.sathrek.sky_archipelago.worldgen.generator.core.SkyIslandChunkGenerator;

/** Actual assembled storage/parent coordinate evidence, independently of Dynamic Waters. */
final class SableClimateProbe {
    static void run(MinecraftServer server,Path output) throws Exception {
        var level=server.overworld();var generator=(SkyIslandChunkGenerator)level.getChunkSource().getGenerator();
        var field=((GeologicalGenerator)(Object)generator).skyweave$field(level.getChunkSource().randomState());
        var anchors=field.collectIslandPreviewsInRadius(0,0,8000,generator.resolvedSkyIslandSettings()).stream()
                .filter(p->p.family()==org.sathrek.sky_archipelago.worldgen.generator.field.model.IslandFamily.ANCHOR_PLATEAU)
                .sorted(Comparator.comparingDouble(p->Math.hypot(p.x(),p.z()))).toList();
        BlockPos physical=null;
        outer:for(var anchor:anchors)for(int dx=-1;dx<=1;dx++)for(int dz=-1;dz<=1;dz++) {
            int x=anchor.x()+dx*anchor.radius()/3,z=anchor.z()+dz*anchor.radius()/3;
            var context=((InteriorField)(Object)field).skyweave$contextAt(x,z,generator.resolvedSkyIslandSettings());
            for(var body:context.bodies()) {
                int y=body.exterior().topY()+16;
                var reference=context.climate().referenceY(y,design.inherent.skyweavecompat.climate.ClimateConfiguration.profile());
                if(reference!=null && reference!=64){physical=new BlockPos(x,y,z);break outer;}
            }
        }
        if(physical==null)throw new AssertionError("No bounded contributor-aware Sable climate witness");
        level.getChunk(physical);
        var blocks=new ArrayList<BlockPos>();
        for(int dx=0;dx<=8;dx++)for(int dz=0;dz<=1;dz++)blocks.add(physical.offset(dx,0,dz));
        for(var pos:blocks)level.setBlock(pos,Blocks.STONE.defaultBlockState(),2);
        var ship=SubLevelAssemblyHelper.assembleBlocks(level,physical,blocks,BoundingBox3i.from(blocks));
        var report=new JsonObject();report.addProperty("seed",level.getSeed());
        try {
            ship.forceUpdateGlobalBounds();
            var box=ship.getPlot().getBoundingBox();
            var local=new BlockPos(box.minX(),box.minY(),box.minZ());
            var localChunk=ship.getPlot().getChunk(ship.getPlot().toLocal(new net.minecraft.world.level.ChunkPos(local)));
            if(localChunk==null || !localChunk.getBlockState(local).is(Blocks.STONE))throw new AssertionError("Storage witness is not an occupied assembled block");
            if(Sable.HELPER.getContaining(level,local)!=ship)throw new AssertionError("Storage witness is not in its real plot");
            var global=BlockPos.containing(Sable.HELPER.projectOutOfSubLevel(level,local.getCenter()));
            if(Math.hypot((double)global.getX()-physical.getX(),(double)global.getZ()-physical.getZ())>8 || Math.abs(global.getY()-physical.getY())>1)
                throw new AssertionError("Storage witness did not map back near assembled craft");
            level.getChunk(global);
            int before=level.getChunkSource().getLoadedChunksCount();
            var expected=IslandClimate.temperaturePosition(level,global);
            var actual=IslandClimate.temperaturePosition(level,local);
            report.addProperty("physical",physical.toShortString());report.addProperty("storage",local.toShortString());
            report.addProperty("parent",global.toShortString());report.addProperty("expectedTemperature",expected.toShortString());
            report.addProperty("actualTemperature",actual.toShortString());report.addProperty("matchesParent",expected.equals(actual));
            report.addProperty("idempotent",IslandClimate.temperaturePosition(level,actual)==actual);
            report.addProperty("chunksBefore",before);report.addProperty("chunksAfter",level.getChunkSource().getLoadedChunksCount());
            report.addProperty("dynamicWatersPresent",design.inherent.skyweavecompat.OptionalIntegrations.loaded("dynamicwaters"));
            boolean enabled=design.inherent.skyweavecompat.OptionalIntegrations.enabled(design.inherent.skyweavecompat.Feature.SABLE_CLIMATE);
            report.addProperty("enabled",enabled);
            report.addProperty("parentChunkAvailable",level.getChunkSource().getChunkNow(global.getX()>>4,global.getZ()>>4)!=null);
            report.addProperty("runtimeReferenceY",design.inherent.skyweavecompat.climate.ClimateRuntime.referenceY(level,global));
            var natural=((InteriorField)(Object)field).skyweave$contextAt(global.getX(),global.getZ(),generator.resolvedSkyIslandSettings());
            report.add("naturalClimateLayers",new Gson().toJsonTree(natural.climate().layers()));
            report.addProperty("naturalReferenceY",natural.climate().referenceY(global.getY(),design.inherent.skyweavecompat.climate.ClimateConfiguration.profile()));
            var foreign=server.getLevel(net.minecraft.world.level.Level.END);
            boolean unchanged=IslandClimate.temperaturePosition(foreign,local)==local;
            report.addProperty("foreignUnchanged",unchanged);
            var transforms=new JsonArray();report.add("transforms",transforms);
            BlockPos translated=null;
            for(var mode:List.of("initial","translated","tilted","unloaded_parent")) {
                if(mode.equals("translated"))ship.logicalPose().position().add(32,48,-32);
                if(mode.equals("tilted"))ship.logicalPose().orientation().rotateZ(Math.toRadians(25));
                if(mode.equals("unloaded_parent"))ship.logicalPose().position().add(100000,0,-100000);
                ship.forceUpdateGlobalBounds();
                var parent=BlockPos.containing(Sable.HELPER.projectOutOfSubLevel(level,local.getCenter()));
                if(!mode.equals("unloaded_parent"))level.getChunk(parent);
                boolean parentLoaded=level.getChunkSource().getChunkNow(parent.getX()>>4,parent.getZ()>>4)!=null;
                if(mode.equals("unloaded_parent") && parentLoaded)throw new AssertionError("Unloaded parent fixture already has a loaded chunk");
                if(mode.equals("translated"))translated=parent;
                if(mode.equals("tilted") && parent.equals(translated))throw new AssertionError("Tilt witness did not move the observed occupied block");
                int count=level.getChunkSource().getLoadedChunksCount();
                var target=IslandClimate.temperaturePosition(level,parent);var mapped=IslandClimate.temperaturePosition(level,local);
                var row=new JsonObject();transforms.add(row);row.addProperty("pose",mode);row.addProperty("parentChunkAvailable",parentLoaded);
                row.addProperty("parent",parent.toShortString());row.addProperty("expected",target.toShortString());row.addProperty("actual",mapped.toShortString());
                row.addProperty("matches",target.equals(mapped));row.addProperty("chunksBefore",count);
                var predicates=new JsonObject();row.add("nativeWarmEnoughToRain",predicates);
                for(var key:List.of(net.minecraft.world.level.biome.Biomes.PLAINS,net.minecraft.world.level.biome.Biomes.TAIGA)) {
                    var biome=level.registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.BIOME).getOrThrow(key);
                    boolean actualWarm=design.inherent.skyweavecompat.climate.SurfaceClimateScope.query(level,()->biome.warmEnoughToRain(local));
                    boolean expectedWarm=biome.warmEnoughToRain(target);
                    predicates.addProperty(key.location().toString(),actualWarm);
                    if(enabled && actualWarm!=expectedWarm)throw new AssertionError("Native temperature helper retained plot altitude");
                }
                row.addProperty("chunksAfter",level.getChunkSource().getLoadedChunksCount());
                if(target.equals(mapped)!=enabled || IslandClimate.temperaturePosition(level,mapped)!=mapped
                        || count!=level.getChunkSource().getLoadedChunksCount() || !localChunk.getBlockState(local).is(Blocks.STONE))
                    throw new AssertionError("Moved/tilted occupied plot climate contract failed: "+row);
            }

            boolean passed=expected.equals(actual)==enabled && unchanged && IslandClimate.temperaturePosition(level,actual)==actual
                    && before==report.get("chunksAfter").getAsInt();
            report.addProperty("complete",passed);
            report.addProperty("scope","Real occupied assembled block: parent temperature coordinates, idempotence, foreign-world preservation and no creating query; no force/AI/client qualification");
            Files.writeString(output,new GsonBuilder().setPrettyPrinting().create().toJson(report));
            if(!passed)throw new AssertionError("Sable temperature contract failed: "+report);
        } catch(Exception | AssertionError failure) {
            report.addProperty("complete",false);report.addProperty("error",failure.toString());
            Files.writeString(output,new GsonBuilder().setPrettyPrinting().create().toJson(report));
            throw failure;
        } finally {SubLevelContainer.getContainer(level).removeSubLevel(ship,SubLevelRemovalReason.REMOVED);}
        Files.writeString(output,new GsonBuilder().setPrettyPrinting().create().toJson(report));
    }
}
