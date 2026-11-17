package design.inherent.skyweavecompat.probe;

import com.faboslav.variantsandventures.common.entity.ai.goal.TargetAboveWaterGoal;
import com.faboslav.variantsandventures.common.entity.mob.MurkEntity;
import com.google.gson.JsonObject;
import design.inherent.skyweavecompat.Feature;
import design.inherent.skyweavecompat.OptionalIntegrations;
import design.inherent.skyweavecompat.encounters.MurkWater;
import design.inherent.skyweavecompat.terrain.LocalWater;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.LegacyRandomSource;

/** Actual V&V natural predicates/goals over physically authored water; not population frequency. */
public final class MurkEncounterProbe {
    public static final java.util.List<Double> goalTargets=new java.util.ArrayList<>();
    public static boolean measuringWater;
    public static int planningConstructors;
    private static void check(boolean value,String message){EncounterProbe.check(value,message);}
    private static final class Roll extends LegacyRandomSource {
        final int result;int calls;
        Roll(int result){super(42);this.result=result;}
        @Override public int nextInt(int bound){check(bound==40,"Murk rarity bound changed");calls++;return result;}
    }
    @SuppressWarnings("unchecked")
    static JsonObject run(ServerLevel level) throws Exception {
        boolean local=LocalWater.applies(level);
        boolean adapted=local&&OptionalIntegrations.enabled(Feature.MURK_WATER);
        var type=(EntityType<MurkEntity>)BuiltInRegistries.ENTITY_TYPE.get(EncounterProbe.id("variantsandventures:murk"));
        check(type!=null,"Murk type missing");
        for(int x=-1;x<=2;x++)for(int z=0;z<=2;z++)level.getChunk(x,z);
        // Two separate layers at one X/Z, plus a native sea-level control and a covered layer.
        int[] surfaces={62,128,368};
        for(int x=2;x<=14;x++)for(int z=18;z<=30;z++)for(int surface:surfaces) {
            level.setBlock(new BlockPos(x,surface-11,z),Blocks.STONE.defaultBlockState(),2);
            for(int y=surface-10;y<=surface;y++)level.setBlock(new BlockPos(x,y,z),Blocks.WATER.defaultBlockState(),2);
            level.setBlock(new BlockPos(x,surface+1,z),surface==368?Blocks.STONE.defaultBlockState():Blocks.AIR.defaultBlockState(),2);
        }
        // Prepare the exhaustion column before observing query work.
        for(int y=100;y<=360;y++)level.setBlock(new BlockPos(2,y,18),Blocks.WATER.defaultBlockState(),2);
        int loaded=level.getChunkSource().getLoadedChunksCount();var counts=new com.google.gson.JsonArray();
        planningConstructors=0;measuringWater=true;
        for(int surface:surfaces) {
            var deep=new BlockPos(8,surface-5,24);var boundary=deep.above();
            var positive=new Roll(0);boolean accepted=MurkEntity.canSpawn(type,level,MobSpawnType.NATURAL,deep,positive);
            boolean expected=adapted||deep.getY()<level.getSeaLevel()-5;
            check(accepted==expected,"Murk depth mismatch at surface "+surface+": "+accepted+" expected "+expected);
            check(positive.calls==(expected?1:0),"Murk consumed rarity roll before depth qualification");
            check(!MurkEntity.canSpawn(type,level,MobSpawnType.NATURAL,boundary,new Roll(0)),"Murk five-block boundary admitted");
            check(!MurkEntity.canSpawn(type,level,MobSpawnType.NATURAL,deep,new Roll(1)),"Murk one-in-forty filter lost");
            var trial=new Roll(0);check(MurkEntity.canSpawn(type,level,MobSpawnType.TRIAL_SPAWNER,boundary,trial)&&trial.calls==0,"Non-natural trial path changed");
            int acceptedCount=0;var random=RandomSource.create(42);
            for(int attempt=0;attempt<400;attempt++)if(MurkEntity.canSpawn(type,level,MobSpawnType.NATURAL,deep,random))acceptedCount++;
            check(expected?acceptedCount>0:acceptedCount==0,"Native random Murk control disagrees with local datum");counts.add(acceptedCount);
        }
        var missing=new BlockPos(1000000,120,1000000);
        check(MurkWater.datum(level,missing).isEmpty(),"Missing water lookup invented a datum");
        check(MurkWater.datum(level,new BlockPos(0,120,16)).isEmpty(),"Dry/void lookup invented a datum");
        var depth=MurkEntity.class.getDeclaredMethod("isValidSpawnDepth",net.minecraft.world.level.LevelAccessor.class,BlockPos.class);depth.setAccessible(true);
        check((boolean)depth.invoke(null,level,missing.atY(50))==!adapted,"Missing-chunk depth policy did not preserve selected scope");
        // More than256 connected water cells must refuse rather than invent a surface.
        check(MurkWater.datum(level,new BlockPos(2,100,18)).isEmpty(),"Water depth budget did not refuse");
        check(MurkWater.datum(level,new BlockPos(8,123,24)).orElseThrow()==129,"Lower layer lost to upper X/Z water");
        check(MurkWater.datum(level,new BlockPos(8,363,24)).orElseThrow()==369,"Covered water datum incorrect");
        var mob=type.create(level);check(mob!=null,"Murk constructor failed");
        var goal=new TargetAboveWaterGoal(mob,1,level.getSeaLevel());
        var wet=Entity.class.getDeclaredMethod("updateInWaterStateAndDoFluidPushing");wet.setAccessible(true);
        level.setDayTime(18000);
        for(int y:new int[]{57,123,363}) {
            mob.moveTo(8.5,y,24.5);wet.invoke(mob);check(mob.isInWater(),"Murk native water state false");
            boolean eligible=adapted||y==57;
            check(goal.canUse()==eligible,"Murk goal uses a stale/global layer at "+y);
            if(eligible) {
                goalTargets.clear();int upward=0,paths=0;
                for(int seed=0;seed<64;seed++) {
                    goal.start();mob.updateSwimming();mob.getNavigation().stop();mob.getRandom().setSeed(seed);goal.tick();
                    var path=mob.getNavigation().getPath();
                    if(path!=null&&path.getNodeCount()>1){paths++;if(path.getEndNode().y>y)upward++;}
                    goal.stop();
                }
                check(paths>0&&upward>0,"Murk native goal found no upward path at "+y+": "+paths+" paths, "+upward+" up");
                int target=y==57?level.getSeaLevel()-1:y==123?128:368;
                check(goalTargets.size()==64&&goalTargets.stream().allMatch(value->value==target),"Murk native goal target datum incorrect: "+goalTargets);
                goal.stop();mob.getNavigation().stop();
            }
        }
        mob.moveTo(8.5,367,24.5);wet.invoke(mob);check(!goal.canUse(),"Murk goal did not stop near upper local surface");
        level.setDayTime(1000);level.updateSkyBrightness();mob.moveTo(8.5,123,24.5);wet.invoke(mob);
        check(!goal.canUse(),"Murk native daytime goal gate changed");
        check(MurkEntity.canSpawn(type,level,MobSpawnType.NATURAL,new BlockPos(8,123,24),new Roll(0))==adapted,"Murk gained a darkness predicate");
        level.setDayTime(18000);level.updateSkyBrightness();
        mob.moveTo(1.5,123,16.5);wet.invoke(mob);check(!goal.canUse(),"Dry Murk movement admitted");
        mob.discard();
        measuringWater=false;
        check(level.getChunkSource().getLoadedChunksCount()==loaded,"Murk measured path loaded chunks");
        check(planningConstructors==0,"Murk queries constructed a native watershed");
        var warm=level.registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.BIOME).getHolderOrThrow(net.minecraft.world.level.biome.Biomes.WARM_OCEAN).value();
        var spawn=warm.getMobSettings().getMobs(MobCategory.MONSTER).unwrap().stream().filter(s->s.type==type).findFirst().orElseThrow();
        check(spawn.getWeight().asInt()==4&&spawn.minCount==1&&spawn.maxCount==1,"Murk native weight/group changed");
        var result=new JsonObject();result.addProperty("ownedHost",local);result.addProperty("adapted",adapted);
        result.add("acceptedOf400ByLayer",counts);result.addProperty("fiveBlockBoundary",true);result.addProperty("oneInFortyPreserved",true);
        result.addProperty("trialReasonUnchanged",true);result.addProperty("loadedChunksUnchanged",true);result.addProperty("goalRequeriesLayers",adapted);
        result.addProperty("dynamicWatersPresent",OptionalIntegrations.loaded("dynamicwaters"));result.addProperty("watershedConstructorsDuringQueries",planningConstructors);return result;
    }
}
