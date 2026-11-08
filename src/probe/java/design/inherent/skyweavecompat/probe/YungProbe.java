package design.inherent.skyweavecompat.probe;

import com.google.gson.*;
import design.inherent.skyweavecompat.terrain.structure.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import org.sathrek.sky_archipelago.worldgen.generator.core.SkyIslandChunkGenerator;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Focused native policy and coordinate-frame contracts; never edits personal instances. */
final class YungProbe {
    static void run(MinecraftServer server, Path output) throws Exception {
        var report = new JsonObject();
        try {
            var distribution = ((StructurePlacementState)server.overworld().getChunkSource().getGeneratorState()).skyweave$structureDistribution();
            if (distribution == null) throw new AssertionError("YUNG fixture requires the marked structure policy");
            switch (System.getProperty("neighbors.probe.yung")) {
                case "policy" -> yungPolicy(server, report, distribution);
                case "frames" -> frames(server, report, distribution);
                case "supports" -> supports(server, report);
                case "temples" -> temples(server, report, distribution);
                case "hut-supports" -> hutSupports(server, report);
                default -> throw new IllegalArgumentException("Unknown YUNG contract");
            }
            report.addProperty("complete", true);
        } catch (Throwable failure) {
            report.addProperty("complete", false); report.addProperty("failure", failure.toString()); throw failure;
        } finally { Files.writeString(output, new GsonBuilder().setPrettyPrinting().create().toJson(report) + "\n"); }
    }
    private static void hutSupports(MinecraftServer server, JsonObject report) throws Exception {
        var level = server.overworld();
        var generator = (SkyIslandChunkGenerator)level.getChunkSource().getGenerator();
        var field = ((design.inherent.skyweavecompat.terrain.GeologicalGenerator)(Object)generator).skyweave$field(level.getChunkSource().randomState());
        var settings = generator.resolvedSkyIslandSettings();
        net.minecraft.core.BlockPos empty = null;
        for (int offset = 0; offset <= 1024; offset += 16)
            if (!field.sampleColumn(offset+8,8,settings).exists()) { empty = new net.minecraft.core.BlockPos(offset+8,700,8); break; }
        if (empty == null) throw new AssertionError("No bounded void hut control");
        var host = field.collectIslandPreviewsInRadius(0,0,8192,settings).stream()
                .filter(island -> field.sampleColumn(island.x(),island.z(),settings).exists())
                .findFirst().orElseThrow();
        var supported = new net.minecraft.core.BlockPos(host.x(),field.sampleColumn(host.x(),host.z(),settings).topY()+8,host.z());
        boolean bounded = design.inherent.skyweavecompat.OptionalIntegrations.selection().active().stream()
                .anyMatch(feature -> feature.key.equals("betterwitchhuts.island_support"));
        report.addProperty("expectedBounded", bounded);
        var controls = new JsonArray(); report.add("hutSupports",controls);
        for (String name : List.of("LegProcessor", "FenceLegProcessor")) {
            var processor = (net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessor)Class.forName(
                    "com.yungnickyoung.minecraft.betterwitchhuts.world.processor."+name).getField("INSTANCE").get(null);
            var marker = name.equals("LegProcessor") ? net.minecraft.world.level.block.Blocks.BROWN_STAINED_GLASS : net.minecraft.world.level.block.Blocks.CRIMSON_FENCE;
            var material = name.equals("LegProcessor") ? net.minecraft.world.level.block.Blocks.OAK_LOG : net.minecraft.world.level.block.Blocks.OAK_FENCE;
            var row = new JsonObject(); controls.add(row);row.addProperty("processor",name);
            for (var origin : List.of(empty,supported)) {
                boolean voidCase = origin.equals(empty);
                int floor = voidCase ? level.getMinBuildHeight() : origin.getY()-8;
                for(int y=floor;y<origin.getY();y++) level.setBlock(new net.minecraft.core.BlockPos(origin.getX(),y,origin.getZ()),net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(),2);
                if(!voidCase)level.setBlock(origin.below(8),net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(),2);
                var info = new net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo(origin,marker.defaultBlockState(),null);
                processor.processBlock(level,net.minecraft.core.BlockPos.ZERO,net.minecraft.core.BlockPos.ZERO,info,info,
                        new net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings());
                int count=0;
                for(int y=floor;y<origin.getY();y++) if(level.getBlockState(new net.minecraft.core.BlockPos(origin.getX(),y,origin.getZ())).is(material))count++;
                row.addProperty(voidCase?"voidSupports":"solidSupports",count);
                if(voidCase ? bounded ? count!=0 : count<800 : count!=7)throw new AssertionError("Hut support contract: "+name+" void="+voidCase+" blocks="+count);
            }
        }
    }
    private static void temples(MinecraftServer server, JsonObject report, StructureDistribution distribution) throws Exception {
        var level = server.overworld();
        var generator = (SkyIslandChunkGenerator)level.getChunkSource().getGenerator();
        var settings = generator.resolvedSkyIslandSettings();
        var random = level.getChunkSource().randomState();
        var field = ((design.inherent.skyweavecompat.terrain.GeologicalGenerator)(Object)generator).skyweave$field(random);
        var environmentField = StructureDistribution.class.getDeclaredField("environment"); environmentField.setAccessible(true);
        var environment = (org.sathrek.sky_archipelago.worldgen.generator.structure.StructurePlacementPassContext)environmentField.get(distribution);
        if (environment == null) throw new AssertionError("Native structure inputs were not bound");
        var type = Class.forName("design.inherent.skyweavecompat.terrain.structure.YungTempleRules");
        var capture = type.getDeclaredMethod("capture", java.util.Map.class, net.minecraft.core.RegistryAccess.class); capture.setAccessible(true);
        var allows = type.getDeclaredMethod("allows", StructureLayout.class, List.class, java.util.Map.class,
                org.sathrek.sky_archipelago.worldgen.generator.structure.StructurePlacementPassContext.class); allows.setAccessible(true);
        var sets = server.registryAccess().registryOrThrow(Registries.STRUCTURE_SET);
        var placements = new java.util.HashMap<ResourceLocation, net.minecraft.world.level.levelgen.structure.placement.StructurePlacement>();
        placements.put(ResourceLocation.parse("betterdeserttemples:desert_temple"), sets.get(ResourceLocation.parse("betterdeserttemples:desert_temples")).placement());
        placements.put(ResourceLocation.parse("betterjungletemples:jungle_temple"), sets.get(ResourceLocation.parse("betterjungletemples:jungle_temples")).placement());
        var rules = capture.invoke(null, placements, server.registryAccess());
        int before = level.getChunkSource().getLoadedChunksCount();
        var dry = field.collectIslandPreviewsInRadius(0, 0, 8192, settings).stream()
                .filter(host -> !host.archetype().waterBased() && field.sampleColumn(host.x(), host.z(), settings).exists())
                .findFirst().orElseThrow();
        var root = new StructureVolume.Box(dry.x(), dry.y(), dry.z(), dry.x() + 1, dry.y() + 1, dry.z() + 1);
        var site = new StructureSelection.Site(Math.floorDiv(dry.x(),16), Math.floorDiv(dry.z(),16));
        var desert = new StructureLayout(ResourceLocation.parse("betterdeserttemples:desert_temple"), site, List.of(root), 0,
                root, root, StructureFamily.DESERT_TEMPLES);
        if (!(boolean)allows.invoke(rules, desert, List.of(), java.util.Map.of(), environment))
            throw new AssertionError("Dry native habitat was rejected");
        var villageRoot = new StructureVolume.Box(root.minX() + 96, root.minY(), root.minZ(), root.maxX() + 96, root.maxY(), root.maxZ());
        var village = new StructureLayout(ResourceLocation.parse("ctov:small/village_desert"), site, List.of(villageRoot), 0,
                villageRoot, villageRoot, StructureFamily.VILLAGES);
        if ((boolean)allows.invoke(rules, desert, List.of(), java.util.Map.of(StructureFamily.VILLAGES, List.of(village)), environment))
            throw new AssertionError("The native six-chunk village exclusion disappeared");
        var oceanBiome = server.registryAccess().registryOrThrow(Registries.BIOME).getHolderOrThrow(net.minecraft.world.level.biome.Biomes.DEEP_OCEAN);
        var oceanGenerator = new SkyIslandChunkGenerator(new net.minecraft.world.level.biome.FixedBiomeSource(oceanBiome),
                generator.generatorSettings(), generator.embeddedSettings());
        var oceanField = ((design.inherent.skyweavecompat.terrain.GeologicalGenerator)(Object)oceanGenerator).skyweave$field(random);
        net.minecraft.core.BlockPos wet = null;
        search: for (var host : oceanField.collectIslandPreviewsInRadius(0, 0, 8192, settings))
            for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) {
                int x = host.x() + dx * 16, z = host.z() + dz * 16;
                var water = oceanField.sampleWaterArchetypeColumnProfile(x, z, settings);
                if (water.water()) { wet = new net.minecraft.core.BlockPos(x, water.fluidSurfaceY(), z); break search; }
            }
        if (wet == null) throw new AssertionError("No wet native host; use the explicit admitted-Lagoon ocean fixture");
        var oceanPass = new org.sathrek.sky_archipelago.worldgen.generator.structure.StructurePlacementPassContext(oceanGenerator,
                oceanGenerator.getBiomeSource(), environment.registryAccess(), environment.structureState(), null, environment.chunk(),
                environment.templateManager(), settings, oceanField, environment.levelSeed(), random, environment.chunkPos(), environment.sectionPos());
        var wetRoot = new StructureVolume.Box(wet.getX(), wet.getY(), wet.getZ(), wet.getX()+1, wet.getY()+1, wet.getZ()+1);
        for (var id : placements.keySet()) {
            var layout = new StructureLayout(id, site, List.of(wetRoot), 0, wetRoot, wetRoot,
                    id.getNamespace().equals("betterdeserttemples") ? StructureFamily.DESERT_TEMPLES : StructureFamily.JUNGLE_TEMPLES);
            if ((boolean)allows.invoke(rules, layout, List.of(), java.util.Map.of(), oceanPass))
                throw new AssertionError("Wet native host was admitted for " + id);
        }
        if (level.getChunkSource().getLoadedChunksCount() != before) throw new AssertionError("Temple metadata controls loaded chunks");
        report.addProperty("dryAccepted", true); report.addProperty("nearVillageRejected", true);
        report.addProperty("wetTemplesRejected", placements.size()); report.addProperty("chunkLoads", 0);
        report.addProperty("evidence", "Registered placement codec, actual native island metadata and translated synthetic root bounds; not template realization");
    }
    private static void yungPolicy(MinecraftServer server, JsonObject report, StructureDistribution distribution) {
        report.add("yungReplacements", new Gson().toJsonTree(distribution.yungReplacements()));
        var observations = new JsonArray(); report.add("yungSuppression", observations);
        var level = server.overworld();
        var generator = (SkyIslandChunkGenerator)level.getChunkSource().getGenerator();
        var registry = server.registryAccess().registryOrThrow(Registries.STRUCTURE);
        var position = new net.minecraft.world.level.ChunkPos(0, 0);
        var chunk = level.getChunk(0, 0);
        for (String id : List.of("minecraft:desert_pyramid", "minecraft:jungle_pyramid", "minecraft:swamp_hut",
                "minecraft:monument", "minecraft:mineshaft", "minecraft:mineshaft_mesa", "betterdeserttemples:desert_temple")) {
            var structure = registry.get(ResourceLocation.parse(id));
            if (structure == null) continue;
            // Exhaust the native budget: an unsuppressed call must reach that gate, whereas
            // a suppressed one must stop before invoking native structure generation at all.
            var budget = org.sathrek.sky_archipelago.worldgen.generator.structure.v2.common.StructurePlacementBudget.limited(1, 1, 1);
            budget.tryGeneration();
            var context = new org.sathrek.sky_archipelago.worldgen.generator.structure.v2.common.PlacementRequest.StructureGenerationContext(
                    generator, generator.getBiomeSource(), server.registryAccess(), level.getChunkSource().randomState(),
                    server.getStructureManager(), 0, budget);
            var service = new org.sathrek.sky_archipelago.worldgen.generator.structure.v2.common.StructureGenerationAttemptService();
            var foreign = StructureWorldScope.run(null, () -> service.generate(ResourceLocation.parse(id), structure, context, level.getSeed(), position, chunk));
            var owned = StructureWorldScope.run(distribution, () -> service.generate(ResourceLocation.parse(id), structure, context, level.getSeed(), position, chunk));
            var expected = distribution.suppresses(structure)
                    ? org.sathrek.sky_archipelago.worldgen.generator.structure.v2.common.StructureGenerationAttemptService.Outcome.INVALID
                    : org.sathrek.sky_archipelago.worldgen.generator.structure.v2.common.StructureGenerationAttemptService.Outcome.BUDGET_EXHAUSTED;
            if (owned.outcome() != expected || foreign.outcome()
                    != org.sathrek.sky_archipelago.worldgen.generator.structure.v2.common.StructureGenerationAttemptService.Outcome.BUDGET_EXHAUSTED)
                throw new AssertionError("YUNG suppression or foreign boundary mismatch: " + id);
            var row = new JsonObject(); row.addProperty("id", id); row.addProperty("suppressed", distribution.suppresses(structure));
            row.addProperty("owned", owned.outcome().name()); row.addProperty("foreign", foreign.outcome().name()); observations.add(row);
            var remote = new net.minecraft.world.level.ChunkPos(60000, 60000);
            var placement = new net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement(
                    1, 0, net.minecraft.world.level.levelgen.structure.placement.RandomSpreadType.LINEAR, 1729);
            var presence = level.structureManager().checkStructurePresence(remote, structure, placement, false);
            row.addProperty("prospectivePresence", presence.name());
            boolean denied = generator.resolvedSkyIslandSettings().advanced().structurePlacementPolicy().isDenied(ResourceLocation.parse(id));
            var expectedPresence = distribution.suppresses(structure) || denied
                    ? net.minecraft.world.level.levelgen.structure.StructureCheckResult.START_NOT_PRESENT
                    : net.minecraft.world.level.levelgen.structure.StructureCheckResult.CHUNK_LOAD_NEEDED;
            if (presence != expectedPresence || level.getChunkSource().getChunkNow(remote.x, remote.z) != null)
                throw new AssertionError("Prospective lookup disagrees with policy or loaded a remote chunk: " + id + " " + presence);
        }
        var target = design.inherent.skyweavecompat.configuration.YungReplacementBindings.TARGETS.getFirst();
        if (distribution.yungReplacements().containsKey(target.nativeType())) {
            var config = net.neoforged.fml.config.ModConfigs.getConfigSet(net.neoforged.fml.config.ModConfig.Type.COMMON).stream()
                    .filter(candidate -> candidate.getModId().equals(target.mod()) && candidate.getFileName().equals(target.file())).findFirst().orElseThrow();
            var spec = (net.neoforged.neoforge.common.ModConfigSpec)config.getSpec();
            if (!(spec.getValues().get(target.path()) instanceof net.neoforged.neoforge.common.ModConfigSpec.BooleanValue binding))
                throw new AssertionError("Native boolean binding changed");
            boolean before = binding.get();
            try {
                binding.set(!before); // In-memory isolated fixture only; never saved to disk.
                binding.clearCache(); // Emulate the native restart boundary for this world-restart binding.
                if (binding.get() == before) throw new AssertionError("Native fixture binding did not change");
                if (distribution.yungReplacements().get(target.nativeType()) != before)
                    throw new AssertionError("A config edit mutated a fitted world's policy");
                var ops = net.minecraft.resources.RegistryOps.create(com.mojang.serialization.JsonOps.INSTANCE, server.registryAccess());
                var encoded = net.minecraft.world.level.chunk.ChunkGenerator.CODEC.encodeStart(ops, generator).getOrThrow();
                var independent = net.minecraft.world.level.chunk.ChunkGenerator.CODEC.parse(ops, encoded).getOrThrow();
                if (((YungWorldPolicy)(Object)independent).skyweave$yungReplacements().get(target.nativeType()) == before)
                    throw new AssertionError("Independent generator reused another world's config snapshot");
                report.addProperty("yungPolicySnapshotIsolated", true);
            } finally { binding.set(before); binding.clearCache(); }
        }
    }

    private static void frames(MinecraftServer server, JsonObject report, StructureDistribution distribution) {
        var level = server.overworld(); var generator = level.getChunkSource().getGenerator();
        var id = ResourceLocation.parse("bettermineshafts:mineshaft_oak");
        var structure = server.registryAccess().registryOrThrow(Registries.STRUCTURE).get(id);
        if (structure == null) throw new AssertionError("Missing native Better Mineshaft structure");
        var position = new net.minecraft.world.level.ChunkPos(0, 0);
        var start = structure.generate(server.registryAccess(), generator, generator.getBiomeSource(), level.getChunkSource().randomState(),
                server.getStructureManager(), level.getSeed(), position, 0, level.getChunk(0, 0), biome -> true);
        if (!start.isValid()) throw new AssertionError("Native mineshaft fixture did not assemble");
        var context = net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext.fromLevel(level);
        var original = start.getPieces().getFirst(); var tag = original.createTag(context);
        if (!tag.contains("centerPos")) throw new AssertionError("Native entrance's saved-coordinate contract changed");
        int[] before = tag.getIntArray("centerPos");
        var owned = original.getType().load(context, tag.copy());
        var foreign = original.getType().load(context, tag.copy());
        StructureWorldScope.run(distribution, () -> { owned.move(11, 250, -7); return null; });
        StructureWorldScope.run(null, () -> { foreign.move(11, 250, -7); return null; });
        int[] after = owned.createTag(context).getIntArray("centerPos");
        int[] nativeAfter = foreign.createTag(context).getIntArray("centerPos");
        report.add("originalCenter", new Gson().toJsonTree(before)); report.add("ownedCenter", new Gson().toJsonTree(after));
        report.add("foreignCenter", new Gson().toJsonTree(nativeAfter));
        report.addProperty("ownedBounds", owned.getBoundingBox().toString());
        report.addProperty("foreignBounds", foreign.getBoundingBox().toString());
        if (!java.util.Arrays.equals(before, nativeAfter)) throw new AssertionError("Foreign entrance movement changed");
        if (!java.util.Arrays.equals(after, new int[]{before[0] + 11, before[1] + 250, before[2] - 7}))
            throw new AssertionError("Owned entrance center did not follow its relocated box");
        if (owned.getBoundingBox().maxY() >= level.getMaxBuildHeight()) throw new AssertionError("Owned entrance reservation exceeded the dimension ceiling");
        StructureWorldScope.run(distribution, () -> { owned.move(-11, -250, 7); owned.move(11, 250, -7); return null; });
        if (!java.util.Arrays.equals(after, owned.createTag(context).getIntArray("centerPos"))
                || owned.getBoundingBox().maxY() != level.getMaxBuildHeight() - 1)
            throw new AssertionError("Repeated entrance translations lost their coordinate frame or fixed ceiling");
        var reloaded = owned.getType().load(context, owned.createTag(context));
        if (!java.util.Arrays.equals(after, reloaded.createTag(context).getIntArray("centerPos")))
            throw new AssertionError("Relocated entrance center did not survive native serialization");
        report.addProperty("reloadedFrameMatch", true);
    }
    private static void supports(MinecraftServer server, JsonObject report) {
        var level = server.overworld();
        var generator = (SkyIslandChunkGenerator)level.getChunkSource().getGenerator();
        var field = ((design.inherent.skyweavecompat.terrain.GeologicalGenerator)(Object)generator).skyweave$field(level.getChunkSource().randomState());
        var settings = generator.resolvedSkyIslandSettings();
        var structure = server.registryAccess().registryOrThrow(Registries.STRUCTURE).get(ResourceLocation.parse("bettermineshafts:mineshaft_oak"));
        var ops = net.minecraft.resources.RegistryOps.create(com.mojang.serialization.JsonOps.INSTANCE, server.registryAccess());
        var encoded = net.minecraft.world.level.levelgen.structure.Structure.DIRECT_CODEC.encodeStart(ops, structure).getOrThrow().getAsJsonObject();
        var config = com.yungnickyoung.minecraft.bettermineshafts.world.config.BetterMineshaftConfiguration.CODEC
                .parse(ops, encoded.get("config")).getOrThrow();
        net.minecraft.core.BlockPos empty = null;
        for (int offset = 0; offset <= 1024; offset += 16)
            if (!field.sampleColumn(offset + 8, 8, settings).exists()) { empty = new net.minecraft.core.BlockPos(offset + 8, 700, 8); break; }
        if (empty == null) throw new AssertionError("No bounded void support fixture");
        for (int y = level.getMinBuildHeight(); y < empty.getY(); y++)
            level.setBlock(new net.minecraft.core.BlockPos(empty.getX(), y, empty.getZ()), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 2);
        new SupportFixture(config, empty).leg(level);
        int voidLogs = 0;
        for (int y = level.getMinBuildHeight(); y < empty.getY(); y++)
            if (level.getBlockState(new net.minecraft.core.BlockPos(empty.getX(), y, empty.getZ())).is(net.minecraft.world.level.block.Blocks.OAK_LOG)) voidLogs++;
        report.addProperty("voidColumnLogs", voidLogs);
        net.minecraft.core.BlockPos supported = null;
        for (var preview : field.collectIslandPreviewsInRadius(0, 0, 8192, settings)) {
            var column = field.sampleColumn(preview.x(), preview.z(), settings);
            if (column.exists() && column.topY() + 8 < level.getMaxBuildHeight()) {
                supported = new net.minecraft.core.BlockPos(preview.x(), column.topY() + 8, preview.z()); break;
            }
        }
        if (supported == null) throw new AssertionError("No bounded solid support fixture");
        for (int y = supported.getY() - 7; y < supported.getY(); y++) level.setBlock(
                new net.minecraft.core.BlockPos(supported.getX(), y, supported.getZ()), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 2);
        level.setBlock(supported.below(8), net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(), 2);
        new SupportFixture(config, supported).leg(level);
        int supportedLogs = 0;
        for (int y = supported.getY() - 7; y < supported.getY(); y++) if (level.getBlockState(
                new net.minecraft.core.BlockPos(supported.getX(), y, supported.getZ())).is(net.minecraft.world.level.block.Blocks.OAK_LOG)) supportedLogs++;
        report.addProperty("supportedColumnLogs", supportedLogs);
        new SupportFixture(config, empty).legOrChain(level);
        int voidChainLogs = 0;
        for (int y = level.getMinBuildHeight(); y < empty.getY(); y++) if (level.getBlockState(
                new net.minecraft.core.BlockPos(empty.getX(), y, empty.getZ())).is(net.minecraft.world.level.block.Blocks.OAK_LOG)) voidChainLogs++;
        for (int y = supported.getY() - 7; y < supported.getY(); y++) level.setBlock(
                new net.minecraft.core.BlockPos(supported.getX(), y, supported.getZ()), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 2);
        new SupportFixture(config, supported).legOrChain(level);
        int supportedChainLogs = 0;
        for (int y = supported.getY() - 7; y < supported.getY(); y++) if (level.getBlockState(
                new net.minecraft.core.BlockPos(supported.getX(), y, supported.getZ())).is(net.minecraft.world.level.block.Blocks.OAK_LOG)) supportedChainLogs++;
        report.addProperty("voidLegOrChainLogs", voidChainLogs); report.addProperty("supportedLegOrChainLogs", supportedChainLogs);
        report.addProperty("voidPosition", empty.toShortString()); report.addProperty("supportedPosition", supported.toShortString());
        if (voidLogs != 0 || supportedLogs != 7 || voidChainLogs != 0 || supportedChainLogs != 7)
            throw new AssertionError("Void or supported native leg behavior differs: " + voidLogs + "/" + supportedLogs + "/" + voidChainLogs + "/" + supportedChainLogs);
    }

    private static final class SupportFixture extends com.yungnickyoung.minecraft.bettermineshafts.world.generator.pieces.BetterMineshaftPiece {
        SupportFixture(com.yungnickyoung.minecraft.bettermineshafts.world.config.BetterMineshaftConfiguration config, net.minecraft.core.BlockPos position) {
            super(net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType.MINE_SHAFT_ROOM, 0, config,
                    new net.minecraft.world.level.levelgen.structure.BoundingBox(position.getX()-2, position.getY(), position.getZ()-2,
                            position.getX()+2, position.getY()+4, position.getZ()+2));
            setOrientation(net.minecraft.core.Direction.SOUTH);
        }
        void leg(net.minecraft.server.level.ServerLevel level) {
            generateLeg(level, net.minecraft.util.RandomSource.create(42), new net.minecraft.world.level.levelgen.structure.BoundingBox(
                    boundingBox.minX(), level.getMinBuildHeight(), boundingBox.minZ(), boundingBox.maxX(), level.getMaxBuildHeight()-1, boundingBox.maxZ()),
                    2, 2, new com.yungnickyoung.minecraft.yungsapi.api.world.randomize.BlockStateRandomizer(net.minecraft.world.level.block.Blocks.OAK_LOG.defaultBlockState()));
        }
        void legOrChain(net.minecraft.server.level.ServerLevel level) {
            generateLegOrChain(level, net.minecraft.util.RandomSource.create(42), new net.minecraft.world.level.levelgen.structure.BoundingBox(
                    boundingBox.minX(), level.getMinBuildHeight(), boundingBox.minZ(), boundingBox.maxX(), level.getMaxBuildHeight()-1, boundingBox.maxZ()),
                    2, 2, new com.yungnickyoung.minecraft.yungsapi.api.world.randomize.BlockStateRandomizer(net.minecraft.world.level.block.Blocks.OAK_LOG.defaultBlockState()));
        }
        @Override public void postProcess(net.minecraft.world.level.WorldGenLevel level, net.minecraft.world.level.StructureManager structures,
                net.minecraft.world.level.chunk.ChunkGenerator generator, net.minecraft.util.RandomSource random,
                net.minecraft.world.level.levelgen.structure.BoundingBox bounds, net.minecraft.world.level.ChunkPos chunk, net.minecraft.core.BlockPos position) {}
    }
    private YungProbe() {}
}
