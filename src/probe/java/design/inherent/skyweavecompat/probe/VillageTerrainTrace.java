package design.inherent.skyweavecompat.probe;

import com.google.gson.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;

/** Exact reported-witness attribution; never present in the shipped artifact. */
public final class VillageTerrainTrace {
    private static final Set<BlockPos> POINTS=switch(System.getProperty("neighbors.probe.villageWitness","")) {
        case "fortified" -> Set.of(new BlockPos(1931,500,-1476),new BlockPos(1931,499,-1476),
                new BlockPos(1931,498,-1476),new BlockPos(1880,502,-1484),new BlockPos(1880,501,-1484),new BlockPos(1880,500,-1484));
        case "savanna" -> Set.of(new BlockPos(10155,346,6405),new BlockPos(10151,345,6405),new BlockPos(10201,352,6412),
                new BlockPos(10201,351,6412),new BlockPos(10165,351,6371),new BlockPos(10165,350,6371),
                new BlockPos(10171,346,6412),new BlockPos(10170,346,6402),new BlockPos(10175,349,6382),
                new BlockPos(10131,345,6424),new BlockPos(10131,345,6425));
        case "west" -> Set.of(new BlockPos(-610,339,13296),new BlockPos(-609,341,13327),new BlockPos(-593,345,13317),
                new BlockPos(-597,341,13306),new BlockPos(-608,343,13330),new BlockPos(-661,341,13299),
                new BlockPos(-601,343,13342),new BlockPos(-601,342,13342),new BlockPos(-601,341,13342),
                new BlockPos(-601,340,13342),new BlockPos(-601,339,13342),new BlockPos(-626,340,13272));
        default -> Set.of();
    };
    private static final ConcurrentLinkedQueue<JsonObject> EVENTS=new ConcurrentLinkedQueue<>();
    private static final AtomicInteger COUNT=new AtomicInteger();
    private static final ThreadLocal<String> PIECE=new ThreadLocal<>();
    public static void place(PoolElementStructurePiece piece,Runnable original) {
        var previous=PIECE.get();String description=piece.getElement()+" at "+piece.getPosition().toShortString();
        PIECE.set(description.length()>512?description.substring(0,512):description);
        try {original.run();}finally {if(previous==null)PIECE.remove();else PIECE.set(previous);}
    }
    public static void write(ChunkAccess chunk,BlockPos position,BlockState target) {
        if(!POINTS.contains(position) || chunk.getBlockState(position).equals(target) || COUNT.getAndIncrement()>=128)return;
        var row=new JsonObject();row.addProperty("position",position.toShortString());row.addProperty("stage",chunk.getPersistedStatus().toString());
        row.addProperty("from",chunk.getBlockState(position).toString());row.addProperty("to",target.toString());
        row.addProperty("nativePiece",PIECE.get()==null?"outside native piece placement":PIECE.get());
        row.addProperty("structureReferenceTypes",chunk.getAllReferences().size());
        row.addProperty("structureReferenceAddresses",chunk.getAllReferences().values().stream().mapToInt(Set::size).sum());
        row.addProperty("localStructureStarts",chunk.getAllStarts().size());
        row.add("stack",new Gson().toJsonTree(StackWalker.getInstance().walk(frames->frames.limit(18).map(StackWalker.StackFrame::toString).toList())));EVENTS.add(row);
    }
    static JsonArray snapshot() {return new Gson().toJsonTree(List.copyOf(EVENTS)).getAsJsonArray();}
}
