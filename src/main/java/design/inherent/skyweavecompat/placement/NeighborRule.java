package design.inherent.skyweavecompat.placement;

/** Pure horizontal placement rule. Coordinates identify block columns. */
public final class NeighborRule {
    private NeighborRule() {}

    public record Settings(boolean enabled, int minimumGap, int maximumGap, int minimumIslandRadius) {
        public Settings {
            if (minimumGap < 0 || maximumGap < minimumGap || maximumGap > 2048
                    || minimumIslandRadius < 0 || minimumIslandRadius > 2048) {
                throw new IllegalArgumentException("Require 0 <= minimumGap <= maximumGap <= 2048 and island radius in 0..2048");
            }
        }
    }

    public record Footprint(int minX, int minZ, int maxX, int maxZ) {
        public Footprint {
            if (minX > maxX || minZ > maxZ) throw new IllegalArgumentException("Inverted footprint");
        }
        public long distanceSquared(int x, int z) {
            long dx = Math.max(0L, Math.max((long)minX - x, (long)x - maxX));
            long dz = Math.max(0L, Math.max((long)minZ - z, (long)z - maxZ));
            return dx * dx + dz * dz;
        }
    }

    public interface Terrain {
        boolean exists(int x, int z);
        int islandRadius(int x, int z);
    }

    public enum Decision { DISABLED, TOO_CLOSE, NO_HOST, ACCEPTED }

    public static Decision evaluate(Footprint footprint, Settings settings, Terrain terrain) {
        var coarse = coarseDecision(footprint, settings, terrain);
        if (coarse != Decision.ACCEPTED) return coarse;
        int pad = Math.max(0, settings.minimumGap() - 1);
        long minimumSquared = (long)settings.minimumGap() * settings.minimumGap();
        // Every column in the exclusion band is checked, including narrow island tips.
        for (int x = footprint.minX() - pad; x <= footprint.maxX() + pad; x++) {
            for (int z = footprint.minZ() - pad; z <= footprint.maxZ() + pad; z++) {
                long d = footprint.distanceSquared(x, z);
                if ((d == 0 || d < minimumSquared) && terrain.exists(x, z)) return Decision.TOO_CLOSE;
            }
        }
        return Decision.ACCEPTED;
    }

    /** Positive proximity with coarse exclusion; only a candidate screen, never final admission. */
    public static Decision coarseDecision(Footprint footprint, Settings settings, Terrain terrain) {
        if (!settings.enabled()) return Decision.DISABLED;
        int pad = Math.max(0, settings.minimumGap() - 1);
        long minimumSquared = (long)settings.minimumGap() * settings.minimumGap();
        // Reject obvious overlaps before the host search and full clearance scan.
        for (int x = footprint.minX() - pad; x <= footprint.maxX() + pad; x += 16) {
            for (int z = footprint.minZ() - pad; z <= footprint.maxZ() + pad; z += 16) {
                long d = footprint.distanceSquared(x, z);
                if ((d == 0 || d < minimumSquared) && terrain.exists(x, z)) return Decision.TOO_CLOSE;
            }
        }
        if (!hasHost(footprint, settings, terrain)) return Decision.NO_HOST;
        return Decision.ACCEPTED;
    }

    private static boolean hasHost(Footprint footprint, Settings settings, Terrain terrain) {
        int gap = settings.maximumGap();
        long maximumSquared = (long)gap * gap;
        // A positive sample proves proximity. Missing a thin feature only rejects a candidate.
        for (int x = footprint.minX() - gap; x <= footprint.maxX() + gap; x += 16) {
            for (int z = footprint.minZ() - gap; z <= footprint.maxZ() + gap; z += 16) {
                if (footprint.distanceSquared(x, z) > maximumSquared) continue;
                if (terrain.exists(x, z) && terrain.islandRadius(x, z) >= settings.minimumIslandRadius()) return true;
            }
        }
        return false;
    }
}
