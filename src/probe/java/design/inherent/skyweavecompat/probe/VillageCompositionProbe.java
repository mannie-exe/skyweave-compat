package design.inherent.skyweavecompat.probe;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import design.inherent.skyweavecompat.mixin.VillagePaletteAccess;
import design.inherent.skyweavecompat.mixin.VillageTemplateAccess;
import design.inherent.skyweavecompat.terrain.structure.VillageDelegate;
import design.inherent.skyweavecompat.terrain.structure.VillageComposition;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashSet;
import java.util.TreeMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.TagParser;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.FixedBiomeSource;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.pools.SinglePoolElement;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.storage.LevelResource;

/** Native grammar and save controls on a constant surface; no island fit or physical placement. */
final class VillageCompositionProbe {
    private VillageCompositionProbe() {}

    private static final class Surface extends NoiseBasedChunkGenerator {
        Surface(FixedBiomeSource biomes, Holder<NoiseGeneratorSettings> settings) { super(biomes, settings); }
        @Override public int getBaseHeight(int x, int z, Heightmap.Types type, LevelHeightAccessor level, RandomState random) { return 72; }
        @Override public NoiseColumn getBaseColumn(int x, int z, LevelHeightAccessor level, RandomState random) {
            var blocks = new BlockState[level.getHeight()];
            Arrays.fill(blocks, Blocks.AIR.defaultBlockState());
            Arrays.fill(blocks, 0, Math.max(0, Math.min(blocks.length, 72 - level.getMinBuildHeight())), Blocks.STONE.defaultBlockState());
            return new NoiseColumn(level.getMinBuildHeight(), blocks);
        }
    }

    private static String digest(CompoundTag tag) throws Exception {
        // Pinned native StringTagVisitor sorts compound keys; array/list order remains significant.
        return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                .digest(tag.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    }

    private static CompoundTag loadedContract(StructureStart start, CompoundTag tag, MinecraftServer server) {
        var expected = tag.copy(); var children = expected.getList("Children", 10);
        if (children.size() != start.getPieces().size()) throw new AssertionError("Serialized piece count differs");
        for (int i = 0; i < children.size(); i++) {
            if (!(start.getPieces().get(i) instanceof PoolElementStructurePiece piece)) continue;
            var before = piece.getBoundingBox();
            var physical = piece.getElement().getBoundingBox(server.getStructureManager(), piece.getPosition(), piece.getRotation());
            // Native JigsawPlacement extends maxY for assembly clearance. The native load
            // constructor recomputes template bounds. No other state is normalized here.
            if (before.minX() != physical.minX() || before.maxX() != physical.maxX()
                    || before.minZ() != physical.minZ() || before.maxZ() != physical.maxZ()
                    || before.minY() != physical.minY() || before.maxY() < physical.maxY())
                throw new AssertionError("Unsupported native reservation-bounds normalization");
            children.getCompound(i).putIntArray("BB", new int[]{physical.minX(), physical.minY(), physical.minZ(), physical.maxX(), physical.maxY(), physical.maxZ()});
        }
        return expected;
    }

    static void run(MinecraftServer server, Path output) throws Exception {
        var level = server.overworld();
        var access = level.registryAccess();
        var registry = access.registryOrThrow(Registries.STRUCTURE);
        var biomes = new FixedBiomeSource(access.registryOrThrow(Registries.BIOME).getHolderOrThrow(Biomes.PLAINS));
        var generator = new Surface(biomes, access.registryOrThrow(Registries.NOISE_SETTINGS).getHolderOrThrow(NoiseGeneratorSettings.OVERWORLD));
        var serializer = StructurePieceSerializationContext.fromLevel(level);
        var ids = System.getProperty("neighbors.probe.compositionIds", "").split(",");
        String seedSignature=System.getProperty("neighbors.probe.compositionSeeds","1,42,8675309,424242");
        long[] seeds=Arrays.stream(seedSignature.split(",")).mapToLong(Long::parseLong).toArray();
        int samples = Integer.getInteger("neighbors.probe.samples", 2);
        if (ids.length < 1 || ids.length > 16 || samples < 1 || samples > 24 || seeds.length<1 || seeds.length>8
                || Arrays.stream(seeds).distinct().count()!=seeds.length || ids.length*samples*seeds.length>128)
            throw new IllegalArgumentException("Invalid composition fixture bounds");
        var receipt = server.getWorldPath(LevelResource.ROOT).resolve("village-composition-starts.json");
        var report = new JsonObject();
        report.addProperty("complete", false);
        report.add("compositionSeeds",new com.google.gson.Gson().toJsonTree(seeds));
        report.addProperty("scope", "Native assembly over a constant surface, raw single-palette occupied projections and serialized starts. No island fit, processed footing, physical blocks, navigation or usable-island area denominator.");
        int loaded = level.getChunkSource().getLoadedChunksCount();
        try {
            if (Files.exists(receipt)) {
                if (Files.size(receipt) > 16 * 1024 * 1024) throw new AssertionError("Oversized composition receipt");
                var saved = JsonParser.parseString(Files.readString(receipt)).getAsJsonObject();
                if (saved.get("schema").getAsInt() != 3 || !saved.get("ids").getAsString().equals(String.join(",", ids))
                        || !saved.get("seeds").getAsString().equals(seedSignature)
                        || saved.get("samples").getAsInt() != samples) throw new AssertionError("Mismatched composition receipt");
                int restored = 0;
                for (var value : saved.getAsJsonArray("starts")) {
                    var row = value.getAsJsonObject();
                    var tag = TagParser.parseTag(row.get("tag").getAsString());
                    var start = StructureStart.loadStaticStart(serializer, tag.copy(), row.get("seed").getAsLong());
                    var chunk = new ChunkPos(row.get("chunkX").getAsInt(), row.get("chunkZ").getAsInt());
                    if (start == null || !start.isValid() || !digest(start.createTag(serializer, chunk)).equals(row.get("loadedContractDigest").getAsString()))
                        throw new AssertionError("Saved composition start changed");
                    restored++;
                }
                report = saved.getAsJsonObject("report").deepCopy();
                report.addProperty("reopenedStarts", restored);
                report.addProperty("reopened", true);
            } else {
                var rows = new JsonArray(); var starts = new JsonArray(); var catalog = new TreeMap<String, JsonObject>();
                report.add("assemblies", rows);
                report.add("effectiveCatalog",VillageSupportSurvey.run(server));
                var schedule=new JsonArray();report.add("nativeSchedule",schedule);
                var scheduled=access.registryOrThrow(Registries.STRUCTURE_SET).get(ResourceLocation.parse("minecraft:villages"));
                if(scheduled!=null)for(var choice:scheduled.structures()) {
                    var item=new JsonObject();item.addProperty("structure",choice.structure().unwrapKey().orElseThrow().location().toString());
                    item.addProperty("weight",choice.weight());var eligibility=new JsonArray();
                    choice.structure().value().biomes().stream().map(b->b.unwrapKey().orElseThrow().location().toString()).sorted().forEach(eligibility::add);
                    item.add("eligibleBiomes",eligibility);schedule.add(item);
                }
                var definitions = new JsonArray();report.add("definitions",definitions);
                for (var name : ids) {
                    var id = ResourceLocation.parse(name);
                    var structure = registry.get(id);
                    if (structure == null) throw new AssertionError("Missing authored composition structure: " + id);
                    var holder = registry.getHolderOrThrow(net.minecraft.resources.ResourceKey.create(Registries.STRUCTURE, id));
                    if (design.inherent.skyweavecompat.terrain.structure.StructureMembership.family(holder)
                            != design.inherent.skyweavecompat.terrain.structure.StructureFamily.VILLAGES
                            || !holder.is(net.minecraft.tags.StructureTags.VILLAGE))
                        throw new AssertionError("Composition definition lost managed or standard village discovery: " + id);
                    var definition=new JsonObject();definition.addProperty("structure",name);
                    var eligible=new JsonArray();structure.biomes().stream().map(b->b.unwrapKey().orElseThrow().location().toString())
                            .sorted().forEach(eligible::add);definition.add("nativeEligibleBiomes",eligible);
                    if(name.startsWith("skyweave_compat:villages/") && eligible.isEmpty())
                        throw new AssertionError("Authored profile has no native eligible biomes: "+name);
                    definition.add("nativeDefinition",net.minecraft.world.level.levelgen.structure.Structure.DIRECT_CODEC.encodeStart(
                            net.minecraft.resources.RegistryOps.create(com.mojang.serialization.JsonOps.INSTANCE,access),structure).getOrThrow());
                    definitions.add(definition);
                    int accepted = 0, qualified = 0;
                    var contentRefusals = new TreeMap<String, Integer>();
                    for (long seed : seeds) for (int sample = 0; sample < samples; sample++) {
                        var chunk = new ChunkPos(sample % 2 == 0 ? -17 : 31, sample * 7 - 9);
                        long began = System.nanoTime();
                        var start = structure.generate(access, generator, biomes, level.getChunkSource().randomState(), server.getStructureManager(), seed, chunk, 0, level, biome -> true);
                        var row = new JsonObject(); rows.add(row);
                        row.addProperty("structure", name); row.addProperty("seed", seed); row.addProperty("sample", sample);
                        row.addProperty("chunkX",chunk.x);row.addProperty("chunkZ",chunk.z);
                        row.addProperty("assemblyMillis", (System.nanoTime() - began) / 1_000_000.0);
                        row.addProperty("valid", start.isValid());
                        if (!start.isValid()) continue;
                        accepted++;
                        if (start.getPieces().size() > 2048) throw new AssertionError("Composition piece budget exceeded");
                        var composition = VillageComposition.assess(id, start.getPieces());
                        row.add("requiredContent", new com.google.gson.Gson().toJsonTree(composition));
                        boolean required = holder.is(net.minecraft.tags.TagKey.create(Registries.STRUCTURE,
                                ResourceLocation.parse("skyweave_compat:managed/composed_villages")));
                        if (required && design.inherent.skyweavecompat.OptionalIntegrations.enabled(design.inherent.skyweavecompat.Feature.GROUND_SUPPORT)) {
                            if (composition.qualified()) qualified++;
                            else {
                                if (composition.missing().isEmpty()) throw new AssertionError("Unexplained content refusal: " + id + " " + composition);
                                contentRefusals.merge(composition.status().name(), 1, Integer::sum);
                            }
                        }
                        if (composition.qualified()) {
                            var withoutRoot = new java.util.ArrayList<>(start.getPieces()); withoutRoot.removeFirst();
                            var missingRoot = VillageComposition.assess(id, withoutRoot);
                            if (missingRoot.status() != VillageComposition.Status.MISSING_REQUIRED
                                    && missingRoot.status() != VillageComposition.Status.UNSUPPORTED_ELEMENT
                                    || !missingRoot.missing().containsKey("authored_court"))
                                throw new AssertionError("Removed actual root retained content credit: " + id + " " + missingRoot);
                            var duplicateRoot = new java.util.ArrayList<>(start.getPieces()); duplicateRoot.add(start.getPieces().getFirst());
                            if (!VillageComposition.assess(id, duplicateRoot).equals(new VillageComposition.Result(
                                    composition.status(), composition.counts(), composition.missing(), duplicateRoot.size())))
                                throw new AssertionError("Duplicate physical piece received double role credit: " + id);
                            row.add("removedRootControl", new com.google.gson.Gson().toJsonTree(missingRoot));
                            row.addProperty("duplicateRootCountUnchanged", true);
                        }
                        var occupied = new HashSet<Long>(); var types = new TreeMap<String, Integer>();
                        var occurrences = new TreeMap<String,Integer>();
                        int beds = 0, unsupported = 0, junctions = 0, visits = 0;
                        for (var piece : start.getPieces()) {
                            if (!(piece instanceof PoolElementStructurePiece pool)) { unsupported++; continue; }
                            junctions += pool.getJunctions().size();
                            var element = pool.getElement();
                            for (int depth = 0; element instanceof VillageDelegate delegate && depth < 8; depth++) element = delegate.skyweave$delegate();
                            types.merge(element.getClass().getName(), 1, Integer::sum);
                            if (!(element instanceof SinglePoolElement single)) { unsupported++; continue; }
                            var nativeAccess = (VillageTemplateAccess) single;
                            var template = nativeAccess.skyweave$template(server.getStructureManager());
                            var palettes = ((VillagePaletteAccess) template).skyweave$palettes();
                            if (palettes.size() != 1) { unsupported++; continue; }
                            String key = nativeAccess.skyweave$template().left().map(Object::toString).orElse("inline:" + element);
                            occurrences.merge(key,1,Integer::sum);
                            var metadata = new JsonObject(); metadata.addProperty("template", key);
                            metadata.addProperty("projection", single.getProjection().name());
                            metadata.addProperty("groundLevelDelta", single.getGroundLevelDelta());
                            metadata.addProperty("sizeX", template.getSize().getX()); metadata.addProperty("sizeY", template.getSize().getY()); metadata.addProperty("sizeZ", template.getSize().getZ());
                            catalog.putIfAbsent(key + "|" + single.getProjection(), metadata);
                            for (var block : palettes.getFirst().blocks()) {
                                if (++visits > 4_000_000) throw new AssertionError("Composition raw block budget exceeded");
                                if (block.state().isAir() || block.state().is(Blocks.JIGSAW) || block.state().is(Blocks.STRUCTURE_VOID)) continue;
                                var position = StructureTemplate.transform(block.pos(), Mirror.NONE, pool.getRotation(), BlockPos.ZERO).offset(pool.getPosition());
                                occupied.add(ChunkPos.asLong(position.getX(), position.getZ()));
                                if (block.state().getBlock() instanceof BedBlock && block.state().getValue(BedBlock.PART) == BedPart.HEAD) beds++;
                            }
                        }
                        var tag = start.createTag(serializer, chunk);
                        var expected = loadedContract(start, tag, server);
                        var comparable=expected.copy();comparable.remove("id");
                        row.addProperty("loadedLayoutDigest",digest(comparable));
                        var restored = StructureStart.loadStaticStart(serializer, tag.copy(), seed);
                        if (restored == null || !restored.createTag(serializer, chunk).equals(expected)) {
                            report.addProperty("codecStructure", name);
                            report.add("codecBefore", net.minecraft.nbt.NbtOps.INSTANCE.convertTo(com.mojang.serialization.JsonOps.INSTANCE, tag));
                            if (restored != null) report.add("codecAfter", net.minecraft.nbt.NbtOps.INSTANCE.convertTo(com.mojang.serialization.JsonOps.INSTANCE, restored.createTag(serializer, chunk)));
                            throw new AssertionError("Composition codec round trip changed");
                        }
                        row.addProperty("pieces", start.getPieces().size()); row.addProperty("rawOccupiedColumns", occupied.size());
                        if(start.getPieces().getFirst() instanceof PoolElementStructurePiece root)row.addProperty("rootRotation",root.getRotation().name());
                        row.addProperty("rawBedHeads", beds); row.addProperty("unsupportedMetricPieces", unsupported);
                        row.add("templateCounts",new com.google.gson.Gson().toJsonTree(occurrences));
                        row.addProperty("nativeJunctionOccurrences", junctions); row.addProperty("inflatedBounds", start.getBoundingBox().toString());
                        var bounds=start.getBoundingBox();
                        int referenceRadius=Math.max(Math.max(Math.abs((bounds.minX()>>4)-chunk.x),Math.abs((bounds.maxX()>>4)-chunk.x)),
                                Math.max(Math.abs((bounds.minZ()>>4)-chunk.z),Math.abs((bounds.maxZ()>>4)-chunk.z)));
                        row.addProperty("referenceChunkRadius",referenceRadius);
                        if(name.startsWith("skyweave_compat:villages/") && referenceRadius>8)
                            throw new AssertionError("Authored village exceeds the native eight-chunk reference horizon");
                        row.add("elementTypes", new com.google.gson.Gson().toJsonTree(types));
                        var savedStart = new JsonObject(); starts.add(savedStart); savedStart.addProperty("seed", seed);
                        savedStart.addProperty("chunkX", chunk.x); savedStart.addProperty("chunkZ", chunk.z); savedStart.addProperty("tag", tag.toString());
                        savedStart.addProperty("loadedContractDigest", digest(expected));
                    }
                    definition.addProperty("nativeAssemblies", accepted);
                    definition.addProperty("qualifiedAssemblies", qualified);
                    definition.add("contentRefusals", new com.google.gson.Gson().toJsonTree(contentRefusals));
                    if (accepted == 0) throw new AssertionError("No native assembly for " + id);
                    boolean required = holder.is(net.minecraft.tags.TagKey.create(Registries.STRUCTURE,
                            ResourceLocation.parse("skyweave_compat:managed/composed_villages")));
                    if (required && design.inherent.skyweavecompat.OptionalIntegrations.enabled(design.inherent.skyweavecompat.Feature.GROUND_SUPPORT) && qualified == 0)
                        throw new AssertionError("No qualified assembly for authored profile " + id);
                }
                var templates = new JsonArray(); catalog.values().forEach(templates::add); report.add("templateCatalog", templates);
                report.addProperty("reopened", false);
                var saved = new JsonObject(); saved.addProperty("schema", 3); saved.addProperty("ids", String.join(",", ids)); saved.addProperty("samples", samples);
                saved.addProperty("seeds",seedSignature);
                saved.add("starts", starts); saved.add("report", report.deepCopy());
                String text = new GsonBuilder().create().toJson(saved);
                if (text.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 16 * 1024 * 1024)
                    throw new AssertionError("Composition receipt budget exhausted before publication");
                Files.writeString(receipt, text);
            }
            if (loaded != level.getChunkSource().getLoadedChunksCount()) throw new AssertionError("Composition inspection loaded chunks");
            report.addProperty("loadedChunksUnchanged", true); report.addProperty("complete", true);
        } finally { Files.writeString(output, new GsonBuilder().setPrettyPrinting().create().toJson(report)); }
    }
}
