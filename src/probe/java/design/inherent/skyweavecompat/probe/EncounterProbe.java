package design.inherent.skyweavecompat.probe;

import com.google.gson.*;
import com.mojang.authlib.GameProfile;
import design.inherent.skyweavecompat.Feature;
import design.inherent.skyweavecompat.OptionalIntegrations;
import design.inherent.skyweavecompat.encounters.*;
import java.nio.file.*;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.*;
import net.minecraft.nbt.*;
import net.minecraft.resources.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.vehicle.MinecartSpawner;
import net.minecraft.world.level.*;
import net.minecraft.world.level.biome.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.levelgen.*;
import net.minecraft.world.level.levelgen.structure.*;
import net.minecraft.world.level.levelgen.structure.templatesystem.LiquidSettings;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.MobSpawnEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Disposable, native encounter controls. No code from this fixture ships in the product. */
public final class EncounterProbe {
    public static boolean observing;
    public static final List<CompoundTag> loads=new ArrayList<>();
    public static final List<AABB> boxes=new ArrayList<>();
    public static final List<String> rules=new ArrayList<>();
    private static boolean cancelPosition,cancelJoin;
    private static int positionChecks,joins,finalizations;
    private MinecraftServer pending;
    public EncounterProbe() {
        NeoForge.EVENT_BUS.addListener((ServerStartedEvent e)->pending=e.getServer());
        NeoForge.EVENT_BUS.addListener((MobSpawnEvent.PositionCheck e)->{
            if(observing){positionChecks++;if(cancelPosition)e.setResult(MobSpawnEvent.PositionCheck.Result.FAIL);}
        });
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent e)->{if(observing)finalizations++;});
        NeoForge.EVENT_BUS.addListener((EntityJoinLevelEvent e)->{if(observing){joins++;if(cancelJoin)e.setCanceled(true);}});
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post e)->{
            if(pending==null)return;var server=pending;pending=null;
            var report=new JsonObject();
            try {run(server,report);report.addProperty("complete",true);}
            catch(Throwable failure){report.addProperty("complete",false);report.addProperty("error",failure.toString());failure.printStackTrace();}
            try{Files.writeString(Path.of(System.getProperty("neighbors.probe.output")),new GsonBuilder().setPrettyPrinting().create().toJson(report));}
            catch(Exception failure){throw new RuntimeException(failure);}
            server.halt(false);
        });
    }
    static void check(boolean condition,String message){if(!condition)throw new AssertionError(message);}
    static ResourceLocation id(String id){return ResourceLocation.parse(id);}
    private static String type(Entity entity){return BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString();}
    private static void run(MinecraftServer server,JsonObject result) throws Exception {
        var level=server.overworld();server.setDifficulty(Difficulty.NORMAL,true);
        level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false,server);
        level.getGameRules().getRule(GameRules.RULE_RANDOMTICKING).set(0,server);
        level.setDayTime(18000);level.updateSkyBrightness();
        result.add("patrol",patrol(level));
        // These are fixture preparation requests, outside the no-load measurements below.
        for(int x=0;x<=2;x++)for(int z=0;z<=1;z++)level.getChunk(x,z);
        var reopened=Path.of("encounter-saved.json");
        if(Files.exists(reopened)) {
            var expected=JsonParser.parseString(Files.readString(reopened)).getAsJsonObject();
            for(var item:expected.entrySet()) {
                var pos=BlockPos.of(Long.parseLong(item.getKey()));
                var block=level.getBlockEntity(pos);check(block!=null,"Saved encounter block missing: "+pos);
                var tag=block.saveWithFullMetadata(level.registryAccess());
                if(block instanceof SpawnerBlockEntity) {
                    check(tag.getCompound("SpawnData").toString().equals(item.getValue().getAsJsonObject().get("data").getAsString()),"Saved SpawnData changed");
                    check(tag.getList("SpawnPotentials",10).toString().equals(item.getValue().getAsJsonObject().get("potentials").getAsString()),"Saved SpawnPotentials changed");
                } else check(tag.getCompound("normal_config").toString().equals(item.getValue().getAsString()),"Saved trial configuration changed");
            }
            result.addProperty("savedBlocksReopened",expected.size());
        }
        var saved=new JsonObject();
        result.add("spawners",spawners(server,level,saved));
        result.add("dungeonModes",dungeons(level));
        result.add("trials",trials(level,saved));
        result.add("murk",MurkEncounterProbe.run(level));
        Files.writeString(reopened,saved.toString());
    }
    private static JsonObject patrol(ServerLevel level) throws Exception {
        boolean active=OptionalIntegrations.enabled(Feature.PATROL_CHANCE);
        var original=level.random;var field=Level.class.getDeclaredField("random");field.setAccessible(true);
        var players=level.players();var fixtures=new ArrayList<FakePlayer>();
        for(int i=0;i<5;i++) {
            var p=new FakePlayer(level,new GameProfile(UUID.nameUUIDFromBytes(("patrol"+i).getBytes()),"Patrol"+i));
            p.gameMode.changeGameModeForPlayer(GameType.SPECTATOR);fixtures.add(p);players.add(p);
        }
        var out=new JsonObject();
        try {
            level.setDayTime(24000L*6+1000);level.updateSkyBrightness();
            level.getGameRules().getRule(GameRules.RULE_DO_PATROL_SPAWNING).set(true,level.getServer());
            for(double percent:new double[]{0,20,100,15}) {
                profile(percent,EncounterProfile.Mode.NATIVE_TYPE,5);
                var random=new PatrolRandom();field.set(level,random);
                new PatrolSpawner().tick(level,true,false);
                var wanted=!active||percent==20?List.of(1200,5,5):percent==0?List.of(1200):List.of(1200,5);
                check(random.ints.equals(wanted),"Patrol RNG callsite/player isolation at"+percent+": "+random.ints);
                check(random.doubles==(active&&percent==15?1:0),"Patrol replacement roll count");
                out.add("percent"+(int)percent,new Gson().toJsonTree(random.ints));
            }
            var random=new PatrolRandom();field.set(level,random);new PatrolSpawner().tick(level,false,false);
            check(random.ints.isEmpty(),"Patrol changed native hostile-spawn gate");
            level.setDayTime(1000);random=new PatrolRandom();field.set(level,random);new PatrolSpawner().tick(level,true,false);
            check(random.ints.equals(List.of(1200)),"Patrol changed native day gate");
            out.addProperty("fivePlayersUnchanged",true);out.addProperty("featureActive",active);
        } finally {field.set(level,original);players.removeAll(fixtures);level.setDayTime(18000);level.updateSkyBrightness();}
        return out;
    }
    private static final class PatrolRandom extends LegacyRandomSource {
        final List<Integer> ints=new ArrayList<>();int doubles;
        PatrolRandom(){super(42);}
        @Override public int nextInt(int bound){ints.add(bound);return bound==1200?300:ints.size()==2?0:4;}
        @Override public double nextDouble(){doubles++;return 0;}
    }
    private static void profile(double patrol,EncounterProfile.Mode mode,double chance) throws Exception {
        var path=Path.of("encounter-profile");Files.createDirectories(path);
        var value=new EncounterProfile(patrol,new EncounterProfile.Spawners(mode,chance,EncounterProfile.DEFAULT.spawners().rules()));
        Files.writeString(path.resolve(EncounterConfiguration.FILE),new Gson().toJson(value));EncounterConfiguration.load(path);
    }
    private static CompoundTag entity(String name,BlockPos target) {
        var value=new CompoundTag();value.putString("id",name);var pos=new ListTag();
        pos.add(DoubleTag.valueOf(target.getX()+.5));pos.add(DoubleTag.valueOf(target.getY()));pos.add(DoubleTag.valueOf(target.getZ()+.5));value.put("Pos",pos);
        value.putString("CustomName","\"Encounter witness\"");value.putBoolean("PersistenceRequired",true);return value;
    }
    private static CompoundTag settings(CompoundTag entity,int count,int cap) {
        var value=new CompoundTag();var data=new CompoundTag();data.put("entity",entity);
        var light=new CompoundTag();light.putInt("min_inclusive",0);light.putInt("max_inclusive",15);
        var rules=new CompoundTag();rules.put("block_light_limit",light.copy());rules.put("sky_light_limit",light.copy());data.put("custom_spawn_rules",rules);
        value.put("SpawnData",data);var list=new ListTag();var entry=new CompoundTag();entry.putInt("weight",1);entry.put("data",data.copy());list.add(entry);value.put("SpawnPotentials",list);
        value.putShort("Delay",(short)0);value.putShort("MinSpawnDelay",(short)200);value.putShort("MaxSpawnDelay",(short)800);
        value.putShort("SpawnCount",(short)count);value.putShort("MaxNearbyEntities",(short)cap);value.putShort("RequiredPlayerRange",(short)16);value.putShort("SpawnRange",(short)4);return value;
    }
    private static void clear(ServerLevel level) {
        for(var entity:level.getEntities((Entity)null,new AABB(0,80,0,48,150,32),e->!(e instanceof FakePlayer)))entity.discard();
    }
    private static void observe(BaseSpawner spawner,ServerLevel level,BlockPos owner) {
        loads.clear();boxes.clear();rules.clear();positionChecks=0;joins=0;finalizations=0;
        observing=true;try{spawner.serverTick(level,owner);}finally{observing=false;cancelPosition=false;cancelJoin=false;}
    }
    private static JsonObject spawners(MinecraftServer server,ServerLevel level,JsonObject saved) throws Exception {
        boolean active=OptionalIntegrations.enabled(Feature.VARIANT_SPAWNERS);
        var owner=new BlockPos(8,100,8);var target=new BlockPos(24,100,8);
        for(int x=4;x<30;x++)for(int z=4;z<13;z++)for(int y=98;y<=104;y++)level.setBlock(new BlockPos(x,y,z),y==99?Blocks.STONE.defaultBlockState():Blocks.AIR.defaultBlockState(),2);
        level.setBlock(owner,Blocks.SPAWNER.defaultBlockState(),2);var block=(SpawnerBlockEntity)level.getBlockEntity(owner);var spawner=block.getSpawner();
        var fake=new FakePlayer(level,new GameProfile(UUID.nameUUIDFromBytes("encounter-spawner".getBytes()),"SpawnerWitness"));fake.moveTo(8,100,8);level.players().add(fake);
        var cases=new JsonArray();
        try {
            profile(20,EncounterProfile.Mode.PER_EMISSION,100);
            var biomes=List.of(Biomes.DESERT,Biomes.SNOWY_PLAINS,Biomes.JUNGLE,Biomes.SNOWY_PLAINS,Biomes.SWAMP,Biomes.JUNGLE);
            int index=0;
            for(var variant:SpawnerSelection.Variant.values()) {
                clear(level);var biome=level.registryAccess().registryOrThrow(Registries.BIOME).getHolderOrThrow(biomes.get(index++));
                var desert=level.registryAccess().registryOrThrow(Registries.BIOME).getHolderOrThrow(Biomes.DESERT);
                for(int x=0;x<=1;x++)level.getChunk(x,0).fillBiomesFromNoise((qx,qy,qz,sampler)->qx<4?desert:biome,level.getChunkSource().randomState().sampler());
                var input=entity(variant.source,target);var hand=new ListTag();var sword=new CompoundTag();
                sword.putString("id","minecraft:iron_sword");sword.putInt("count",1);hand.add(sword);hand.add(new CompoundTag());input.put("HandItems",hand);
                var tag=settings(input,1,6);spawner.load(level,owner,tag);
                var before=spawner.save(new CompoundTag());observe(spawner,level,owner);var after=spawner.save(new CompoundTag());
                check(loads.size()==1,"Expected one native entity construction for "+variant+": "+loads);
                String wanted=active?variant.target:variant.source;
                check(loads.getFirst().getString("id").equals(wanted),"Native selected NBT did not follow actual-position biome: "+variant+" "+loads);
                var emitted=level.getEntities((Entity)null,new AABB(20,99,4,28,104,12),e->true);
                check(emitted.size()==1&&type(emitted.getFirst()).equals(wanted),"Native emission not realized exactly once: "+variant);
                check(emitted.getFirst().getCustomName().getString().equals("Encounter witness"),"Custom NBT lost");
                check(((Mob)emitted.getFirst()).getMainHandItem().is(net.minecraft.world.item.Items.IRON_SWORD),"Authored equipment lost");
                check(before.getCompound("SpawnData").equals(after.getCompound("SpawnData"))&&before.get("SpawnPotentials").equals(after.get("SpawnPotentials")),"Persistent spawner identity mutated");
                cases.add(wanted);
            }
            clear(level);var jungle=level.registryAccess().registryOrThrow(Registries.BIOME).getHolderOrThrow(Biomes.JUNGLE);
            for(int x=0;x<=1;x++)level.getChunk(x,0).fillBiomesFromNoise((qx,qy,qz,sampler)->jungle,level.getChunkSource().randomState().sampler());
            // A scripted rare roll followed by a miss proves transient state resets within one native loop.
            profile(20,EncounterProfile.Mode.PER_EMISSION,5);var original=level.random;var field=Level.class.getDeclaredField("random");field.setAccessible(true);
            var random=new LegacyRandomSource(42){int rolls;@Override public double nextDouble(){return rolls++==0?.01:.99;}};
            spawner.load(level,owner,settings(entity("minecraft:zombie",target),2,6));field.set(level,random);
            try{observe(spawner,level,owner);}finally{field.set(level,original);}
            check(loads.size()==2,"Native repeated attempt construction count");
            check(loads.get(0).getString("id").equals(active?"variantsandventures:thicket":"minecraft:zombie")&&loads.get(1).getString("id").equals("minecraft:zombie"),"Choice leaked between native attempts");
            clear(level);profile(20,EncounterProfile.Mode.PER_EMISSION,100);
            // Aggregate cap uses the native owner's box, not the chosen mob's exact class.
            var nearby=EntityType.ZOMBIE.create(level);nearby.moveTo(9.5,100,9.5);level.addFreshEntity(nearby);
            spawner.load(level,owner,settings(entity("minecraft:zombie",owner.east(3)),1,1));observe(spawner,level,owner);
            check(joins==0,"Mixed-family nearby cap did not preserve selected mode");
            clear(level);
            for(String failure:List.of("position","join","duplicate","collision","range")) {
                var input=entity("minecraft:zombie",target);var state=settings(input,1,6);
                if(failure.equals("duplicate")){var existing=EntityType.ZOMBIE.create(level);existing.moveTo(30,100,12);level.addFreshEntity(existing);input.putUUID("UUID",existing.getUUID());}
                if(failure.equals("collision"))level.setBlock(target,Blocks.STONE.defaultBlockState(),2);
                if(failure.equals("range"))fake.moveTo(200,100,200);
                spawner.load(level,owner,state);cancelPosition=failure.equals("position");cancelJoin=failure.equals("join");observe(spawner,level,owner);
                check(loads.size()<2,"Failed emission rerolled/constructed twice: "+failure);
                check(level.getEntities((Entity)null,new AABB(20,99,4,28,104,12),e->true).isEmpty(),"Failed emission inserted a fallback: "+failure);
                level.setBlock(target,Blocks.AIR.defaultBlockState(),2);fake.moveTo(8,100,8);clear(level);
            }
            // Passengers and authored variants are left native, including their saved identity.
            for(String source:List.of("passengers","variantsandventures:thicket","minecraft:zombie_villager")) {
                var input=entity(source.equals("passengers")?"minecraft:zombie":source,target);
                if(source.equals("passengers")){var passengers=new ListTag();var pig=new CompoundTag();pig.putString("id","minecraft:pig");passengers.add(pig);input.put("Passengers",passengers);}
                spawner.load(level,owner,settings(input,1,6));observe(spawner,level,owner);
                check(loads.size()==1&&loads.getFirst().getString("id").equals(input.getString("id")),"Excluded authored/passenger identity changed");clear(level);
            }
            var cart=new MinecartSpawner(level,8,100,8);cart.getSpawner().load(level,owner,settings(entity("minecraft:zombie",target),1,6));observe(cart.getSpawner(),level,owner);
            check(loads.size()==1&&loads.getFirst().getString("id").equals("minecraft:zombie"),"Minecart spawner was adapted");cart.discard();clear(level);
            profile(20,EncounterProfile.Mode.NATIVE_TYPE,100);spawner.load(level,owner,settings(entity("minecraft:zombie",target),1,6));observe(spawner,level,owner);
            check(loads.size()==1&&loads.getFirst().getString("id").equals("minecraft:zombie"),"NATIVE_TYPE changed emission identity");clear(level);
            profile(20,EncounterProfile.Mode.PER_EMISSION,100);
            var plain=new CompoundTag();plain.putString("id","minecraft:zombie");var nativeData=settings(plain,8,6);
            nativeData.getCompound("SpawnData").remove("custom_spawn_rules");spawner.load(level,owner,nativeData);observe(spawner,level,owner);
            var initialized=level.getEntities((Entity)null,new AABB(0,98,0,18,105,18),e->e instanceof Mob);
            check(!initialized.isEmpty()&&initialized.stream().allMatch(e->((Mob)e).getSpawnType()==MobSpawnType.SPAWNER),"Native fresh-mob initialization was lost");
            String initializedType=active?"variantsandventures:thicket":"minecraft:zombie";
            check(!rules.isEmpty()&&rules.stream().allMatch(initializedType::equals),"Native spawn rules did not receive the selected type: "+rules);
            clear(level);
            if(active) {
                profile(20,EncounterProfile.Mode.PER_EMISSION,100);
                var desert=level.registryAccess().registryOrThrow(Registries.BIOME).getHolderOrThrow(Biomes.DESERT);
                for(int x=0;x<=2;x++)for(int z=0;z<=1;z++)level.getChunk(x,z).fillBiomesFromNoise(
                        (qx,qy,qz,sampler)->((qx+qy+qz)&1)==0?desert:jungle,level.getChunkSource().randomState().sampler());
                int mismatches=0;
                for(int x=17;x<=28;x++)for(int y=100;y<108;y++) {
                    var at=new BlockPos(x,y,8);var biome=level.getBiome(at);
                    var selected=VariantSpawners.choose(level,at,entity("minecraft:zombie",at));
                    String wanted=biome.is(Biomes.DESERT)?"minecraft:husk":"variantsandventures:thicket";
                    if(selected==null||!BuiltInRegistries.ENTITY_TYPE.getKey(selected.type()).toString().equals(wanted))mismatches++;
                }
                check(mismatches==0,"Actual-position native fuzzy biome mismatch count: "+mismatches+"/96");
            }
            var tag=spawner.save(new CompoundTag());var snapshot=new JsonObject();snapshot.addProperty("data",tag.getCompound("SpawnData").toString());snapshot.addProperty("potentials",tag.getList("SpawnPotentials",10).toString());saved.add(Long.toString(owner.asLong()),snapshot);block.setChanged();
        } finally {level.players().remove(fake);clear(level);}
        var result=new JsonObject();result.add("sixRealizedTypes",cases);result.addProperty("featureActive",active);result.addProperty("mixedNearbyCap",true);result.addProperty("nativeLoopReset",true);result.addProperty("failureControls",5);result.addProperty("excludedIdentityControls",4);result.addProperty("persistentIdentityUnchanged",true);result.addProperty("nativeInitializationAndRules",true);result.addProperty("nativeFuzzyBiomeSamples",active?96:0);return result;
    }
    @SuppressWarnings("unchecked")
    private static JsonObject trials(ServerLevel level,JsonObject saved) throws Exception {
        boolean repaired=Boolean.getBoolean("neighbors.probe.encounterRepair");var result=new JsonObject();var mobs=new JsonArray();
        var manager=level.getStructureManager();int index=0;
        if(OptionalIntegrations.enabled(Feature.VARIANT_TRIAL_ALIASES))result.add("lithostitchedBridge",TrialAliasProbe.run(level.getServer()));
        var structure=level.registryAccess().registryOrThrow(Registries.STRUCTURE).get(id("minecraft:trial_chambers"));
        List<net.minecraft.world.level.levelgen.structure.pools.alias.PoolAliasBinding> aliases;
        if(OptionalIntegrations.loaded("lithostitched")&&Class.forName("dev.worldgen.lithostitched.worldgen.structure.AlternateJigsawStructure").isInstance(structure)) {
            var config=structure.getClass().getMethod("config").invoke(structure);
            aliases=(List<net.minecraft.world.level.levelgen.structure.pools.alias.PoolAliasBinding>)config.getClass().getMethod("poolAliases").invoke(config);
        } else {
            var field=net.minecraft.world.level.levelgen.structure.structures.JigsawStructure.class.getDeclaredField("poolAliases");field.setAccessible(true);
            aliases=(List<net.minecraft.world.level.levelgen.structure.pools.alias.PoolAliasBinding>)field.get(structure);
        }
        result.addProperty("structureConsumer",structure.getClass().getName());
        var targets=aliases.stream().flatMap(EncounterProbe::aliasTargets).distinct().toList();check(targets.size()<=128,"Trial alias closure exceeds fixture budget");
        var aliasDiagnostics=new JsonArray();
        for(int i=0;i<aliases.size();i++) {
            var binding=aliases.get(i);var diagnostic=new JsonObject();diagnostic.addProperty("class",binding.getClass().getName());
            diagnostic.addProperty("allTargets",binding.allTargets().count());var resolved=new JsonArray();
            binding.forEachResolved(RandomSource.create(42),(from,to)->resolved.add(from.location()+"->"+to.location()));
            diagnostic.add("resolved",resolved);aliasDiagnostics.add(diagnostic);
        }
        var missing=new java.util.TreeSet<String>();var references=new java.util.TreeSet<String>();
        var ops=RegistryOps.create(com.mojang.serialization.JsonOps.INSTANCE,level.registryAccess());
        for(var key:targets) {
            var pool=level.registryAccess().registryOrThrow(Registries.TEMPLATE_POOL).getOrThrow(key);
            for(var element:pool.getShuffledTemplates(RandomSource.create(42))) {
                var json=net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement.CODEC.encodeStart(ops,element).getOrThrow();
                locations(json,references);
            }
        }
        check(references.size()<=256&&!references.isEmpty(),"Trial template closure budget/empty result; consumer="+structure.getClass().getName()+", aliases="+aliases.size()+", targetPools="+targets.size()+", diagnostic="+aliasDiagnostics);
        for(var reference:references)if(manager.get(id(reference)).isEmpty())missing.add(reference);
        check(missing.equals(repaired?Set.of():Set.of("variantsandventures:trial_chambers/spawner/slow_ranged/murk","variantsandventures:trial_chambers/spawner/slow_ranged/verdant")),"Trial alias template closure has unexpected gaps: "+missing);
        result.add("aliasBindings",net.minecraft.world.level.levelgen.structure.pools.alias.PoolAliasBinding.CODEC.listOf().encodeStart(ops,aliases).getOrThrow());
        result.addProperty("aliasTargetPools",targets.size());result.addProperty("referencedTemplates",references.size());result.add("missingTemplates",new Gson().toJsonTree(missing));
        for(String mob:List.of("murk","verdant")) {
            var pool=level.registryAccess().registryOrThrow(Registries.TEMPLATE_POOL).get(id("minecraft:trial_chambers/spawner/slow_ranged/"+mob));
            check(pool!=null,"V&V slow-ranged pool absent");var element=pool.getRandomTemplate(RandomSource.create(42));var at=new BlockPos(34,120,4+index++*8);
            if(!repaired) {
                check(manager.get(id("variantsandventures:trial_chambers/spawner/slow_ranged/"+mob)).isEmpty(),"Baseline missing-template control is not missing");
                check(!element.place(manager,level,level.structureManager(),level.getChunkSource().getGenerator(),at,at,Rotation.NONE,BoundingBox.infinite(),RandomSource.create(42),LiquidSettings.APPLY_WATERLOGGING,false),"Missing template unexpectedly placed");
                continue;
            }
            var source=manager.get(id("variantsandventures:trial_chambers/spawner/ranged/"+mob)).orElseThrow();
            var raw=source.filterBlocks(BlockPos.ZERO,new net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings(),Blocks.TRIAL_SPAWNER).getFirst().nbt();
            var before=raw.copy();
            check(element.place(manager,level,level.structureManager(),level.getChunkSource().getGenerator(),at,at,Rotation.NONE,BoundingBox.infinite(),RandomSource.create(42),LiquidSettings.APPLY_WATERLOGGING,false),"Repaired native pool element failed placement");
            check(raw.equals(before)&&raw.getCompound("normal_config").getInt("ticks_between_spawn")==20,"Repair mutated the cached upstream ranged template");
            check(level.getBlockState(at.offset(1,1,1)).equals(source.filterBlocks(BlockPos.ZERO,new net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings(),Blocks.TRIAL_SPAWNER).getFirst().state()),"Repair changed the authored trial-spawner state");
            var block=(TrialSpawnerBlockEntity)level.getBlockEntity(at.offset(1,1,1));check(block!=null,"Native trial block not placed");
            var trial=block.getTrialSpawner();var normal=trial.getNormalConfig();var ominous=trial.getOminousConfig();
            for(var config:List.of(normal,ominous)) {
                check(config.ticksBetweenSpawn()==160&&config.simultaneousMobs()==4&&config.simultaneousMobsAddedPerPlayer()==2,"Slow-ranged cadence/scaling incorrect");
                check(config.totalMobs()==6&&config.totalMobsAddedPerPlayer()==2,"Native total mob scaling changed");
                check(config.spawnPotentialsDefinition().unwrap().getFirst().data().getEntityToSpawn().getString("id").equals("variantsandventures:"+mob),"Trial variant identity lost");
            }
            check(ominous.spawnPotentialsDefinition().unwrap().getFirst().data().getEquipment().isPresent(),"Ominous equipment lost");
            check(ominous.lootTablesToEject().unwrap().stream().mapToInt(w->w.getWeight().asInt()).sum()==10,"Ominous rewards lost");
            var snapshot=block.saveWithFullMetadata(level.registryAccess());saved.addProperty(Long.toString(block.getBlockPos().asLong()),snapshot.getCompound("normal_config").toString());block.setChanged();mobs.add(mob);
        }
        result.addProperty("repaired",repaired);result.addProperty("missingPlacementControls",repaired?0:2);result.add("placedSlowRanged",mobs);return result;
    }
    @SuppressWarnings("unchecked")
    private static java.util.stream.Stream<ResourceKey<net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool>> aliasTargets(net.minecraft.world.level.levelgen.structure.pools.alias.PoolAliasBinding binding) {
        if(!binding.getClass().getName().equals("dev.worldgen.lithostitched.worldgen.poolalias.RandomEntries"))return binding.allTargets();
        try {
            var pools=(List<HolderSet<net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool>>)binding.getClass().getMethod("pools").invoke(binding);
            return pools.stream().flatMap(HolderSet::stream).map(holder->holder.unwrapKey().orElseThrow());
        } catch(ReflectiveOperationException error){throw new IllegalStateException(error);}
    }
    private static void locations(JsonElement value,Set<String> result) {
        if(value.isJsonArray())value.getAsJsonArray().forEach(v->locations(v,result));
        else if(value.isJsonObject())for(var entry:value.getAsJsonObject().entrySet()) {
            if(entry.getKey().equals("location")&&entry.getValue().isJsonPrimitive()&&entry.getValue().getAsJsonPrimitive().isString())result.add(entry.getValue().getAsString());
            else locations(entry.getValue(),result);
        }
    }
    @SuppressWarnings("unchecked")
    private static JsonObject dungeons(ServerLevel level) throws Exception {
        var config=com.faboslav.variantsandventures.common.VariantsAndVentures.getConfig();
        var fields=List.of("huskSpawnerChance","gelidSpawnerChance","thicketSpawnerChance","straySpawnerChance","boggedSpawnerChance","verdantSpawnerChance");
        var originals=new ArrayList<Double>();for(var name:fields)originals.add(config.getClass().getField(name).getDouble(config));
        var at=new BlockPos(40,100,24);var jungle=level.registryAccess().registryOrThrow(Registries.BIOME).getHolderOrThrow(Biomes.JUNGLE);
        level.getChunk(2,1).fillBiomesFromNoise((qx,qy,qz,sampler)->jungle,level.getChunkSource().randomState().sampler());
        var results=new JsonArray();
        var entriesField=net.neoforged.neoforge.common.MonsterRoomHooks.class.getDeclaredField("monsterRoomMobs");entriesField.setAccessible(true);
        var entries=(List<net.neoforged.neoforge.common.MonsterRoomHooks.MobEntry>)entriesField.get(null);
        int total=entries.stream().mapToInt(e->e.weight().asInt()).sum();check(total>5,"Native dungeon datamap no longer matches bounded RNG fixture");
        try {
            for(int chance:new int[]{0,100})for(int family:new int[]{0,1}) {
                profile(20,chance==0?EncounterProfile.Mode.PER_EMISSION:EncounterProfile.Mode.NATIVE_TYPE,5);
                for(var name:fields)config.getClass().getField(name).setDouble(config,chance);
                for(int x=-3;x<=3;x++)for(int z=-3;z<=3;z++)for(int y=-1;y<=4;y++)level.setBlock(at.offset(x,y,z),Blocks.STONE.defaultBlockState(),2);
                level.setBlock(at.offset(-3,0,0),Blocks.AIR.defaultBlockState(),2);level.setBlock(at.offset(-3,1,0),Blocks.AIR.defaultBlockState(),2);
                var wantedType=family==0?EntityType.SKELETON:EntityType.ZOMBIE;int offset=0;
                for(var entry:entries){if(entry.type()==wantedType)break;offset+=entry.weight().asInt();}
                check(offset<total,"Native base mob missing from monster-room datamap");final int selected=offset;
                var random=new LegacyRandomSource(42){@Override public int nextInt(int bound){return bound==total?selected:0;}};
                check(net.minecraft.world.level.levelgen.feature.Feature.MONSTER_ROOM.place(net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration.INSTANCE,
                        level,level.getChunkSource().getGenerator(),random,at),"Native monster room did not place");
                var block=(SpawnerBlockEntity)level.getBlockEntity(at);check(block!=null,"Native dungeon spawner missing");
                var actual=block.getSpawner().save(new CompoundTag()).getCompound("SpawnData").getCompound("entity").getString("id");
                String expected=chance==0?(family==0?"minecraft:skeleton":"minecraft:zombie"):(family==0?"variantsandventures:verdant":"variantsandventures:thicket");
                check(actual.equals(expected),"Native generation-time conversion did not follow authoritative mode: "+actual+" expected "+expected);results.add(actual);
            }
        } finally {
            for(int i=0;i<fields.size();i++)config.getClass().getField(fields.get(i)).setDouble(config,originals.get(i));
            profile(20,EncounterProfile.Mode.PER_EMISSION,5);
        }
        var result=new JsonObject();result.add("nativeStoredTypesAtZeroAnd100",results);result.addProperty("foreignConfigRestored",true);return result;
    }
}
