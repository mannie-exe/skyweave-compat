package design.inherent.skyweavecompat.climate;

import java.util.List;

/** Exact block-column data for one tracked chunk; no geometric interpolation. */
public record ClimateChunk(int x, int z, List<ClimateColumn> columns) {
    public static final int MAX_TOTAL_LAYERS = 4096;
    public ClimateChunk {
        columns = List.copyOf(columns);
        if (Math.abs((long)x) > 1875000 || Math.abs((long)z) > 1875000 || columns.size() != 256
                || columns.stream().mapToInt(column -> column.layers().size()).sum() > MAX_TOTAL_LAYERS)
            throw new IllegalArgumentException("Invalid climate chunk");
    }
    /** Complete producer admission, before a cache or transport can observe this chunk. */
    public static ClimateChunk capture(int x,int z,java.util.function.IntFunction<ClimateColumn> source) {
        if(Math.abs((long)x)>1875000 || Math.abs((long)z)>1875000)throw new IllegalArgumentException("Climate chunk outside world");
        var columns=new java.util.ArrayList<ClimateColumn>(256);int total=0;
        for(int i=0;i<256;i++) {
            var column=java.util.Objects.requireNonNull(source.apply(i));
            if((total+=column.layers().size())>MAX_TOTAL_LAYERS)
                throw new ClimateColumn.Unavailable(ClimateColumn.Unavailable.Reason.BUDGET);
            columns.add(column);
        }
        return new ClimateChunk(x,z,columns);
    }
    public ClimateColumn column(int blockX, int blockZ) {
        if (Math.floorDiv(blockX,16) != x || Math.floorDiv(blockZ,16) != z)
            throw new IllegalArgumentException("Climate query outside chunk");
        return columns.get((blockX & 15) * 16 + (blockZ & 15));
    }
}
