package design.inherent.skyweavecompat.rendering;

/** Reverses BLOCK-format vertices without changing their position/UV association. */
public final class ReverseVertices {
    private ReverseVertices() {}

    public static int[] copy(int[] vertices) {
        if (vertices.length != 32) throw new IllegalArgumentException("Expected four BLOCK vertices");
        int[] result = new int[vertices.length];
        for (int target = 0; target < 4; target++) {
            int source = (4 - target) % 4;
            System.arraycopy(vertices, source * 8, result, target * 8, 8);
            result[target * 8 + 7] = oppositeNormal(vertices[source * 8 + 7]);
        }
        return result;
    }

    /** Compare cyclic reversed BLOCK geometry without allocating a temporary quad per pair. */
    public static boolean matches(int[] source, int[] candidate) {
        if (source.length != 32 || candidate.length != 32) return false;
        for (int shift = 0; shift < 4; shift++) {
            boolean same = true;
            for (int vertex = 0; vertex < 4 && same; vertex++) for (int attribute = 0; attribute < 8; attribute++) {
                int value = source[((4 - vertex) % 4) * 8 + attribute];
                if (attribute == 7) value = oppositeNormal(value);
                if (value != candidate[((vertex + shift) % 4) * 8 + attribute]) { same = false; break; }
            }
            if (same) return true;
        }
        return false;
    }

    private static int oppositeNormal(int normal) {
        return normal & 0xff000000 | (-(byte) normal & 255)
                | (-(byte) (normal >>> 8) & 255) << 8 | (-(byte) (normal >>> 16) & 255) << 16;
    }
}
