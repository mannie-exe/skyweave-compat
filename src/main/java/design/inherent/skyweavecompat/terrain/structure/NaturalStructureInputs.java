package design.inherent.skyweavecompat.terrain.structure;

import design.inherent.skyweavecompat.SkyweaveCompat;
import design.inherent.skyweavecompat.terrain.GenerationGuard;
import design.inherent.skyweavecompat.terrain.GeologicalGenerator;
import java.util.*;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.*;
import net.neoforged.neoforge.registries.*;

/** Immutable CARVERS inputs. Absence or a mismatched binding never falls back to live feature output. */
public final class NaturalStructureInputs {
    private static final int SCHEMA=2;
    private static final com.mojang.serialization.Codec<PalettedContainer<BlockState>> SECTION =
            PalettedContainer.codecRW(Block.BLOCK_STATE_REGISTRY,BlockState.CODEC,PalettedContainer.Strategy.SECTION_STATES,Blocks.AIR.defaultBlockState());
    private static final DeferredRegister<AttachmentType<?>> TYPES=DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES,SkyweaveCompat.ID);
    public static final Supplier<AttachmentType<Snapshot>> TYPE=TYPES.register("natural_structure_inputs",()->
            AttachmentType.<Snapshot>builder(()->{throw new IllegalStateException("Natural terrain was not captured at CARVERS");})
                    .serialize(new Serializer()).build());
    private NaturalStructureInputs() {}
    public static void register(IEventBus bus) { TYPES.register(bus); }

    public static final class Snapshot {
        private final long seed;
        private final String identity;
        private final ResourceLocation dimension;
        private final ChunkPos position;
        private final StructureTerrainView.ColumnChunk terrain;
        private final Journal grading;
        private Snapshot(long seed,String identity,ResourceLocation dimension,ChunkPos position,StructureTerrainView.ColumnChunk terrain) {
            this(seed,identity,dimension,position,terrain,null);
        }
        private Snapshot(long seed,String identity,ResourceLocation dimension,ChunkPos position,StructureTerrainView.ColumnChunk terrain,Journal grading) {
            if(!identity.matches("[0-9a-f]{64}"))throw new IllegalArgumentException("Invalid retained generation identity");
            this.seed=seed;this.identity=identity;this.dimension=Objects.requireNonNull(dimension);
            this.position=Objects.requireNonNull(position);this.terrain=Objects.requireNonNull(terrain);
            this.grading=grading;
            if(grading!=null)for(var cell:grading.cells())if(!new ChunkPos(cell.position()).equals(position)
                    || !terrain.state(cell.position().getX(),cell.position().getY(),cell.position().getZ()).equals(cell.original()))
                throw new IllegalArgumentException("Retained grading differs from its natural input");
        }
        public BlockState state(BlockPos at) {
            if(!new ChunkPos(at).equals(position))throw new StructureTerrainView.Unavailable("Natural input belongs to another chunk");
            return terrain.state(at.getX(),at.getY(),at.getZ());
        }
        /** Intended patch only; this is not a final support/acceptance receipt. */
        public Journal grading() { return grading; }
        private boolean matches(ServerLevel level,ChunkAccess chunk) {
            return seed==level.getSeed() && identity.equals(GenerationGuard.fingerprint(level.getServer()))
                    && dimension.equals(level.dimension().location()) && position.equals(chunk.getPos())
                    && terrain.minY==chunk.getMinBuildHeight() && terrain.height==chunk.getHeight();
        }
    }
    public record Journal(String layout,List<VillageGrading.Cell> cells) {
        public Journal {
            cells=List.copyOf(cells);
            if(!layout.matches("[0-9a-f]{64}") || cells.size()>VillageGrading.MAX_CELLS
                    || cells.stream().map(VillageGrading.Cell::position).distinct().count()!=cells.size())
                throw new IllegalArgumentException("Invalid retained grading intent");
        }
    }
    /** Persist the complete intended own patch before its first block write. Changed replay is refused. */
    public static void bindGrading(ServerLevel level,ChunkAccess chunk,String layout,List<VillageGrading.Cell> cells) {
        var snapshot=existing(level,chunk);if(snapshot==null)throw new StructureTerrainView.Unavailable("Natural input missing before grading");
        var journal=new Journal(layout,cells);
        if(snapshot.grading!=null) {
            if(!snapshot.grading.equals(journal))throw new IllegalStateException("Ground village intent differs from its retained patch");
            return;
        }
        chunk.setData(TYPE,new Snapshot(snapshot.seed,snapshot.identity,snapshot.dimension,snapshot.position,snapshot.terrain,journal));
        chunk.setUnsaved(true);
    }
    public static boolean owned(ServerLevel level) {
        return design.inherent.skyweavecompat.OptionalIntegrations.enabled(design.inherent.skyweavecompat.Feature.GROUND_SUPPORT)
                && level.getChunkSource().getGenerator() instanceof GeologicalGenerator generator && generator.skyweave$isEnabled()
                && level.getServer().getWorldData().worldGenOptions().generateStructures();
    }
    /** Called after native carving returns, even when an optional carver policy cancelled its body. */
    public static void capture(ServerLevel level,ChunkAccess chunk) {
        if(!owned(level))return;
        if(chunk.hasData(TYPE)) {
            if(!chunk.getData(TYPE).matches(level,chunk))throw new IllegalStateException("Mismatched retained natural terrain");
            return;
        }
        // A late caller cannot certify already decorated terrain as the natural source.
        if(!chunk.getPersistedStatus().isBefore(ChunkStatus.CARVERS))
            throw new IllegalStateException("Natural terrain capture must precede CARVERS completion");
        var data=new Snapshot(level.getSeed(),GenerationGuard.fingerprint(level.getServer()),level.dimension().location(),
                chunk.getPos(),new StructureTerrainView.ColumnChunk(chunk));
        chunk.setData(TYPE,data);chunk.setUnsaved(true);
    }
    public static Snapshot existing(ServerLevel level,ChunkAccess chunk) {
        if(!owned(level) || !chunk.hasData(TYPE))return null;
        var snapshot=chunk.getData(TYPE);return snapshot.matches(level,chunk)?snapshot:null;
    }
    /** Both native LIGHT pyramids close radius-one FEATURES readers before this task starts. */
    public static void retire(ServerLevel level,ChunkAccess chunk) {
        if(!owned(level) || !chunk.hasData(TYPE))return;
        if(chunk.getPersistedStatus().isBefore(ChunkStatus.INITIALIZE_LIGHT))
            throw new IllegalStateException("Natural support source retired before the native FEATURES barrier");
        if(!chunk.getData(TYPE).matches(level,chunk))throw new IllegalStateException("Mismatched natural source at retirement");
        chunk.removeData(TYPE);chunk.setUnsaved(true);
    }
    /** Caller supplies already available chunks; this method does not request any status. */
    public static StructureTerrainView view(ServerLevel level,Collection<? extends ChunkAccess> chunks) {
        if(chunks.isEmpty() || chunks.size()>StructureTerrainView.MAX_CHUNKS)
            throw new StructureTerrainView.Unavailable("Retained terrain chunk budget exceeded");
        var inputs=new HashMap<ChunkPos,StructureTerrainView.ColumnChunk>();
        for(var chunk:chunks) {
            var snapshot=existing(level,chunk);
            if(snapshot==null)throw new StructureTerrainView.Unavailable("Natural input unavailable for "+chunk.getPos());
            if(inputs.putIfAbsent(chunk.getPos(),snapshot.terrain)!=null)
                throw new StructureTerrainView.Unavailable("Duplicate retained terrain chunk");
        }
        return StructureTerrainView.retained(level.getSeed(),level.registryAccess(),inputs);
    }

    private static final class Serializer implements IAttachmentSerializer<CompoundTag,Snapshot> {
        @Override public Snapshot read(IAttachmentHolder holder,CompoundTag tag,HolderLookup.Provider provider) {
            if(!tag.getAllKeys().equals(Set.of("schema","seed","identity","dimension","x","z","minY","height","sections","grading")))
                throw new IllegalArgumentException("Unexpected retained natural terrain fields");
            for(var key:List.of("schema","x","z","minY","height"))if(!tag.contains(key,Tag.TAG_INT))
                throw new IllegalArgumentException("Invalid retained integer: "+key);
            if(!tag.contains("seed",Tag.TAG_LONG) || !tag.contains("identity",Tag.TAG_STRING) || !tag.contains("dimension",Tag.TAG_STRING))
                throw new IllegalArgumentException("Invalid retained natural terrain identity");
            if(!(holder instanceof ChunkAccess chunk) || tag.getInt("schema")!=SCHEMA
                    || tag.getInt("x")!=chunk.getPos().x || tag.getInt("z")!=chunk.getPos().z
                    || tag.getInt("minY")!=chunk.getMinBuildHeight() || tag.getInt("height")!=chunk.getHeight())
                throw new IllegalArgumentException("Invalid retained natural terrain header");
            int count=chunk.getHeight()/16;
            if(count<1 || count>StructureTerrainView.MAX_SECTIONS || !tag.contains("sections",Tag.TAG_LIST))
                throw new IllegalArgumentException("Invalid retained section count");
            var list=tag.getList("sections",Tag.TAG_COMPOUND);
            if(list.size()!=count)throw new IllegalArgumentException("Incomplete retained natural terrain");
            var sections=new ArrayList<PalettedContainer<BlockState>>();
            for(int i=0;i<count;i++) {
                var section=list.getCompound(i);var palette=section.getList("palette",Tag.TAG_COMPOUND);
                if(!Set.of("palette","data").containsAll(section.getAllKeys())
                        || palette.isEmpty() || palette.size()>4096 || section.contains("data") && !section.contains("data",Tag.TAG_LONG_ARRAY)
                        || palette.size()==1 && section.contains("data")
                        || section.getLongArray("data").length>1024)
                    throw new IllegalArgumentException("Invalid retained block palette");
                // Native decoding has a recovery-to-AIR path. Validate each state first so
                // unknown blocks cannot silently grant support or erase a cavity/water input.
                for(var value:palette)BlockState.CODEC.parse(NbtOps.INSTANCE,value).getOrThrow();
                var decoded=SECTION.parse(NbtOps.INSTANCE,section).getOrThrow();
                // Exercise every packed index now, before retaining a malformed container.
                for(int x=0;x<16;x++)for(int y=0;y<16;y++)for(int z=0;z<16;z++)
                    Objects.requireNonNull(decoded.get(x,y,z));
                sections.add(decoded);
            }
            if(!tag.contains("grading",Tag.TAG_COMPOUND))throw new IllegalArgumentException("Invalid retained grading field");
            var own=tag.getCompound("grading");Journal journal=null;
            if(!own.isEmpty()) {
                if(!own.getAllKeys().equals(Set.of("layout","cells")) || !own.contains("layout",Tag.TAG_STRING)
                        || !own.contains("cells",Tag.TAG_LIST))throw new IllegalArgumentException("Invalid retained grading header");
                var listCells=(ListTag)own.get("cells");
                if(!listCells.isEmpty() && listCells.getElementType()!=Tag.TAG_COMPOUND)
                    throw new IllegalArgumentException("Invalid retained grading list");
                if(listCells.size()>VillageGrading.MAX_CELLS)throw new IllegalArgumentException("Retained grading budget exceeded");
                var cells=new ArrayList<VillageGrading.Cell>();
                for(var value:listCells) {
                    var cell=(CompoundTag)value;
                    if(!cell.getAllKeys().equals(Set.of("position","original","target")) || !cell.contains("position",Tag.TAG_LONG)
                            || !cell.contains("original",Tag.TAG_COMPOUND) || !cell.contains("target",Tag.TAG_COMPOUND))
                        throw new IllegalArgumentException("Invalid retained grading cell");
                    cells.add(new VillageGrading.Cell(BlockPos.of(cell.getLong("position")),
                            BlockState.CODEC.parse(NbtOps.INSTANCE,cell.get("original")).getOrThrow(),
                            BlockState.CODEC.parse(NbtOps.INSTANCE,cell.get("target")).getOrThrow()));
                }
                journal=new Journal(own.getString("layout"),cells);
            }
            return new Snapshot(tag.getLong("seed"),tag.getString("identity"),ResourceLocation.parse(tag.getString("dimension")),
                    chunk.getPos(),new StructureTerrainView.ColumnChunk(chunk.getMinBuildHeight(),chunk.getHeight(),sections),journal);
        }
        @Override public CompoundTag write(Snapshot snapshot,HolderLookup.Provider provider) {
            var tag=new CompoundTag();tag.putInt("schema",SCHEMA);tag.putLong("seed",snapshot.seed);
            tag.putString("identity",snapshot.identity);tag.putString("dimension",snapshot.dimension.toString());
            tag.putInt("x",snapshot.position.x);tag.putInt("z",snapshot.position.z);
            tag.putInt("minY",snapshot.terrain.minY);tag.putInt("height",snapshot.terrain.height);
            var sections=new ListTag();
            for(var section:snapshot.terrain.sections)sections.add(SECTION.encodeStart(NbtOps.INSTANCE,section).getOrThrow());
            tag.put("sections",sections);
            var grading=new CompoundTag();
            if(snapshot.grading!=null) {
                grading.putString("layout",snapshot.grading.layout());var cells=new ListTag();
                for(var cell:snapshot.grading.cells()) {
                    var value=new CompoundTag();value.putLong("position",cell.position().asLong());
                    value.put("original",BlockState.CODEC.encodeStart(NbtOps.INSTANCE,cell.original()).getOrThrow());
                    value.put("target",BlockState.CODEC.encodeStart(NbtOps.INSTANCE,cell.target()).getOrThrow());cells.add(value);
                }
                grading.put("cells",cells);
            }
            tag.put("grading",grading);return tag;
        }
    }
}
