package design.inherent.skyweavecompat.probe;

import com.google.gson.*;
import java.lang.reflect.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.entity.animal.Turtle;
import net.minecraft.world.entity.monster.Drowned;
import net.minecraft.world.level.block.*;
import net.minecraft.world.phys.AABB;

/** Controlled native goals and hatching at an elevated physical shore, not spawn frequency. */
final class HabitatMovementProbe {
    private HabitatMovementProbe() {}
    private static void check(boolean value,String message) {if(!value)throw new AssertionError(message);}
    private static Goal goal(String name,Class<?>[] types,Object... arguments) throws Exception {
        var constructor=Class.forName(name).getDeclaredConstructor(types);constructor.setAccessible(true);
        return (Goal)constructor.newInstance(arguments);
    }
    private static void wet(Entity entity) throws Exception {
        var method=Entity.class.getDeclaredMethod("updateInWaterStateAndDoFluidPushing");method.setAccessible(true);
        method.invoke(entity);check(entity.isInWater(),"Movement fixture is not physically in water");
    }
    static JsonObject run(ServerLevel level) throws Exception {
        // Explicit fixture preparation is separate from the measured goal calls.
        for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++)level.getChunk(x,z);
        for(int x=2;x<=14;x++)for(int z=2;z<=14;z++) {
            level.setBlock(new BlockPos(x,499,z),Blocks.STONE.defaultBlockState(),2);
            for(int y=500;y<=508;y++)level.setBlock(new BlockPos(x,y,z),Blocks.WATER.defaultBlockState(),2);
            for(int y=509;y<=512;y++)level.setBlock(new BlockPos(x,y,z),Blocks.AIR.defaultBlockState(),2);
        }
        var turtle=EntityType.TURTLE.create(level);var drowned=EntityType.DROWNED.create(level);
        check(turtle!=null && drowned!=null,"Native movement entities unavailable");
        var result=new JsonObject();int loaded=level.getChunkSource().getLoadedChunksCount();
        boolean adapted=design.inherent.skyweavecompat.OptionalIntegrations.enabled(design.inherent.skyweavecompat.Feature.WATER_SPAWNS);
        result.addProperty("waterSpawnAdaptation",adapted);
        try {
            turtle.moveTo(8.5,504,8.5);wet(turtle);
            var travel=goal("net.minecraft.world.entity.animal.Turtle$TurtleTravelGoal",new Class<?>[]{Turtle.class,double.class},turtle,1d);
            var destination=Turtle.class.getDeclaredMethod("getTravelPos");destination.setAccessible(true);
            check(travel.canUse(),"Native wet turtle travel was not eligible");
            int raised=0;
            for(int seed=0;seed<64;seed++) {
                var random=RandomSource.create(seed);int dx=random.nextInt(1025)-512,dy=random.nextInt(9)-4,dz=random.nextInt(1025)-512;
                if(504+dy>(adapted?508:level.getSeaLevel())-1)dy=0;
                turtle.getRandom().setSeed(seed);travel.start();var actual=(BlockPos)destination.invoke(turtle);
                check(actual.equals(new BlockPos(8+dx,504+dy,8+dz)),"Native turtle travel retained global sea height: "+actual);
                if(actual.getY()>504)raised++;travel.stop();
            }
            check(adapted?raised>0:raised==0,"Turtle travel did not match selected water altitude");
            result.addProperty("turtleTravelSeeds",64);result.addProperty("turtleRaisedDestinations",raised);

            level.setDayTime(18000);level.updateSkyBrightness();
            check(!level.isDay(),"Native drowned movement fixture is not night");
            drowned.moveTo(8.5,503,8.5);wet(drowned);
            var swim=goal("net.minecraft.world.entity.monster.Drowned$DrownedSwimUpGoal",
                    new Class<?>[]{Drowned.class,double.class,int.class},drowned,1d,level.getSeaLevel());
            check(swim.canUse()==adapted,"Native drowned ascent failed at "+drowned.blockPosition()+", local surface="
                    +design.inherent.skyweavecompat.terrain.LocalWater.movementSurface(level,drowned.blockPosition(),level.getSeaLevel()));
            if(adapted) {
                swim.start();drowned.updateSwimming();
                for(int i=0;i<32 && drowned.getNavigation().getPath()==null;i++){drowned.getRandom().setSeed(i);swim.tick();}
                var path=drowned.getNavigation().getPath();
                check(path!=null && path.getNodeCount()>1,"Native drowned ascent produced no water path");
                result.addProperty("drownedAscentNodes",path.getNodeCount());
                int endY=path.getEndNode().y;result.addProperty("drownedAscentEndY",endY);
                check(endY>503,"Native drowned ascent path did not rise toward local water");
                swim.stop();drowned.getNavigation().stop();
                drowned.moveTo(8.5,507,8.5);wet(drowned);
                check(!swim.canUse(),"Drowned ascent did not terminate at local water surface");
            } else result.addProperty("nativeDrownedGlobalAscentPreserved",true);

            // A real shore for the native home, breeding-completion and egg-laying goals.
            for(int x=2;x<=7;x++)for(int z=2;z<=14;z++) {
                for(int y=500;y<=507;y++)level.setBlock(new BlockPos(x,y,z),Blocks.SAND.defaultBlockState(),2);
                level.setBlock(new BlockPos(x,508,z),Blocks.AIR.defaultBlockState(),2);
            }
            var home=new BlockPos(6,508,8);turtle.moveTo(6.5,508,8.5);turtle.setHomePos(home);turtle.setOnGround(true);
            var mate=EntityType.TURTLE.create(level);check(mate!=null,"Native turtle partner unavailable");
            try {
                mate.moveTo(6.5,508,9.5);turtle.setInLove(null);mate.setInLove(null);
                var breed=goal("net.minecraft.world.entity.animal.Turtle$TurtleBreedGoal",new Class<?>[]{Turtle.class,double.class},turtle,1d);
                var partner=BreedGoal.class.getDeclaredField("partner");partner.setAccessible(true);partner.set(breed,mate);
                var finish=breed.getClass().getDeclaredMethod("breed");finish.setAccessible(true);finish.invoke(breed);
                check(turtle.hasEgg() && turtle.getAge()==6000 && mate.getAge()==6000,"Native breeding completion failed");
                result.addProperty("nativeBreedingCompletion",true);
                var homeGoal=goal("net.minecraft.world.entity.animal.Turtle$TurtleGoHomeGoal",new Class<?>[]{Turtle.class,double.class},turtle,1d);
                turtle.moveTo(8.5,508,8.5);check(homeGoal.canUse(),"Egg-carrying turtle did not select home");
                homeGoal.start();check(!homeGoal.canContinueToUse(),"Home goal lost native seven-block arrival distance");homeGoal.stop();
                result.addProperty("homeArrivalPreserved",true);
                turtle.moveTo(6.5,508,8.5);
                var lay=goal("net.minecraft.world.entity.animal.Turtle$TurtleLayEggGoal",new Class<?>[]{Turtle.class,double.class},turtle,1d);
                check(lay.canUse(),"Native elevated sand nesting target unavailable");lay.start();
                var target=MoveToBlockGoal.class.getDeclaredField("blockPos");target.setAccessible(true);
                var sand=(BlockPos)target.get(lay);turtle.moveTo(sand.getX()+.5,sand.getY()+1,sand.getZ()+.5);
                // Clear the earlier water-contact cache before the real goal's dry-land check.
                var fluid=Entity.class.getDeclaredMethod("updateInWaterStateAndDoFluidPushing");fluid.setAccessible(true);fluid.invoke(turtle);
                for(int i=0;i<240 && turtle.hasEgg();i++)lay.tick();lay.stop();
                var eggs=sand.above();var eggState=level.getBlockState(eggs);
                check(eggState.is(Blocks.TURTLE_EGG) && !turtle.hasEgg(),"Native nesting did not place eggs at physical island height");
                int expected=eggState.getValue(TurtleEggBlock.EGGS);result.addProperty("laidEggs",expected);result.addProperty("eggY",eggs.getY());
                // Native random ticks retain the time/random and substrate rules.
                var negative=eggs.offset(0,0,3);level.setBlock(negative.below(),Blocks.STONE.defaultBlockState(),2);
                level.setBlock(negative,Blocks.TURTLE_EGG.defaultBlockState().setValue(TurtleEggBlock.HATCH,2),2);
                var random=RandomSource.create(42);
                for(int i=0;i<2048;i++)level.getBlockState(negative).randomTick(level,negative,random);
                check(level.getBlockState(negative).is(Blocks.TURTLE_EGG),"Native hatching lost its sand requirement");
                int ticks=0;for(;ticks<8192 && level.getBlockState(eggs).is(Blocks.TURTLE_EGG);ticks++)
                    level.getBlockState(eggs).randomTick(level,eggs,random);
                var babies=level.getEntitiesOfClass(Turtle.class,new AABB(eggs).inflate(2),Turtle::isBaby);
                check(babies.size()==expected,"Native hatchlings did not match laid eggs");
                var getHome=Turtle.class.getDeclaredMethod("getHomePos");getHome.setAccessible(true);
                for(var baby:babies)check(getHome.invoke(baby).equals(eggs),"Native hatchling lost its physical home");
                result.addProperty("hatchedBabies",babies.size());result.addProperty("hatchRandomTicks",ticks);
                result.addProperty("stoneEggDidNotHatch",true);for(var baby:babies)baby.discard();
            } finally {mate.discard();}
            result.addProperty("chunksBefore",loaded);result.addProperty("chunksAfter",level.getChunkSource().getLoadedChunksCount());
            check(loaded==level.getChunkSource().getLoadedChunksCount(),"Bounded movement goals created chunks");
            result.addProperty("scope","Actual native travel/ascent paths, controlled breeding completion, home arrival, nesting and hatching; not natural population frequency or autonomous long-range travel");
            return result;
        } finally {turtle.discard();drowned.discard();}
    }
}
