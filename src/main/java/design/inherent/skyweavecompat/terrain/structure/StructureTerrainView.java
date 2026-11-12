package design.inherent.skyweavecompat.terrain.structure;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.levelgen.Heightmap;

/** A copied native terrain input. Processors cannot load chunks, acquire a mutable level or write through it. */
public final class StructureTerrainView implements InvocationHandler {
    public static final int MAX_CHUNKS=64, MAX_SECTIONS=64;
    public static final class Unavailable extends RuntimeException {
        public Unavailable(String message) { super(message); }
    }
    static final class ColumnChunk {
        final int minY,height;
        final List<PalettedContainer<BlockState>> sections;
        final Map<Heightmap.Types,int[]> heights=new ConcurrentHashMap<>();
        ColumnChunk(ChunkAccess chunk) {
            minY=chunk.getMinBuildHeight();height=chunk.getHeight();
            if(height<=0 || height%16!=0 || minY%16!=0 || (long)minY+height>Integer.MAX_VALUE || chunk.getSections().length>MAX_SECTIONS
                    || chunk.getSections().length!=height/16)throw new Unavailable("Unsupported terrain snapshot height");
            var copied=new ArrayList<PalettedContainer<BlockState>>();
            for(var section:chunk.getSections())copied.add(section.getStates().copy());
            sections=List.copyOf(copied);
        }
        ColumnChunk(int minY,int height,List<PalettedContainer<BlockState>> sources) {
            if(height<=0 || height%16!=0 || minY%16!=0 || (long)minY+height>Integer.MAX_VALUE
                    || sources.size()>MAX_SECTIONS || sources.size()!=height/16)
                throw new Unavailable("Unsupported retained terrain bounds");
            this.minY=minY;this.height=height;
            sections=sources.stream().map(PalettedContainer::copy).toList();
        }
        BlockState state(int x,int y,int z) {
            if(y<minY || y>=minY+height)throw new Unavailable("Terrain input outside captured build height: "+y);
            return sections.get((y-minY)>>4).get(x&15,y&15,z&15);
        }
        int height(Heightmap.Types type,int x,int z) {
            var values=heights.computeIfAbsent(type,key->{
                var result=new int[256];Arrays.fill(result,minY);
                for(int dx=0;dx<16;dx++)for(int dz=0;dz<16;dz++)for(int y=minY+height-1;y>=minY;y--)
                    if(key.isOpaque().test(state(dx,y,dz))) { result[dx*16+dz]=y+1;break; }
                return result;
            });
            return values[(x&15)*16+(z&15)];
        }
    }
    private final long seed;
    private final RegistryAccess registries;
    private final Map<ChunkPos,ColumnChunk> chunks;
    private final int minY,height;
    private final WorldGenLevel reader;

    private StructureTerrainView(long seed,RegistryAccess registries,Collection<? extends ChunkAccess> sources) {
        if(sources.isEmpty() || sources.size()>MAX_CHUNKS)throw new Unavailable("Terrain snapshot chunk budget exceeded");
        var copied=new HashMap<ChunkPos,ColumnChunk>();
        for(var source:sources) {
            var previous=copied.putIfAbsent(source.getPos(),new ColumnChunk(source));
            if(previous!=null)throw new Unavailable("Duplicate terrain input chunk: "+source.getPos());
        }
        this.seed=seed;this.registries=Objects.requireNonNull(registries);
        chunks=Map.copyOf(copied);var first=chunks.values().iterator().next();minY=first.minY;height=first.height;
        for(var chunk:chunks.values())if(chunk.minY!=minY || chunk.height!=height)
            throw new Unavailable("Mixed terrain snapshot bounds");
        reader=(WorldGenLevel)Proxy.newProxyInstance(StructureTerrainView.class.getClassLoader(),new Class<?>[]{WorldGenLevel.class},this);
    }
    private StructureTerrainView(long seed,RegistryAccess registries,Map<ChunkPos,ColumnChunk> sources) {
        if(sources.isEmpty() || sources.size()>MAX_CHUNKS)throw new Unavailable("Retained terrain chunk budget exceeded");
        this.seed=seed;this.registries=Objects.requireNonNull(registries);chunks=Map.copyOf(sources);
        var first=chunks.values().iterator().next();minY=first.minY;height=first.height;
        for(var chunk:chunks.values())if(chunk.minY!=minY || chunk.height!=height)
            throw new Unavailable("Mixed retained terrain bounds");
        reader=(WorldGenLevel)Proxy.newProxyInstance(StructureTerrainView.class.getClassLoader(),new Class<?>[]{WorldGenLevel.class},this);
    }
    static StructureTerrainView retained(long seed,RegistryAccess registries,Map<ChunkPos,ColumnChunk> sources) {
        return new StructureTerrainView(seed,registries,sources);
    }
    public static StructureTerrainView capture(long seed,RegistryAccess registries,Collection<? extends ChunkAccess> chunks) {
        return new StructureTerrainView(seed,registries,chunks);
    }
    public WorldGenLevel reader() { return reader; }
    /** Apply only an already bound own grading plan to an immutable processor input. */
    public StructureTerrainView overlay(List<VillageGrading.Cell> cells) {
        if(cells.size()>VillageGrading.MAX_CELLS)throw new Unavailable("Terrain overlay budget exceeded");
        var changed=new HashMap<ChunkPos,ColumnChunk>();
        for(var cell:cells) {
            if(!state(cell.position()).equals(cell.original()))throw new Unavailable("Grading does not match natural input");
            var address=new ChunkPos(cell.position());
            var copied=changed.computeIfAbsent(address,key->{var old=chunks.get(key);return new ColumnChunk(old.minY,old.height,old.sections);});
            var at=cell.position();copied.sections.get((at.getY()-copied.minY)>>4).set(at.getX()&15,at.getY()&15,at.getZ()&15,cell.target());
        }
        var all=new HashMap<>(chunks);all.putAll(changed);return new StructureTerrainView(seed,registries,all);
    }
    public boolean contains(BlockPos pos) {
        return pos.getY()>=minY && pos.getY()<minY+height && chunks.containsKey(new ChunkPos(pos));
    }
    private ColumnChunk chunk(int x,int z) {
        var pos=new ChunkPos(Math.floorDiv(x,16),Math.floorDiv(z,16));var result=chunks.get(pos);
        if(result==null)throw new Unavailable("Uncaptured processor terrain input: "+pos);
        return result;
    }
    public BlockState state(BlockPos pos) { return chunk(pos.getX(),pos.getZ()).state(pos.getX(),pos.getY(),pos.getZ()); }
    RegistryAccess registries() { return registries; }
    private record HeightKey(Heightmap.Types type,int x,int z) {}
    private final java.util.concurrent.ConcurrentMap<HeightKey,Integer> heights=new java.util.concurrent.ConcurrentHashMap<>();
    public int height(Heightmap.Types type,int x,int z) {
        var column=chunk(x,z); // Validate the finite captured domain before allocating a cache key.
        return heights.computeIfAbsent(new HeightKey(type,x,z),key->column.height(type,x,z));
    }

    @Override public Object invoke(Object proxy,Method method,Object[] args) {
        return switch(method.getName()) {
            case "getSeed" -> seed;
            case "registryAccess" -> registries;
            case "isClientSide" -> false;
            case "getMinBuildHeight" -> minY;
            case "getMaxBuildHeight" -> minY+height;
            case "getHeight" -> args==null || args.length==0?height:height((Heightmap.Types)args[0],(Integer)args[1],(Integer)args[2]);
            case "getBlockState" -> state((BlockPos)args[0]);
            case "getFluidState" -> state((BlockPos)args[0]).getFluidState();
            case "hasChunk" -> chunks.containsKey(new ChunkPos((Integer)args[0],(Integer)args[1]));
            case "toString" -> "StructureTerrainView["+chunks.size()+" chunks]";
            case "hashCode" -> System.identityHashCode(proxy);
            case "equals" -> proxy==args[0];
            default -> throw new Unavailable("Unsupported processor terrain operation: "+method.getName());
        };
    }
}
