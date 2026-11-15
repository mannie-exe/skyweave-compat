package design.inherent.skyweavecompat.probe;

import com.google.gson.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.templatesystem.LiquidSettings;
import net.minecraft.world.phys.AABB;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import design.inherent.skyweavecompat.terrain.structure.*;
import design.inherent.skyweavecompat.terrain.GeologicalGenerator;
import org.sathrek.sky_archipelago.worldgen.generator.core.SkyIslandChunkGenerator;

/** Exercise the missing upstream NPC branches through native pool/template entity placement. */
final class VillagePoolProbe {
    private static Path pendingOutput;
    private static JsonObject readiness;
    private static long readinessDeadline;
    private static int readinessTicks;
    private static final List<net.minecraft.world.level.ChunkPos> ENTITY_CHUNKS=java.util.stream.IntStream.range(0,4)
            .mapToObj(x->new net.minecraft.world.level.ChunkPos(x,0)).toList();

    static void start(MinecraftServer server,Path output) {
        readiness=new JsonObject();var before=new JsonArray();
        for(var chunk:ENTITY_CHUNKS) {
            server.overworld().getChunk(chunk.x,chunk.z);
            before.add(server.overworld().areEntitiesLoaded(chunk.toLong()));
            server.overworld().setChunkForced(chunk.x,chunk.z,true);
        }
        readiness.add("entitiesLoadedAtFullRequest",before);
        readinessTicks=0;readinessDeadline=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(30);
        pendingOutput=output;
    }
    static void tick(MinecraftServer server) {
        if(pendingOutput==null)return;
        readinessTicks++;
        boolean ready=ENTITY_CHUNKS.stream().allMatch(chunk->server.overworld().areEntitiesLoaded(chunk.toLong()));
        if(!ready && readinessTicks<200 && System.nanoTime()<readinessDeadline)return;
        var output=pendingOutput;pendingOutput=null;
        readiness.addProperty("actualEntitiesLoadedBeforePlacement",ready);readiness.addProperty("waitedServerTicks",readinessTicks);
        try {
            if(!ready) {
                var failed=new JsonObject();failed.addProperty("complete",false);failed.add("entityReadiness",readiness);
                Files.writeString(output,failed.toString());throw new AssertionError("Native entity sections never became available");
            }
            run(server,output);
        } catch(Exception failure) {throw new IllegalStateException("Native village pool check failed",failure);}
        finally {
            for(var chunk:ENTITY_CHUNKS)server.overworld().setChunkForced(chunk.x,chunk.z,false);
            server.halt(false);
        }
    }
    static void run(MinecraftServer server, Path output) throws Exception {
        var report = new JsonObject(); var rows = new JsonArray(); report.add("pools", rows);
        if(readiness!=null)report.add("entityReadiness",readiness);
        try {
            var level = server.overworld(); var registry = server.registryAccess().registryOrThrow(Registries.TEMPLATE_POOL);
            var structures = server.registryAccess().registryOrThrow(Registries.STRUCTURE);
            var state = (StructurePlacementState)level.getChunkSource().getGeneratorState();
            var ctovId = ResourceLocation.parse("ctov:small/village_plains");
            var ctov = structures.get(ctovId);
            if (ctov != null) {
                var classification = new JsonObject(); report.add("ctov", classification);
                classification.addProperty("bridged", ctov instanceof IslandJigsaw);
                var distribution = state.skyweave$structureDistribution();
                classification.addProperty("family", String.valueOf(distribution.family(ctovId)));
                var generator = (SkyIslandChunkGenerator)level.getChunkSource().getGenerator();
                var field = ((GeologicalGenerator)(Object)generator).skyweave$field(level.getChunkSource().randomState());
                classification.addProperty("size", field.structureSize(ctov, server.registryAccess()).toString());
                if (!(ctov instanceof IslandJigsaw) || distribution.family(ctovId) != StructureFamily.VILLAGES)
                    throw new AssertionError("CTOV bridge or tagged family missing");
                boolean foreignRoute = org.sathrek.sky_archipelago.worldgen.generator.structure.StructureRoutingPolicy
                        .shouldRouteVillagePreAnchor(ctovId, ctov, org.sathrek.sky_archipelago.worldgen.structure.StructurePlacementCategory.GROUND_VILLAGE);
                if (foreignRoute) throw new AssertionError("CTOV route leaked outside owned placement scope");
                classification.addProperty("foreignRoutePreserved", true);
            }
            if (server.getResourceManager().getResource(ResourceLocation.parse(
                    "skyweave_compat:lithostitched/worldgen_modifier/villages/plains/house.json")).isPresent())
                composition(server, report);
            report.add("supportInputs", VillageSupportSurvey.run(server));
            var coverage=StructurePlacementCoverage.run(server);report.add("placementCoverage",coverage);
            if(Boolean.getBoolean("neighbors.probe.placementFollowup")) {
                boolean dwelling=false,farm=false;
                for(var value:coverage.getAsJsonObject("variants").asMap().values()) {
                    var row=value.getAsJsonObject();
                    if(row.has("template") && row.get("template").getAsString().equals("kaisyn:village/beach_lighthouse/side/beach_main_house_1")) {
                        if(!row.get("role").getAsString().equals("BUILDING") || row.get("rawBeds").getAsInt()==0)
                            throw new AssertionError("Native lighthouse dwelling still bypasses inhabited-building fitting");
                        dwelling=true;
                    }
                    if(row.has("template") && row.get("template").getAsString().equals("skyvillages:farm_2")) {
                        if(!row.get("role").getAsString().equals("FARM") || row.get("rawBeds").getAsInt()==0)
                            throw new AssertionError("Dwelling fallback replaced existing native farm intent");
                        farm=true;
                    }
                }
                if(!dwelling)throw new AssertionError("Lighthouse dwelling role control was not exercised");
                if(!farm)throw new AssertionError("Native farm role precedence control was not exercised");
            }
            int index = 0;
            for (String id : List.of("kaisyn:village/beach_lighthouse/villager_lighthouse_master",
                    "kaisyn:village/exclusives/mediterranean/villager_bishop",
                    "kaisyn:village/meadow_swiss/villagers",
                    "kaisyn:village/sparse_jungle_polynesian/villager_village_chief")) {
                var row = new JsonObject(); rows.add(row); row.addProperty("id", id);
                var pool = registry.get(ResourceLocation.parse(id));
                row.addProperty("present", pool != null);
                if (pool == null) throw new AssertionError("Missing villager pool: " + id);
                var random = RandomSource.create(42 + index);
                var element = pool.getRandomTemplate(random);
                var position = new BlockPos(index++ * 16, 720, 0);
                level.getChunk(position.getX() >> 4, 0);
                var bounds = element.getBoundingBox(server.getStructureManager(), position, Rotation.NONE);
                if (!element.place(server.getStructureManager(), level, level.structureManager(), level.getChunkSource().getGenerator(),
                        position, position, Rotation.NONE, bounds, random, LiquidSettings.APPLY_WATERLOGGING, false))
                    throw new AssertionError("Native villager template placement refused: " + id);
                var villagers = level.getEntitiesOfClass(Villager.class, new AABB(position).inflate(4));
                row.addProperty("villagers", villagers.size());
                if(villagers.size()!=1) {
                    var known=new JsonArray();
                    for(var entity:level.getAllEntities())if(entity instanceof Villager && known.size()<16) {
                        var seen=new JsonObject();seen.addProperty("position",entity.position().toString());
                        seen.addProperty("removed",entity.isRemoved());known.add(seen);
                    }
                    row.add("knownVillagersOnFailure",known);
                    row.addProperty("chunkImmediatelyAvailable",level.getChunkSource().getChunkNow(position.getX()>>4,position.getZ()>>4)!=null);
                }
                if (villagers.size() != 1) throw new AssertionError("Expected one native villager from " + id);
                var villager = villagers.getFirst();
                row.addProperty("type", villager.getVillagerData().getType().toString());
                row.addProperty("profession", villager.getVillagerData().getProfession().toString());
                row.addProperty("baby", villager.isBaby());
                villager.discard();
            }
            if(Boolean.getBoolean("neighbors.probe.placementFollowup"))report.add("placementFollowup",PlacementFollowupControls.run(server));
            report.addProperty("complete", true);
        } catch (Throwable failure) {
            report.addProperty("complete", false); report.addProperty("failure", failure.toString());
            throw failure;
        } finally {
            Files.writeString(output, new GsonBuilder().setPrettyPrinting().create().toJson(report) + "\n");
        }
    }

    private static void composition(MinecraftServer server, JsonObject report) {
        var access = server.registryAccess();
        var ops = net.minecraft.resources.RegistryOps.create(com.mojang.serialization.JsonOps.INSTANCE, access);
        var sets = access.registryOrThrow(Registries.STRUCTURE_SET);
        var observed = new JsonObject(); report.add("composition", observed);
        for (String family : List.of("villages", "pillager_outposts")) {
            var set = sets.get(ResourceLocation.withDefaultNamespace(family));
            var ids = set.structures().stream().map(entry -> entry.structure().getRegisteredName()).toList();
            if (new java.util.HashSet<>(ids).size() != ids.size()) throw new AssertionError("Duplicate schedule: " + family);
            if (family.equals("villages") && ids.stream().anyMatch(id -> id.startsWith("minecraft:")))
                throw new AssertionError("Untouched vanilla settlement fallback");
            var expected = family.equals("villages") ? StructureFamily.VILLAGES : StructureFamily.OUTPOSTS;
            if (set.structures().stream().anyMatch(entry -> StructureMembership.family(entry.structure()) != expected))
                throw new AssertionError("Unqualified schedule member: " + family);
            observed.add(family, net.minecraft.world.level.levelgen.structure.StructureSet.DIRECT_CODEC.encodeStart(ops, set).getOrThrow());
        }
        if (!sets.get(ResourceLocation.parse("towns_and_towers:towers")).structures().isEmpty())
            throw new AssertionError("Independent tower schedule still active");
        var towns = sets.get(ResourceLocation.parse("towns_and_towers:towns")).structures();
        if (towns.size() != 1 || !towns.getFirst().structure().getRegisteredName().equals("towns_and_towers:exclusives/village_piglin"))
            throw new AssertionError("Independent town schedule still active");
        observed.addProperty("sourceSchedulesReplaced", true);
        var pools = access.registryOrThrow(Registries.TEMPLATE_POOL);
        var house = net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool.DIRECT_CODEC
                .encodeStart(ops, pools.get(ResourceLocation.parse("ctov:village/plains/house"))).getOrThrow();
        observed.add("plainsHousePool", house);
        var encoded = house.toString();
        if (!encoded.contains("lithostitched:delegating") || !encoded.contains("minecraft:village/plains/houses/")
                || !encoded.contains("kaisyn:village/")) throw new AssertionError("Donor modifiers were not applied");
        var definition = net.minecraft.world.level.levelgen.structure.Structure.DIRECT_CODEC.encodeStart(ops,
                access.registryOrThrow(Registries.STRUCTURE).get(ResourceLocation.parse("ctov:small/village_plains"))).getOrThrow();
        observed.add("plainsStructure", definition);
        if (!definition.toString().contains("minecraft:village/plains/streets"))
            throw new AssertionError("Native structure aliases were not applied");
    }
    private VillagePoolProbe() {}
}
