package design.inherent.skyweavecompat.probe;

import com.google.gson.*;
import design.inherent.skyweavecompat.terrain.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ambient.Bat;
import net.minecraft.world.entity.animal.Turtle;
import net.minecraft.world.entity.monster.Drowned;
import net.minecraft.world.level.block.Blocks;
import org.sathrek.sky_archipelago.worldgen.generator.core.SkyIslandChunkGenerator;
import java.nio.file.*;

/** Actual native predicates in controlled physical habitats; not natural spawn frequency or AI proof. */
final class HabitatProbe {
    private HabitatProbe() {}
    private static void check(boolean value,String message) { if (!value) throw new AssertionError(message); }
    static void run(MinecraftServer server, Path output) throws Exception {
        var level=server.overworld(); var generator=(SkyIslandChunkGenerator)level.getChunkSource().getGenerator();
        if(!design.inherent.skyweavecompat.OptionalIntegrations.enabled(design.inherent.skyweavecompat.Feature.WATER_SPAWNS)) {
            var report=new JsonObject();report.addProperty("waterSpawnsDisabled",true);
            report.add("nativeMovement",HabitatMovementProbe.run(level));report.addProperty("complete",true);
            report.addProperty("scope","Disabled water-movement rules and unchanged native breeding/nesting/hatching; other spawn predicates are qualified by the enabled profile");
            Files.writeString(output,new GsonBuilder().setPrettyPrinting().create().toJson(report));return;
        }
        var field=(InteriorField)(Object)((GeologicalGenerator)(Object)generator).skyweave$field(level.getChunkSource().randomState());
        var raw=(org.sathrek.sky_archipelago.worldgen.generator.field.IslandField)(Object)field;
        var anchors=raw.collectIslandPreviewsInRadius(0,0,8000,generator.resolvedSkyIslandSettings()).stream()
                .filter(p->p.family()==org.sathrek.sky_archipelago.worldgen.generator.field.model.IslandFamily.ANCHOR_PLATEAU)
                .sorted(java.util.Comparator.comparingDouble(p->Math.hypot(p.x(),p.z()))).toList();
        IslandContext.Body body=null; BlockPos cave=null;
        outer: for(var anchor:anchors) for(int dx=-1;dx<=1;dx++) for(int dz=-1;dz<=1;dz++) {
            int x=anchor.x()+dx*anchor.radius()/3, z=anchor.z()+dz*anchor.radius()/3;
            var context=field.skyweave$contextAt(x,z,generator.resolvedSkyIslandSettings());
            for(var candidate:context.bodies()) if(candidate.exterior().thickness()>120) {
                body=candidate; cave=new BlockPos(x,candidate.exterior().topY()-70,z); break outer;
            }
        }
        check(body!=null,"No bounded island body supports the authored habitat fixture");
        level.getChunk(cave);
        // The original rock is dark; a bounded authored room exercises predicates without
        // modifying their light/biome/random checks or inventing a replacement LevelReader.
        for(int x=-2;x<=2;x++) for(int z=-2;z<=2;z++) for(int y=-2;y<=12;y++)
            level.setBlock(cave.offset(x,y,z), (Math.abs(x)==2||Math.abs(z)==2||y==-2||y==12)
                    ? Blocks.STONE.defaultBlockState():Blocks.AIR.defaultBlockState(),2);
        for(int y=-1;y<=9;y++) level.setBlock(cave.above(y),Blocks.WATER.defaultBlockState(),2);
        int loaded=level.getChunkSource().getLoadedChunksCount();
        check(LocalWater.spawnSurface(level,cave,-128)==cave.getY()+9,"Loaded water layer was not selected");
        check(level.getChunkSource().getLoadedChunksCount()==loaded,"Habitat water lookup loaded a chunk");
        check(level.getRawBrightness(cave,0)==0,"Cave fixture is not physically dark");
        check(GlowSquid.checkGlowSquidSpawnRules(EntityType.GLOW_SQUID,level,MobSpawnType.NATURAL,cave,RandomSource.create(1)),"Deep cave water rejected glow squid");
        var shallow=cave.atY(body.exterior().topY()-16);
        level.setBlock(shallow,Blocks.WATER.defaultBlockState(),2);
        check(!GlowSquid.checkGlowSquidSpawnRules(EntityType.GLOW_SQUID,level,MobSpawnType.NATURAL,shallow,RandomSource.create(1)),"Shallow cover admitted glow squid");
        var bat=cave.offset(1,0,0); level.setBlock(bat.below(),Blocks.STONE.defaultBlockState(),2);
        check(IslandHabitat.roof(level,bat,-128)>bat.getY(),"Bat fixture has no natural cover owner");
        check(level.getMaxLocalRawBrightness(bat)==0,"Bat fixture is not physically dark");
        check(level.getBlockState(bat.below()).isValidSpawn(level,bat.below(),EntityType.BAT),"Bat fixture floor is invalid");
        int bats=0,drowned=0; var batRandom=RandomSource.create(42); var drownedRandom=RandomSource.create(42);
        for(int attempt=0;attempt<256;attempt++) {
            if(Bat.checkBatSpawnRules(EntityType.BAT,level,MobSpawnType.NATURAL,bat,batRandom)) bats++;
            if(Drowned.checkDrownedSpawnRules(EntityType.DROWNED,level,MobSpawnType.NATURAL,cave,drownedRandom)) drowned++;
        }
        check(bats>0,"Native bat habitat never accepted"); check(drowned>0,"Native deep-water drowned habitat never accepted");
        var shore=new BlockPos(8,508,8); level.getChunk(shore);
        level.setBlock(shore.below(),Blocks.SAND.defaultBlockState(),2); level.setBlock(shore,Blocks.AIR.defaultBlockState(),2);
        var water=shore.east(); level.setBlock(water,Blocks.WATER.defaultBlockState(),2); level.setBlock(water.above(),Blocks.AIR.defaultBlockState(),2);
        check(Turtle.checkTurtleSpawnRules(EntityType.TURTLE,level,MobSpawnType.NATURAL,shore,RandomSource.create(1)),"Dry high shore rejected turtle");
        level.setBlock(shore.below(),Blocks.STONE.defaultBlockState(),2);
        check(!Turtle.checkTurtleSpawnRules(EntityType.TURTLE,level,MobSpawnType.NATURAL,shore,RandomSource.create(1)),"Turtle lost native sand rule");
        BlockPos slime=null; IslandContext.Body slimeBody=null;
        findSlime: for(int dx=-8;dx<=8;dx++) for(int dz=-8;dz<=8;dz++) {
            int x=cave.getX()+dx*16,z=cave.getZ()+dz*16;
            if(net.minecraft.world.level.levelgen.WorldgenRandom.seedSlimeChunk(Math.floorDiv(x,16),Math.floorDiv(z,16),level.getSeed(),987234911L).nextInt(10)!=0) continue;
            var at=field.skyweave$contextAt(x,z,generator.resolvedSkyIslandSettings());
            for(var candidate:at.bodies()) if(candidate.exterior().thickness()>100
                    && candidate.exterior().contains(candidate.climateDatum())) {
                slimeBody=candidate; slime=new BlockPos(x,(candidate.exterior().bottomY()+candidate.exterior().topY())/2,z); break findSlime;
            }
        }
        check(slime!=null,"No bounded physical slime-chunk witness"); level.getChunk(slime);
        for(int y=0;y<3;y++) level.setBlock(slime.above(y),Blocks.AIR.defaultBlockState(),2);
        level.setBlock(slime.below(),Blocks.STONE.defaultBlockState(),2);
        check(level.getMaxLocalRawBrightness(slime)==0,"Underground slime fixture is not dark");
        int caveSlimes=0;var slimeRandom=RandomSource.create(42);
        for(int attempt=0;attempt<256;attempt++) if(net.minecraft.world.entity.monster.Slime.checkSlimeSpawnRules(
                EntityType.SLIME,level,MobSpawnType.NATURAL,slime,slimeRandom)) caveSlimes++;
        check(caveSlimes>0,"Native relative underground slime rule never accepted");
        var surfaceSlime=slime.atY(slimeBody.climateDatum());var swamp=level.registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.BIOME)
                .getHolderOrThrow(net.minecraft.world.level.biome.Biomes.SWAMP);
        level.getChunk(surfaceSlime).fillBiomesFromNoise((qx,qy,qz,sampler)->swamp,level.getChunkSource().randomState().sampler());
        level.setDayTime(18000);
        level.setBlock(surfaceSlime.below(),Blocks.GRASS_BLOCK.defaultBlockState(),2);
        for(int y=0;y<3;y++) level.setBlock(surfaceSlime.above(y),Blocks.AIR.defaultBlockState(),2);
        int swampSlimes=0;var swampRandom=RandomSource.create(42);
        for(int attempt=0;attempt<256;attempt++) if(net.minecraft.world.entity.monster.Slime.checkSlimeSpawnRules(
                EntityType.SLIME,level,MobSpawnType.NATURAL,surfaceSlime,swampRandom)) swampSlimes++;
        check(swampSlimes>0,"Native relative swamp slime rule never accepted");
        // Constructors resolve the actual nested movement goal hooks. This is load coverage,
        // not a claim that breeding, movement or nesting has been exercised.
        check(EntityType.TURTLE.create(level)!=null && EntityType.DROWNED.create(level)!=null,"Native movement classes failed initialization");
        var result=new JsonObject(); result.addProperty("seed",level.getSeed()); result.add("cave",new Gson().toJsonTree(cave));
        result.addProperty("undergroundSlimeAttempts",caveSlimes);result.addProperty("swampSlimeAttempts",swampSlimes);
        result.addProperty("batAcceptedAttempts",bats); result.addProperty("drownedAcceptedAttempts",drowned);
        result.addProperty("deepGlowSquid",true);result.addProperty("shallowGlowSquidRejected",true);
        result.addProperty("dryShoreTurtle",true);result.addProperty("sandRulePreserved",true);
        result.addProperty("waterQueriesDoNotLoadChunks",true);result.addProperty("movementHooksLoad",true);
        result.add("nativeMovement",HabitatMovementProbe.run(level));
        if(design.inherent.skyweavecompat.OptionalIntegrations.loaded("dynamicwaters"))
            result.add("dynamicFloeClimate",FloeClimateProbe.run(level));
        result.addProperty("complete",true);Files.writeString(output,new GsonBuilder().setPrettyPrinting().create().toJson(result));
    }
}
