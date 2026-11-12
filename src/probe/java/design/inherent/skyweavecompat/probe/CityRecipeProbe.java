package design.inherent.skyweavecompat.probe;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.FixedBiomeSource;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;

/** Native city assembly without consulting an island field, committing a start or placing blocks. */
final class CityRecipeProbe {
    private CityRecipeProbe() {}
    private static final class NoTerrain extends NoiseBasedChunkGenerator {
        int queries;
        NoTerrain(FixedBiomeSource source,Holder<NoiseGeneratorSettings> settings) {super(source,settings);}
        @Override public int getBaseHeight(int x,int z,Heightmap.Types type,LevelHeightAccessor level,RandomState random) {
            queries++;throw new AssertionError("City assembly requested terrain height");
        }
        @Override public NoiseColumn getBaseColumn(int x,int z,LevelHeightAccessor level,RandomState random) {
            queries++;throw new AssertionError("City assembly requested terrain column");
        }
    }
    static void run(MinecraftServer server,Path output) throws Exception {
        var level=server.overworld();var registries=level.registryAccess();
        var biome=registries.registryOrThrow(Registries.BIOME).getHolderOrThrow(Biomes.DEEP_DARK);
        var source=new FixedBiomeSource(biome);
        var generator=new NoTerrain(source,registries.registryOrThrow(Registries.NOISE_SETTINGS)
                .getHolderOrThrow(net.minecraft.resources.ResourceKey.create(Registries.NOISE_SETTINGS,
                        net.minecraft.resources.ResourceLocation.parse("skyweave_compat:islands_v1"))));
        var city=registries.registryOrThrow(Registries.STRUCTURE).get(net.minecraft.resources.ResourceLocation.withDefaultNamespace("ancient_city"));
        var serializer=StructurePieceSerializationContext.fromLevel(level);
        var report=new JsonObject();report.addProperty("complete",false);var rows=new JsonArray();report.add("assemblies",rows);
        int loaded=level.getChunkSource().getLoadedChunksCount();
        try {
            for(var chunk:new ChunkPos[]{new ChunkPos(0,0),new ChunkPos(17,-31),new ChunkPos(-48,8),new ChunkPos(101,151)}) {
                var start=city.generate(registries,generator,source,level.getChunkSource().randomState(),server.getStructureManager(),
                        level.getSeed(),chunk,0,level,city.biomes()::contains);
                if(!start.isValid() || start.getPieces().isEmpty())throw new AssertionError("Fixed-biome native city assembly failed");
                if(start.getPieces().size()>2048 || start.getPieces().stream().anyMatch(piece->!(piece instanceof PoolElementStructurePiece pool)
                        || pool.getElement().getProjection()!=StructureTemplatePool.Projection.RIGID))throw new AssertionError("City exceeds qualified rigid assembly boundary");
                var tag=start.createTag(serializer,chunk);
                var restored=StructureStart.loadStaticStart(serializer,tag.copy(),level.getSeed());
                if(restored==null || !restored.isValid() || !restored.createTag(serializer,chunk).equals(tag))
                    throw new AssertionError("Native city pieces did not round-trip exactly");
                var row=new JsonObject();rows.add(row);row.addProperty("chunkX",chunk.x);row.addProperty("chunkZ",chunk.z);
                row.addProperty("pieces",start.getPieces().size());row.addProperty("bounds",start.getBoundingBox().toString());
                row.addProperty("nativeRoundTrip",true);
            }
            if(generator.queries!=0 || loaded!=level.getChunkSource().getLoadedChunksCount())throw new AssertionError("City assembly touched terrain/chunks");
            report.addProperty("terrainQueries",generator.queries);report.addProperty("loadedChunksUnchanged",true);
            report.addProperty("scope","Native all-rigid assembly and serialization only; no island fit, city reservation, cavern or realized city guarantee");
            report.addProperty("complete",true);
        } finally {Files.writeString(output,new GsonBuilder().setPrettyPrinting().create().toJson(report));}
    }
}
