package design.inherent.skyweavecompat.terrain.structure;

import java.util.Collection;

/** Immutable occupied pieces, rather than treating the empty space between buildings as solid. */
public final class StructureVolume {
    public record Box(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        public Box {
            if (maxX < minX || maxY < minY || maxZ < minZ) throw new IllegalArgumentException("Inverted structure box");
        }
        public boolean intersects(Box other, int padding) {
            return intersects(other, padding, padding);
        }
        public boolean intersects(Box other, int firstPadding, int secondPadding) {
            if (firstPadding < 0 || secondPadding < 0) throw new IllegalArgumentException("Negative structure padding");
            long gap = (long)firstPadding + secondPadding;
            return (long)minX <= other.maxX + gap && (long)maxX + gap >= other.minX
                    && (long)minY <= other.maxY + gap && (long)maxY + gap >= other.minY
                    && (long)minZ <= other.maxZ + gap && (long)maxZ + gap >= other.minZ;
        }
    }
    private StructureVolume() {}

    public static boolean conflicts(Collection<Box> first, Collection<Box> second, int padding) {
        if (padding < 0) throw new IllegalArgumentException("Negative structure padding");
        return first.stream().anyMatch(a -> second.stream().anyMatch(b -> a.intersects(b, padding)));
    }

    public static Box bounds(Collection<Box> pieces) {
        if (pieces.isEmpty()) throw new IllegalArgumentException("Empty structure volume");
        return new Box(pieces.stream().mapToInt(Box::minX).min().orElseThrow(), pieces.stream().mapToInt(Box::minY).min().orElseThrow(),
                pieces.stream().mapToInt(Box::minZ).min().orElseThrow(), pieces.stream().mapToInt(Box::maxX).max().orElseThrow(),
                pieces.stream().mapToInt(Box::maxY).max().orElseThrow(), pieces.stream().mapToInt(Box::maxZ).max().orElseThrow());
    }
}
