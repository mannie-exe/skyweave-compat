/* SPDX-License-Identifier: Apache-2.0 */
package design.inherent.skyweavecompat.terrain.structure;

import java.io.Closeable;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import net.minecraft.world.level.ChunkPos;

/** Pages directory names on the native IO owner; never accumulates the world's file inventory. */
public final class StructureRegionCursor implements Closeable {
    public static final int PAGE = 256;
    private final int minX, minZ, maxX, maxZ;
    // Only the native cache/pending snapshot needs deduplication against on-disk names.
    private final Set<ChunkPos> snapshot;
    private final Iterator<ChunkPos> extra;
    private final DirectoryStream<Path> directory;
    private final Iterator<Path> files;
    private boolean closed;

    public StructureRegionCursor(Path folder, Iterable<ChunkPos> nativeRegions,
            int minChunkX, int minChunkZ, int maxChunkX, int maxChunkZ) throws IOException {
        minX = Math.floorDiv(minChunkX, 32); minZ = Math.floorDiv(minChunkZ, 32);
        maxX = Math.floorDiv(maxChunkX, 32); maxZ = Math.floorDiv(maxChunkZ, 32);
        snapshot = new HashSet<>();
        for (var region : nativeRegions) if (inside(region)) snapshot.add(region);
        long cx = Math.floorDiv((long)minX + maxX, 2), cz = Math.floorDiv((long)minZ + maxZ, 2);
        extra = snapshot.stream().sorted(Comparator.<ChunkPos>comparingLong(p -> {
            long dx = p.x - cx, dz = p.z - cz; return dx * dx + dz * dz;
        }).thenComparingLong(ChunkPos::toLong)).iterator();
        if (!Files.isDirectory(folder) && !Files.notExists(folder)) throw new IOException("Region inventory is not an accessible directory");
        directory = Files.isDirectory(folder) ? Files.newDirectoryStream(folder, "r.*.*.mca") : null;
        files = directory == null ? java.util.Collections.emptyIterator() : directory.iterator();
    }

    private boolean inside(ChunkPos region) {
        return region.x >= minX && region.x <= maxX && region.z >= minZ && region.z <= maxZ;
    }

    /** Both useful and skipped names consume a page slice, so sparse inventories still yield. */
    public synchronized StructureSearchStorage.RegionPage next() throws IOException {
        if (closed) return new StructureSearchStorage.RegionPage(List.of(), true, false);
        var result = new ArrayList<ChunkPos>();
        for (int visited = 0; visited < PAGE; visited++) {
            if (extra.hasNext()) { result.add(extra.next()); continue; }
            if (!files.hasNext()) break;
            String name = files.next().getFileName().toString();
            String[] parts = name.split("\\.");
            if (parts.length != 4) continue;
            try {
                var region = new ChunkPos(Integer.parseInt(parts[1]), Integer.parseInt(parts[2]));
                // These are the names native RegionFileStorage actually opens.
                if (!name.equals("r." + region.x + "." + region.z + ".mca")) continue;
                if (inside(region) && !snapshot.contains(region)) result.add(region);
            } catch (NumberFormatException ignored) { /* Not a native coordinate. */ }
        }
        boolean complete = !extra.hasNext() && !files.hasNext();
        if (complete) close();
        return new StructureSearchStorage.RegionPage(result, complete, false);
    }

    @Override public synchronized void close() throws IOException {
        if (closed) return;
        closed = true;
        if (directory != null) directory.close();
        snapshot.clear();
    }
}
