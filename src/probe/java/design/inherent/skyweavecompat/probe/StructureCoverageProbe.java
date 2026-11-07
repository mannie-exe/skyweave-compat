package design.inherent.skyweavecompat.probe;

import org.sathrek.sky_archipelago.worldgen.generator.field.model.IslandFamily;
import com.google.gson.*;
import design.inherent.skyweavecompat.terrain.GeologicalGenerator;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.structure.placement.ConcentricRingsStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;
import org.sathrek.sky_archipelago.worldgen.generator.core.SkyIslandChunkGenerator;
import org.sathrek.sky_archipelago.worldgen.generator.field.IslandField;
import java.nio.file.*;
import java.util.*;

/** Natural candidate starts, followed by bounded realization of progression-critical content. */
final class StructureCoverageProbe {
    private StructureCoverageProbe() {}
    private static BlockPos lootPosition;
    private static String lootTable;
    private static Path pendingOutput;

    static void run(MinecraftServer server, Path output) throws Exception {
        var level = server.overworld();
        var generator = (SkyIslandChunkGenerator)level.getChunkSource().getGenerator();
        var state = level.getChunkSource().getGeneratorState();
        var field = ((GeologicalGenerator)(Object)generator).skyweave$field(level.getChunkSource().randomState());
        var settings = generator.resolvedSkyIslandSettings();
        var registry = server.registryAccess().registryOrThrow(Registries.STRUCTURE);
        var anchors = field.collectIslandPreviewsInRadius(0, 0, 16000, settings).stream()
                .filter(p -> p.family() == IslandFamily.ANCHOR_PLATEAU)
                .sorted(Comparator.comparingDouble(p -> Math.hypot(p.x(), p.z())))
                .limit(Math.clamp(Integer.getInteger("neighbors.probe.structureClusters", 16), 1, 64)).toList();
        var report = new JsonObject(); report.addProperty("seed", level.getSeed());
        var rows = new JsonArray();
        var names = Boolean.getBoolean("neighbors.probe.surfaceStructures")
                ? List.of("village_plains", "village_desert", "village_savanna", "village_snowy", "village_taiga",
                        "pillager_outpost", "mansion", "desert_pyramid", "jungle_pyramid", "igloo", "swamp_hut",
                        "trail_ruins", "ruined_portal", "ruined_portal_mountain", "buried_treasure",
                        "shipwreck", "shipwreck_beached", "monument", "ocean_ruin_cold", "ocean_ruin_warm")
                : List.of("stronghold", "trial_chambers", "ancient_city", "mineshaft", "mineshaft_mesa");
        String selectedId = System.getProperty("neighbors.probe.structureId", "");
        if (!selectedId.isEmpty()) {
            if (!names.contains(selectedId)) throw new IllegalArgumentException("Structure is outside this fixture: " + selectedId);
            var family = design.inherent.skyweavecompat.terrain.structure.StructureFamily.forId("minecraft:" + selectedId);
            names = family == null ? List.of(selectedId) : family.ids().stream().sorted()
                    .map(id -> id.substring("minecraft:".length())).toList();
        }
        report.addProperty("layoutsOnly", Boolean.getBoolean("neighbors.probe.layoutsOnly"));
        for (String name : names) {
            var id = ResourceLocation.withDefaultNamespace(name);
            var holder = registry.getHolder(id).orElseThrow();
            var candidates = new LinkedHashSet<ChunkPos>();
            var observedStarts = new LinkedHashSet<ChunkPos>();
            var nativeReport = org.sathrek.sky_archipelago.worldgen.benchmark.StructureBenchmarkRecorder
                    .report("skyweave", level.getSeed(), List.of(), Map.of());
            for (var observed : nativeReport.samples()) if (observed.accepted() && observed.structureId().equals(id.toString()))
                observedStarts.add(new ChunkPos(observed.chunkX(),observed.chunkZ()));
            String previous = System.getProperty("neighbors.probe.previousStructures", "");
            var replay = new LinkedHashSet<ChunkPos>();
            if (!previous.isEmpty() && Files.isRegularFile(Path.of(previous))) {
                var prior = JsonParser.parseString(Files.readString(Path.of(previous))).getAsJsonObject();
                for (var row : prior.getAsJsonArray("structures")) if (row.getAsJsonObject().get("id").getAsString().equals(id.toString()))
                    for (var old : row.getAsJsonObject().getAsJsonArray("candidates")) {
                        var sample = old.getAsJsonObject();
                        replay.add(new ChunkPos(sample.get("chunkX").getAsInt(),sample.get("chunkZ").getAsInt()));
                        if (sample.get("valid").getAsBoolean()) observedStarts.add(new ChunkPos(sample.get("chunkX").getAsInt(),sample.get("chunkZ").getAsInt()));
                    }
            }
            // Realizing a footprint discovers additional nearby starts. Reopening
            // must replay the original candidates, including rejected ones, rather
            // than let those newly observed starts displace the tested sample.
            candidates.addAll(replay.isEmpty() ? observedStarts : replay);
            for (var placement : replay.isEmpty() ? state.getPlacementsForStructure(holder) : List.<StructurePlacement>of()) {
                if (placement instanceof design.inherent.skyweavecompat.terrain.structure.ArchipelagoStructurePlacement adapted) {
                    var planning = new JsonArray();
                    for (var anchor : anchors) {
                        // A crater/crescent may have no terrain at its center, but still has an accepted cluster owner.
                        var owner = anchor.terrainPlan() == null ? adapted.distribution().owner(anchor.x(), anchor.z())
                                : adapted.distribution().owner(anchor.terrainPlan().descriptor());
                        if (owner == null) continue;
                        var plan = adapted.distribution().plan(owner);
                        var row = new JsonObject(); row.add("owner", new Gson().toJsonTree(owner));
                        row.add("eligibleChunks", new Gson().toJsonTree(plan.eligibleChunks()));
                        row.add("sites", new Gson().toJsonTree(plan.sites()));
                        planning.add(row);
                        for (var site : plan.sites(adapted.family())) {
                            if (!adapted.isStructureChunk(state, site.chunkX(), site.chunkZ()))
                                throw new AssertionError("Selected family destination is not a placement candidate");
                            candidates.add(new ChunkPos(site.chunkX(), site.chunkZ()));
                        }
                    }
                    report.add("distributionPlans", planning);
                } else if (placement instanceof ConcentricRingsStructurePlacement ring) {
                    var positions = state.getRingPositionsFor(ring);
                    if (positions != null) positions.stream()
                            .sorted(Comparator.comparingDouble(p -> Math.hypot(p.x, p.z)))
                            .limit(12).forEach(candidates::add);
                } else if (placement instanceof RandomSpreadStructurePlacement spread) {
                    for (var anchor : anchors) {
                        int cx = anchor.x() >> 4, cz = anchor.z() >> 4;
                        if (name.startsWith("mineshaft") || name.equals("buried_treasure")) {
                            // Mineshaft spacing is one chunk with a frequency gate.
                            // Examine descriptor-local candidates without generating their neighbors.
                            int selected = 0;
                            search: for (int radius = 0; radius <= 10; radius++)
                                for (int dx = -radius; dx <= radius; dx++) for (int dz = -radius; dz <= radius; dz++) {
                                    if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) continue;
                                    if (spread.isStructureChunk(state, cx + dx, cz + dz)) {
                                        candidates.add(new ChunkPos(cx + dx, cz + dz));
                                        if (++selected >= 2) break search;
                                    }
                                }
                        } else if (settings.advanced().structurePlacementPolicy().categoryFor(id)
                                == org.sathrek.sky_archipelago.worldgen.structure.StructurePlacementCategory.WATER) {
                            // A sparse island can straddle a placement-region boundary. Survey
                            // actual neighboring-region candidates in usable water, not only the
                            // region containing the descriptor's center. This is not a frequency sample.
                            var local = new ArrayList<ChunkPos>();
                            for (int dx=-1;dx<=1;dx++) for (int dz=-1;dz<=1;dz++) {
                                var potential = spread.getPotentialStructureChunk(level.getSeed(),cx+dx*spread.spacing(),cz+dz*spread.spacing());
                                if (spread.isStructureChunk(state,potential.x,potential.z)
                                        && field.sampleWaterArchetypeColumnProfile(potential.getMiddleBlockX(),potential.getMiddleBlockZ(),settings).water())
                                    local.add(potential);
                            }
                            local.sort(Comparator.comparingDouble(p -> Math.hypot(p.getMiddleBlockX()-anchor.x(),p.getMiddleBlockZ()-anchor.z())));
                            candidates.addAll(local);
                        } else {
                            var potential = spread.getPotentialStructureChunk(level.getSeed(), cx, cz);
                            if (spread.isStructureChunk(state, potential.x, potential.z)) candidates.add(potential);
                        }
                    }
                }
            }
            var row = new JsonObject(); row.addProperty("id", id.toString());
            row.addProperty("category", settings.advanced().structurePlacementPolicy().categoryFor(id).name());
            if (settings.advanced().structurePlacementPolicy().categoryFor(id)
                    == org.sathrek.sky_archipelago.worldgen.structure.StructurePlacementCategory.WATER)
                row.addProperty("candidateSelection","Wet-center neighboring natural placement regions; not a frequency estimate");
            var samples = new JsonArray(); int accepted = 0, realized = 0;
            var spawn = level.getSharedSpawnPos();
            var selectedCandidates = new ArrayList<>(candidates.stream().filter(candidate ->
                    !Boolean.getBoolean("neighbors.probe.farStructures") || Math.hypot(candidate.getMiddleBlockX() - spawn.getX(),
                            candidate.getMiddleBlockZ() - spawn.getZ()) >= 4096)
                    .limit(Math.clamp(Integer.getInteger("neighbors.probe.samples", 12),1,16)).toList());
            boolean managed = placementManaged(state, holder);
            if (Boolean.getBoolean("neighbors.probe.reverseStructures")) Collections.reverse(selectedCandidates);
            for (var candidate : selectedCandidates) {
                var chunk = level.getChunk(candidate.x, candidate.z, ChunkStatus.STRUCTURE_STARTS);
                var start = chunk.getStartForStructure(holder.value());
                var sample = new JsonObject(); sample.addProperty("chunkX", candidate.x); sample.addProperty("chunkZ", candidate.z);
                sample.addProperty("initialChunkStatus", chunk.getPersistedStatus().toString());
                if (managed) {
                    var column = field.sampleColumn(candidate.getMiddleBlockX(), candidate.getMiddleBlockZ(), settings);
                    sample.addProperty("destinationSurfaceY", column.topY());
                }
                boolean valid = start != null && start.isValid(); sample.addProperty("valid", valid);
                sample.addProperty("source", observedStarts.contains(candidate) ? "startup-native-start" : "natural-candidate");
                if (valid && managed && !report.has("physicalClaimIsolation"))
                    report.add("physicalClaimIsolation", physicalClaimIsolation(level, chunk, candidate, id));
                if (managed && design.inherent.skyweavecompat.terrain.structure.StructureDistribution.hasLayoutPlan(
                        design.inherent.skyweavecompat.terrain.structure.StructureFamily.forId(id.toString()))) {
                    var policy = ((design.inherent.skyweavecompat.terrain.structure.StructurePlacementState)state).skyweave$structureDistribution();
                    var owner = policy.owner(candidate.getMiddleBlockX(), candidate.getMiddleBlockZ());
                    var site = new design.inherent.skyweavecompat.terrain.structure.StructureSelection.Site(candidate.x, candidate.z);
                    var fitted = policy.plan(owner);
                    var layout = fitted.layout(design.inherent.skyweavecompat.terrain.structure.StructureFamily.forId(id.toString()), site);
                    var expectedId = layout == null ? null : layout.id();
                    if (id.equals(expectedId) != valid)
                        throw new AssertionError("Structure fit/materialization mismatch at " + candidate + " for " + id);
                    if (valid && !chunk.getPersistedStatus().isOrAfter(ChunkStatus.FULL)) {
                        var expected = layout.pieces();
                        var actual = start.getPieces().stream().map(piece -> {
                            var b = piece.getBoundingBox();
                            return new design.inherent.skyweavecompat.terrain.structure.StructureVolume.Box(b.minX(),b.minY(),b.minZ(),b.maxX(),b.maxY(),b.maxZ());
                        }).toList();
                        if (expected == null || !new HashSet<>(expected).equals(new HashSet<>(actual)))
                            throw new AssertionError("Committed structure geometry differs from its fitted plan at " + candidate);
                        sample.addProperty("fittedPiecesMatch", true);
                    } else if (valid) {
                        sample.addProperty("fittedVariantMatch", true);
                        sample.addProperty("geometryPhase", "native postprocessing complete; compare saved outcome on reopen");
                    }
                }
                if (valid) {
                    accepted++;
                    var bounds = start.getBoundingBox();
                    sample.addProperty("bounds", bounds.toString()); sample.addProperty("pieces", start.getPieces().size());
                    var serialization = net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext.fromLevel(level);
                    sample.addProperty("piecesBeforeRealizationDigest", piecesDigest(start, serialization));
                    if (name.equals("ancient_city")) {
                        var root = start.getPieces().getFirst().getBoundingBox();
                        sample.addProperty("rootBounds",root.toString());
                        var host = field.sampleDominantIslandDescriptor((root.minX()+root.maxX())>>1, (root.minZ()+root.maxZ())>>1, settings);
                        if (host == null) throw new AssertionError("Accepted city core has no host");
                        sample.addProperty("hostInterior", design.inherent.skyweavecompat.terrain.IslandInteriors.kind(
                                host,design.inherent.skyweavecompat.terrain.InteriorConfiguration.profile()).name());
                        sample.add("host",new Gson().toJsonTree(host));
                        var plan = design.inherent.skyweavecompat.terrain.city.StructureBurial.plan(start.getPieces().stream().map(piece -> {
                            var b=piece.getBoundingBox();
                            return new design.inherent.skyweavecompat.terrain.city.StructureBurial.Box(b.minX(),b.minY(),b.minZ(),b.maxX()+1,b.maxY()+1,b.maxZ()+1);
                        }).toList());
                        var body = (design.inherent.skyweavecompat.terrain.InteriorField)(Object)field;
                        var cached = new HashMap<Long,org.sathrek.sky_archipelago.worldgen.generator.field.TerrainColumn>();
                        double burial = plan.buriedFraction(0,(x,y,z)->cached.computeIfAbsent((long)x<<32^(z&0xffffffffL),
                                ignored -> body.skyweave$exteriorOf(host,x,z,settings)).contains(y));
                        sample.addProperty("savedStartBuriedFraction",burial);
                        if (burial + 1e-12 < design.inherent.skyweavecompat.terrain.city.CityConfiguration.policy().minimumBuriedFraction())
                            throw new AssertionError("Committed city no longer meets burial policy");
                    }
                    sample.addProperty("portalRoomPieces", start.getPieces().stream()
                            .filter(piece -> piece.getClass().getSimpleName().equals("PortalRoom")).count());
                    int footprintChunks = ((bounds.maxX() >> 4) - (bounds.minX() >> 4) + 1)
                            * ((bounds.maxZ() >> 4) - (bounds.minZ() >> 4) + 1);
                    sample.addProperty("footprintChunks", footprintChunks);
                    if (!Boolean.getBoolean("neighbors.probe.layoutsOnly") && realized == 0 && footprintChunks <= 400) {
                        var blocks = new TreeMap<String, Integer>();
                        var pos = new BlockPos.MutableBlockPos();
                        // Complete placement before inspecting it: later chunks may finish shared pieces.
                        for (int cx = bounds.minX() >> 4; cx <= bounds.maxX() >> 4; cx++)
                            for (int cz = bounds.minZ() >> 4; cz <= bounds.maxZ() >> 4; cz++) level.getChunk(cx, cz);
                        var placedBounds = holder.value().adjustBoundingBox(
                                net.minecraft.world.level.levelgen.structure.BoundingBox.encapsulatingBoxes(
                                        start.getPieces().stream().map(piece -> piece.getBoundingBox()).toList()).orElseThrow());
                        // Scattered pieces place foundations/traps below their nominal box (pyramid TNT is at local Y=-13).
                        int verticalMargin = start.getPieces().stream().anyMatch(piece -> piece instanceof
                                net.minecraft.world.level.levelgen.structure.ScatteredFeaturePiece) ? 16 : 0;
                        var contentBounds = new net.minecraft.world.level.levelgen.structure.BoundingBox(bounds.minX(),
                                Math.max(level.getMinBuildHeight(), Math.min(bounds.minY(), placedBounds.minY()) - verticalMargin), bounds.minZ(),
                                bounds.maxX(), Math.min(level.getMaxBuildHeight() - 1, Math.max(bounds.maxY(), placedBounds.maxY()) + verticalMargin), bounds.maxZ());
                        sample.addProperty("contentBounds", contentBounds.toString());
                        for (int cx = bounds.minX() >> 4; cx <= bounds.maxX() >> 4; cx++)
                            for (int cz = bounds.minZ() >> 4; cz <= bounds.maxZ() >> 4; cz++) {
                                var full = level.getChunk(cx, cz);
                                for (int x = Math.max(cx * 16, bounds.minX()); x <= Math.min(cx * 16 + 15, bounds.maxX()); x++)
                                    for (int z = Math.max(cz * 16, bounds.minZ()); z <= Math.min(cz * 16 + 15, bounds.maxZ()); z++)
                                        for (int y = contentBounds.minY(); y <= contentBounds.maxY(); y++) {
                                            var block = full.getBlockState(pos.set(x, y, z)).getBlock();
                                            if (block != net.minecraft.world.level.block.Blocks.AIR
                                                    && block != net.minecraft.world.level.block.Blocks.CAVE_AIR)
                                                blocks.merge(BuiltInRegistries.BLOCK.getKey(block).toString(), 1, Integer::sum);
                                        }
                            }
                        sample.add("blocks", new Gson().toJsonTree(blocks));
                        if (!name.startsWith("mineshaft")) {
                            var containers = VillageContentProbe.inspect(level, contentBounds,
                                    !Boolean.getBoolean("neighbors.probe.surfaceStructures"));
                            sample.add("containers", containers);
                            if (name.equals("desert_pyramid")) {
                                long chests = java.util.stream.StreamSupport.stream(containers.spliterator(), false)
                                        .filter(container -> container.getAsJsonObject().get("lootTable").getAsString()
                                                .equals("minecraft:chests/desert_pyramid")).count();
                                sample.addProperty("desertPyramidLootChests", chests);
                                if (chests != 4 || blocks.getOrDefault("minecraft:tnt", 0) < 9
                                        || blocks.getOrDefault("minecraft:stone_pressure_plate", 0) == 0)
                                    {
                                    var heights = new java.util.IntSummaryStatistics();
                                    for (int x=bounds.minX();x<=bounds.maxX();x++) for(int z=bounds.minZ();z<=bounds.maxZ();z++)
                                        heights.accept(level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,x,z));
                                    throw new AssertionError("Desert pyramid content mismatch: chests=" + chests
                                            + " tnt=" + blocks.getOrDefault("minecraft:tnt",0)
                                            + " plate=" + blocks.getOrDefault("minecraft:stone_pressure_plate",0)
                                            + " before=" + bounds + " after=" + placedBounds + " heights=" + heights
                                            + " pieces=" + start.getPieces().stream().map(piece -> piece.createTag(
                                                    net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext.fromLevel(level))).toList());
                                }
                            }
                            if (lootPosition == null) for (var container : containers) {
                                var first = container.getAsJsonObject();
                                // Dispensers and trial vaults retain native loot;
                                // Lootr conversion is only expected for its supported storage.
                                if (!Set.of("minecraft:chest", "minecraft:trapped_chest", "minecraft:barrel")
                                        .contains(first.get("container").getAsString())) continue;
                                lootPosition = new Gson().fromJson(first.get("position"), BlockPos.class);
                                lootTable = first.get("lootTable").getAsString();
                                break;
                            }
                        }
                        if (name.startsWith("mineshaft")) sample.addProperty("chestMinecarts", level.getEntitiesOfClass(
                                net.minecraft.world.entity.vehicle.MinecartChest.class, net.minecraft.world.phys.AABB.of(bounds)).size());
                        if (name.startsWith("village_")) {
                            var skyVillage = registry.getHolder(ResourceLocation.parse("skyvillages:skyvillage"));
                            var nearby = new JsonArray();
                            if (skyVillage.isPresent()) for (var villagePlacement : state.getPlacementsForStructure(skyVillage.get())) {
                                if (!(villagePlacement instanceof design.inherent.skyweavecompat.placement.ArchipelagoVillagePlacement adapted)) continue;
                                var owner = adapted.owner((bounds.minX()+bounds.maxX())>>1,(bounds.minZ()+bounds.maxZ())>>1);
                                if (owner == null) continue;
                                for (var site : adapted.sites(owner)) {
                                    var neighbor = level.getChunk(site.chunkX(),site.chunkZ(),ChunkStatus.STRUCTURE_STARTS)
                                            .getStartForStructure(skyVillage.get().value());
                                    if (neighbor == null || !neighbor.isValid()) continue;
                                    var observed = new JsonObject();
                                    observed.add("owner",new Gson().toJsonTree(owner));
                                    observed.addProperty("chunkX",site.chunkX()); observed.addProperty("chunkZ",site.chunkZ());
                                    observed.addProperty("bounds",neighbor.getBoundingBox().toString());
                                    observed.addProperty("pieces",neighbor.getPieces().size());
                                    nearby.add(observed);
                                }
                            }
                            sample.add("sameArchipelagoSkyVillages",nearby);
                        }
                        long locateBegan = System.nanoTime();
                        var located = generator.findNearestMapStructure(level, net.minecraft.core.HolderSet.direct(holder),
                                candidate.getMiddleBlockPosition(0), 2, false);
                        sample.addProperty("locateMillis", (System.nanoTime() - locateBegan) / 1_000_000.0);
                        sample.addProperty("locateFound", located != null && located.getSecond().equals(holder));
                        if (located != null) sample.addProperty("locatePosition", located.getFirst().toShortString());
                        var placement = state.getPlacementsForStructure(holder).getFirst();
                        sample.addProperty("sourcePresenceBeforeFull", level.structureManager()
                                .checkStructurePresence(candidate, holder.value(), placement, false).name());
                        sample.addProperty("canonicalChunk", start.getChunkPos().toString());
                        var sourceFull = level.getChunk(candidate.x, candidate.z);
                        var sourceStart = sourceFull.getStartForStructure(holder.value());
                        sample.addProperty("sourceStartAfterFull", sourceStart != null && sourceStart.isValid());
                        sample.addProperty("sourcePresenceAfterFull", level.structureManager()
                                .checkStructurePresence(candidate, holder.value(), placement, false).name());
                        var afterFull = generator.findNearestMapStructure(level, net.minecraft.core.HolderSet.direct(holder),
                                candidate.getMiddleBlockPosition(0), 2, false);
                        sample.addProperty("locateAfterSourceFull", afterFull != null);
                        if (afterFull != null) sample.addProperty("locateAfterFullPosition", afterFull.getFirst().toShortString());
                        if (name.equals("stronghold") && blocks.getOrDefault("minecraft:end_portal_frame", 0) < 12)
                            throw new AssertionError("Accepted stronghold has no complete portal frame: " + bounds);
                        if (name.equals("trial_chambers") && blocks.getOrDefault("minecraft:trial_spawner", 0) == 0)
                            throw new AssertionError("Accepted trial chamber has no realized trial spawner: " + bounds);
                        if (name.equals("igloo")) {
                            if (blocks.getOrDefault("minecraft:furnace", 0) == 0 || blocks.getOrDefault("minecraft:crafting_table", 0) == 0)
                                throw new AssertionError("Igloo top did not materialize inside its recorded bounds: " + bounds);
                            if (start.getPieces().size() > 1 && (blocks.getOrDefault("minecraft:ladder", 0) == 0
                                    || blocks.getOrDefault("minecraft:brewing_stand", 0) == 0))
                                throw new AssertionError("Igloo basement or ladder did not materialize inside its recorded bounds: " + bounds);
                        }
                        if (name.equals("ancient_city")) {
                            if (blocks.getOrDefault("minecraft:reinforced_deepslate", 0) == 0)
                                throw new AssertionError("Accepted ancient city has no realized center: " + bounds);
                            var root = start.getPieces().getFirst().getBoundingBox();
                            int feet = 0, roofs = 0;
                            for (int x : new int[]{root.minX(),(root.minX()+root.maxX())>>1,root.maxX()})
                                for (int z : new int[]{root.minZ(),(root.minZ()+root.maxZ())>>1,root.maxZ()}) {
                                    boolean footing = false;
                                    for (int y = root.minY()-12; y <= root.minY(); y++) {
                                        pos.set(x,y,z);
                                        footing |= level.getBlockState(pos).isFaceSturdy(level,pos,net.minecraft.core.Direction.UP);
                                    }
                                    if (footing) feet++;
                                    int run = 0;
                                    for (int y = root.maxY()+1; y < level.getMaxBuildHeight(); y++) {
                                        pos.set(x,y,z);
                                        run = level.getBlockState(pos).isSolidRender(level,pos) ? run+1 : 0;
                                        if (run >= 8) { roofs++; break; }
                                    }
                                }
                            sample.addProperty("completedCoreFootings",feet); sample.addProperty("completedCoreRoofs",roofs);
                            if (feet != 9 || roofs != 9) throw new AssertionError("Completed city core lost floor or roof support: " + feet + "/" + roofs);
                            // Inspect actual room independently of template-block/burial success.
                            // Compare to the pre-structure solid intervals, not a clearance helper.
                            int roomSamples = 0, passable = 0, originallySolid = 0, opened = 0;
                            BlockPos witness = null;
                            for (int x = root.minX() + 2; x <= root.maxX() - 2; x += 2)
                                for (int z = root.minZ() + 2; z <= root.maxZ() - 2; z += 2) {
                                    var original = field.sampleSolidSegments(x, z, settings);
                                    for (int y = root.minY() + 3; y < root.maxY() - 1; y += 2) {
                                        final int height = y;
                                        boolean solid = original.stream().anyMatch(segment -> segment.contains(height));
                                        pos.set(x, y, z); roomSamples++;
                                        boolean free = level.getBlockState(pos).isAir() && level.getBlockState(pos.above()).isAir();
                                        if (solid) originallySolid++;
                                        if (free) { passable++; if (solid) opened++; if (witness == null) witness = pos.immutable(); }
                                    }
                                }
                            var room = new JsonObject();
                            room.addProperty("sampled",roomSamples); room.addProperty("twoHighAir",passable);
                            room.addProperty("preStructureSolid",originallySolid); room.addProperty("openedSolid",opened);
                            if (witness != null) room.add("airWitness",new Gson().toJsonTree(witness));
                            sample.add("completedCoreRoom",room);
                            if (passable < roomSamples * .35 || originallySolid > roomSamples * .5 && opened < originallySolid * .35)
                                throw new AssertionError("City core remains embedded: " + room);
                        }
                        realized++;
                    }
                    // Vanilla pieces persist placement flags for chests and spawners.
                    // Compare the finished state across save/reopen, not a pre-placement
                    // start against a post-placement save. Keep the earlier digest as evidence.
                    sample.addProperty("boundsBeforeRealization", bounds.toString());
                    // StructureStart caches its initial box. Scattered pieces can move during native placement,
                    // so calculate the completed box from the current pieces before comparing a saved reload.
                    var completedBounds = holder.value().adjustBoundingBox(
                            net.minecraft.world.level.levelgen.structure.BoundingBox.encapsulatingBoxes(
                                    start.getPieces().stream().map(piece -> piece.getBoundingBox()).toList()).orElseThrow());
                    sample.addProperty("bounds", completedBounds.toString());
                    sample.addProperty("piecesDigest", piecesDigest(start, serialization));
                }
                samples.add(sample);
                System.out.println("COVERAGE_STRUCTURE " + name + " " + candidate + " valid=" + valid);
                if (accepted >= 2 && !managed) break;
            }
            // Later native pending takeovers must not invalidate a start already counted as accepted.
            for (int index = 0; index < selectedCandidates.size(); index++) {
                var source = selectedCandidates.get(index);
                var finalStart = level.getChunk(source.x, source.z, ChunkStatus.STRUCTURE_STARTS).getStartForStructure(holder.value());
                boolean retained = finalStart != null && finalStart.isValid()
                        && !org.sathrek.sky_archipelago.worldgen.generator.structure.v2.common.placement.PendingStructurePlacement.isRevoked(finalStart);
                if (retained != samples.get(index).getAsJsonObject().get("valid").getAsBoolean())
                    throw new AssertionError("Later native placement changed an observed start at " + source + " for " + id);
            }
            row.add("candidates", samples); row.addProperty("accepted", accepted); row.addProperty("realized", realized); rows.add(row);
            report.add("structures", rows); report.addProperty("complete", false);
            Files.writeString(output, new GsonBuilder().setPrettyPrinting().create().toJson(report));
        }
        report.add("nativePlacement", new Gson().toJsonTree(
                org.sathrek.sky_archipelago.worldgen.benchmark.StructureBenchmarkRecorder.report("skyweave", level.getSeed(), List.of(), Map.of())));
        if (lootPosition != null && net.neoforged.fml.ModList.get().isLoaded("lootr")) {
            var center = new ChunkPos(lootPosition);
            // Lootr waits for a loaded 5x5 neighborhood and subsequent server ticks.
            for (int x = center.x - 2; x <= center.x + 2; x++) for (int z = center.z - 2; z <= center.z + 2; z++) {
                level.setChunkForced(x, z, true);
                level.getChunk(x, z);
            }
            pendingOutput = output;
        }
        report.addProperty("complete", pendingOutput == null);
        Files.writeString(output, new GsonBuilder().setPrettyPrinting().create().toJson(report));
    }

    /** Exercise the real private native guard in an isolated registry, without claiming a world slot. */
    private static JsonObject physicalClaimIsolation(net.minecraft.server.level.ServerLevel level,
            net.minecraft.world.level.chunk.ChunkAccess chunk, ChunkPos source, ResourceLocation id) throws Exception {
        var registryType = Class.forName("org.sathrek.sky_archipelago.worldgen.generator.structure.v2.common.placement.PlacementCommitCoordinator$PhysicalStructureClaimRegistry");
        var keyType = Class.forName("org.sathrek.sky_archipelago.worldgen.generator.structure.v2.common.placement.PlacementCommitCoordinator$PhysicalStructureClaimKey");
        var constructor = registryType.getDeclaredConstructor(); constructor.setAccessible(true);
        var registry = constructor.newInstance();
        var keyConstructor = keyType.getDeclaredConstructor(long.class, ResourceLocation.class, net.minecraft.resources.ResourceKey.class,
                int.class, int.class, int.class, int.class, int.class, int.class, int.class, int.class);
        keyConstructor.setAccessible(true);
        var key = keyConstructor.newInstance(level.getSeed(), id, level.dimension(), source.x, source.z, 0, 0, 0, 1, 1, 1);
        var claim = registryType.getDeclaredMethod("tryClaim", keyType); claim.setAccessible(true);
        var release = registryType.getDeclaredMethod("release", keyType); release.setAccessible(true);
        if (claim.invoke(registry, key) != null || claim.invoke(registry, key) == null)
            throw new AssertionError("Native unscoped physical duplicate control failed");
        var state = level.getChunkSource().getGeneratorState();
        var generator = (SkyIslandChunkGenerator)level.getChunkSource().getGenerator();
        var random = level.getChunkSource().randomState();
        var field = ((GeologicalGenerator)(Object)generator).skyweave$field(random);
        var policy = design.inherent.skyweavecompat.terrain.structure.StructureConfiguration.policy();
        var first = new design.inherent.skyweavecompat.terrain.structure.StructureDistribution(generator, field,
                generator.resolvedSkyIslandSettings(), generator.getBiomeSource(), random, level.getSeed(), state.possibleStructureSets(), policy);
        var second = new design.inherent.skyweavecompat.terrain.structure.StructureDistribution(generator, field,
                generator.resolvedSkyIslandSettings(), generator.getBiomeSource(), random, level.getSeed(), state.possibleStructureSets(), policy);
        java.util.function.Supplier<Boolean> duplicate = () -> {
            try { return claim.invoke(registry, key) != null; }
            catch (ReflectiveOperationException failed) { throw new IllegalStateException(failed); }
        };
        for (var owner : java.util.List.of(first, second)) {
            boolean isolated = design.inherent.skyweavecompat.terrain.structure.StructureWorldScope.run(owner,
                    () -> !duplicate.get() && duplicate.get());
            if (!isolated) throw new AssertionError("World-owned native physical duplicate guard failed");
        }
        var deferred = design.inherent.skyweavecompat.terrain.structure.StructureWorldScope.run(first, () ->
                design.inherent.skyweavecompat.terrain.structure.StructureWorldScope.bind(() -> {
                    try { release.invoke(registry, key); }
                    catch (ReflectiveOperationException failed) { throw new IllegalStateException(failed); }
                }));
        design.inherent.skyweavecompat.terrain.structure.StructureWorldScope.run(second, () -> { deferred.run(); return null; });
        if (!design.inherent.skyweavecompat.terrain.structure.StructureWorldScope.run(second, duplicate)
                || design.inherent.skyweavecompat.terrain.structure.StructureWorldScope.run(first, duplicate)
                || !duplicate.get())
            throw new AssertionError("Deferred release crossed world ownership or erased an unscoped claim");
        var unownedDeferred = design.inherent.skyweavecompat.terrain.structure.StructureWorldScope.run(null, () ->
                design.inherent.skyweavecompat.terrain.structure.StructureWorldScope.bind(() -> {
                    try { release.invoke(registry, key); }
                    catch (ReflectiveOperationException failed) { throw new IllegalStateException(failed); }
                }));
        design.inherent.skyweavecompat.terrain.structure.StructureWorldScope.run(second, () -> { unownedDeferred.run(); return null; });
        if (!design.inherent.skyweavecompat.terrain.structure.StructureWorldScope.run(second, duplicate) || duplicate.get())
            throw new AssertionError("Unowned deferred release borrowed the active world");
        var context = new org.sathrek.sky_archipelago.worldgen.generator.structure.v2.common.placement.ReservationContext(
                level.getSeed(), id, source, level.dimension());
        var bounds = new net.minecraft.world.level.levelgen.structure.BoundingBox(10000000, 100, 10000000, 10000008, 108, 10000008);
        java.util.function.Supplier<Boolean> reserve = () ->
                org.sathrek.sky_archipelago.worldgen.generator.structure.v2.common.placement.StructurePlacementReservationRegistry
                    .tryReserve(bounds, org.sathrek.sky_archipelago.worldgen.structure.StructurePlacementCategory.DEFAULT, context).accepted();
        for (var owner : java.util.List.of(first, second)) {
            if (!design.inherent.skyweavecompat.terrain.structure.StructureWorldScope.run(owner, () -> reserve.get() && !reserve.get()))
                throw new AssertionError("Native reservation store crossed world ownership or lost duplicate rejection");
        }
        var owner = first.owner(source.getMiddleBlockX(), source.getMiddleBlockZ());
        for (var world : java.util.List.of(first, second)) world.bind(level.registryAccess(), level.getServer().getStructureManager(),
                net.minecraft.world.level.LevelHeightAccessor.create(level.getMinBuildHeight(), level.getHeight()), state);
        var planned = java.util.concurrent.CompletableFuture.supplyAsync(() -> first.plan(owner));
        var independent = java.util.concurrent.CompletableFuture.supplyAsync(() -> second.plan(owner));
        var expected = ((design.inherent.skyweavecompat.terrain.structure.StructurePlacementState)state)
                .skyweave$structureDistribution().plan(owner);
        if (!expected.equals(planned.join()) || !expected.equals(independent.join()))
            throw new AssertionError("Concurrent independent-world fitted plans differ");
        var cacheField = first.getClass().getDeclaredField("fittedPlans"); cacheField.setAccessible(true);
        ((com.google.common.cache.Cache<?, ?>)cacheField.get(first)).invalidateAll();
        if (!expected.equals(first.plan(owner))) throw new AssertionError("Eviction changed fitted ownership or geometry");
        var result = new JsonObject();
        result.addProperty("concurrentWorldPlansMatch", true);
        result.addProperty("evictionPlanMatches", true);
        result.addProperty("unscopedDuplicateRejected", true);
        result.addProperty("worldsIndependent", true);
        result.addProperty("deferredReleaseOwned", true);
        result.addProperty("nativeReservationsIndependent", true);
        result.addProperty("foreignClaimPreserved", true);
        result.addProperty("unownedDeferredReleaseIsolated", true);
        return result;
    }

    private static boolean placementManaged(net.minecraft.world.level.chunk.ChunkGeneratorStructureState state,
            net.minecraft.core.Holder<net.minecraft.world.level.levelgen.structure.Structure> holder) {
        return state.getPlacementsForStructure(holder).stream().anyMatch(placement ->
                placement instanceof design.inherent.skyweavecompat.terrain.structure.ArchipelagoStructurePlacement);
    }

    private static String piecesDigest(net.minecraft.world.level.levelgen.structure.StructureStart start,
                                      net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext context)
            throws java.security.NoSuchAlgorithmException {
        String pieces = start.getPieces().stream().map(piece -> canonical(piece.createTag(context)))
                .collect(java.util.stream.Collectors.joining("\n"));
        return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                .digest(pieces.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    }

    private static String canonical(net.minecraft.nbt.Tag tag) {
        if (tag instanceof net.minecraft.nbt.CompoundTag compound)
            return compound.getAllKeys().stream().sorted().map(key -> key + ":" + canonical(compound.get(key)))
                    .collect(java.util.stream.Collectors.joining(",","{","}"));
        if (tag instanceof net.minecraft.nbt.ListTag list)
            return list.stream().map(StructureCoverageProbe::canonical).collect(java.util.stream.Collectors.joining(",","[","]"));
        return tag.toString();
    }

    static void finish(MinecraftServer server) throws Exception {
        if (pendingOutput == null) return;
        var level = server.overworld();
        var entity = level.getBlockEntity(lootPosition);
        var report = JsonParser.parseString(Files.readString(pendingOutput)).getAsJsonObject();
        var conversion = new JsonObject();
        String block = BuiltInRegistries.BLOCK.getKey(level.getBlockState(lootPosition).getBlock()).toString();
        conversion.addProperty("block", block);
        conversion.addProperty("originalLootTable", lootTable);
        boolean converted = block.startsWith("lootr:");
        conversion.addProperty("converted", converted);
        if (converted) {
            var key = (net.minecraft.resources.ResourceKey<?>)entity.getClass().getMethod("getInfoLootTable").invoke(entity);
            conversion.addProperty("convertedLootTable", key.location().toString());
            if (!key.location().toString().equals(lootTable)) throw new AssertionError("Lootr changed loot table");
        }
        report.add("lootrConversion", conversion);
        report.addProperty("complete", converted);
        Files.writeString(pendingOutput, new GsonBuilder().setPrettyPrinting().create().toJson(report));
        pendingOutput = null;
        if (!converted) throw new AssertionError("Generated container did not convert after loaded-neighborhood ticks");
    }

}
