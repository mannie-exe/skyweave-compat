package design.inherent.skyweavecompat.terrain.structure;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;

/** Physical pieces identify a layout; its hosting chunk and reference counters do not. */
public final class PhysicalStructureIdentity {
    private static final int MAX_PIECES = 4096;
    private static final long MAX_BYTES = 8 * 1024 * 1024;
    // Exact 1.21.1 postprocessing flags, not arbitrary addon fields with similar names.
    private static final Map<String, List<String>> PLACEMENT_FLAGS = Map.of(
            "minecraft:tedp", List.of("hasPlacedChest0", "hasPlacedChest1", "hasPlacedChest2", "hasPlacedChest3"),
            "minecraft:tejp", List.of("placedMainChest", "placedHiddenChest", "placedTrap1", "placedTrap2"),
            "minecraft:tesh", List.of("Witch", "Cat"), "minecraft:mscorridor", List.of("hps"),
            "minecraft:shcc", List.of("Chest"), "minecraft:shpr", List.of("Mob"),
            "minecraft:nesclt", List.of("Chest"), "minecraft:nescrt", List.of("Chest"), "minecraft:nemt", List.of("Mob"));
    private PhysicalStructureIdentity() {}

    public static String of(ResourceLocation id, StructureStart start, StructurePieceSerializationContext context) {
        if (start.getPieces().size() > MAX_PIECES) throw new IllegalArgumentException("Structure exceeds 4096 pieces");
        return of(id, start.getPieces().stream().map(piece -> piece.createTag(context)).toList());
    }

    public static String of(ResourceLocation id, List<CompoundTag> pieces) {
        if (pieces.isEmpty() || pieces.size() > MAX_PIECES)
            throw new IllegalArgumentException("Physical structure identity requires 1..4096 pieces");
        long bytes = 0;
        var records = new ArrayList<String>(pieces.size());
        for (var piece : pieces) {
            bytes += piece.sizeInBytes();
            if (bytes > MAX_BYTES) throw new IllegalArgumentException("Structure identity exceeds 8 MiB");
            var physical = piece.copy();
            if (physical.getString("id").startsWith("minecraft:")) physical.remove("GD");
            // The native jigsaw loader derives BB from element/position/rotation,
            // discarding assembly-only expansion padding. Those inputs remain significant.
            if (physical.getString("id").equals("minecraft:jigsaw")) physical.remove("BB");
            for (var flag : PLACEMENT_FLAGS.getOrDefault(physical.getString("id"), List.of())) physical.remove(flag);
            records.add(canonical(physical));
        }
        records.sort(String::compareTo);
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            for (var record : records) {
                digest.update(record.getBytes(StandardCharsets.UTF_8));
                digest.update((byte)0);
            }
            return id + "/" + HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException impossible) { throw new AssertionError(impossible); }
    }

    /** The native reference owner is the center chunk of the adjusted physical bounds. */
    public static ChunkPos referenceChunk(StructureStart start, StructurePieceSerializationContext context) {
        var box = referenceBounds(start, context);
        return new ChunkPos(Math.floorDiv((int)(((long)box.minX() + box.maxX()) / 2), 16),
                Math.floorDiv((int)(((long)box.minZ() + box.maxZ()) / 2), 16));
    }

    public static net.minecraft.world.level.levelgen.structure.BoundingBox referenceBounds(
            StructureStart start, StructurePieceSerializationContext context) {
        return referenceBounds(start, context.structureTemplateManager());
    }

    static net.minecraft.world.level.levelgen.structure.BoundingBox referenceBounds(
            StructureStart start, net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager templates) {
        var boxes = start.getPieces().stream().map(piece -> {
            if (piece.getClass() == net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece.class) {
                var pool = (net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece)piece;
                return pool.getElement().getBoundingBox(templates, pool.getPosition(), pool.getRotation());
            }
            return piece.getBoundingBox();
        }).toList();
        return start.getStructure().adjustBoundingBox(
                net.minecraft.world.level.levelgen.structure.BoundingBox.encapsulatingBoxes(boxes).orElseThrow());
    }

    private static String canonical(Tag tag) {
        if (tag instanceof CompoundTag compound) {
            var text = new StringBuilder("{");
            for (var key : compound.getAllKeys().stream().sorted().toList())
                text.append(StringTag.valueOf(key)).append(':').append(canonical(compound.get(key))).append(',');
            return text.append('}').toString();
        }
        if (tag instanceof ListTag list) {
            var text = new StringBuilder("[");
            for (var child : list) text.append(canonical(child)).append(',');
            return text.append(']').toString();
        }
        return tag.toString();
    }
}
