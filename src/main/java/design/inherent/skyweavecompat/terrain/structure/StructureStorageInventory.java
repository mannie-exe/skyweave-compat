package design.inherent.skyweavecompat.terrain.structure;

import design.inherent.skyweavecompat.mixin.StructureRegionStorageAccess;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.storage.RegionFileStorage;
import java.nio.file.Files;
import java.util.*;
import java.util.regex.Pattern;

/** Must run on the storage owner's serial queue, alongside native header/cache/pending-write access. */
public final class StructureStorageInventory {
    private static final Pattern skyweave$regionName = Pattern.compile("r\\.(-?\\d+)\\.(-?\\d+)\\.mca");
    public static StructureSearchStorage.Regions regions(RegionFileStorage storage, Iterable<ChunkPos> pending,
            int minX, int minZ, int maxX, int maxZ, int limit) throws java.io.IOException {
        var access = (StructureRegionStorageAccess)(Object)storage;
        var regions = new HashSet<ChunkPos>();
        int loX = Math.floorDiv(minX, 32), loZ = Math.floorDiv(minZ, 32),
                hiX = Math.floorDiv(maxX, 32), hiZ = Math.floorDiv(maxZ, 32);
        java.util.function.Consumer<ChunkPos> add = p -> {
            if (regions.size() <= limit && p.x >= loX && p.x <= hiX && p.z >= loZ && p.z <= hiZ) regions.add(p);
        };
        for (var p : pending) add.accept(new ChunkPos(p.getRegionX(), p.getRegionZ()));
        for (long key : access.skyweave$regionCache().keySet()) add.accept(new ChunkPos(key));
        var folder = access.skyweave$regionFolder();
        if (Files.isDirectory(folder)) try (var files = Files.newDirectoryStream(folder, "r.*.*.mca")) {
            for (var path : files) {
                var name = skyweave$regionName.matcher(path.getFileName().toString());
                if (!name.matches()) continue;
                try { add.accept(new ChunkPos(Integer.parseInt(name.group(1)), Integer.parseInt(name.group(2)))); }
                catch (NumberFormatException ignored) { /* Not a native region coordinate. */ }
                if (regions.size() > limit) break;
            }
        }
        long cx = Math.floorDiv((long)minX + maxX, 64), cz = Math.floorDiv((long)minZ + maxZ, 64);
        var ordered = regions.stream().sorted(Comparator.<ChunkPos>comparingLong(p -> {
            long dx = p.x - cx, dz = p.z - cz; return dx * dx + dz * dz;
        }).thenComparingLong(ChunkPos::toLong)).limit(limit).toList();
        return new StructureSearchStorage.Regions(ordered, regions.size() > limit);
    }
    public static List<ChunkPos> chunks(RegionFileStorage storage, Iterable<ChunkPos> pending, ChunkPos region) throws java.io.IOException {
        var positions = new HashSet<ChunkPos>();
        for (var p : pending)
            if (p.getRegionX() == region.x && p.getRegionZ() == region.z) positions.add(p);
        var access = (StructureRegionStorageAccess)(Object)storage;
        var file = access.skyweave$regionCache().get(region.toLong());
        if (file == null && Files.isRegularFile(access.skyweave$regionFolder()
                .resolve("r." + region.x + "." + region.z + ".mca")))
            file = access.skyweave$region(new ChunkPos(region.x * 32, region.z * 32));
        if (file != null) for (int x = 0; x < 32; x++) for (int z = 0; z < 32; z++) {
            var p = new ChunkPos(region.x * 32 + x, region.z * 32 + z);
            if (file.hasChunk(p)) positions.add(p);
        }
        return positions.stream().sorted(Comparator.comparingLong(ChunkPos::toLong)).toList();
    }
    private StructureStorageInventory() {}
}
