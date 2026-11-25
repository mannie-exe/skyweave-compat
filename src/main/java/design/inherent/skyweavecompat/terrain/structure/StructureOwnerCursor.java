/* SPDX-License-Identifier: Apache-2.0 */
package design.inherent.skyweavecompat.terrain.structure;

import design.inherent.skyweavecompat.terrain.ClusterLayout;
import java.util.NoSuchElementException;

/** Constant-space traversal of the complete conservative owner rectangle, near rings first. */
public final class StructureOwnerCursor {
    private final int minX, minZ, maxX, maxZ, centerX, centerZ;
    private final long total;
    private long visited, ring, dx, dz;

    public StructureOwnerCursor(int x, int z, int radius, int spacing, int halo) {
        if (radius < 1 || radius > 131072 || spacing < 1 || halo < 0)
            throw new IllegalArgumentException("Invalid structure owner bounds");
        minX = Math.toIntExact(Math.floorDiv((long)x - radius, spacing) - halo);
        minZ = Math.toIntExact(Math.floorDiv((long)z - radius, spacing) - halo);
        maxX = Math.toIntExact(Math.floorDiv((long)x + radius, spacing) + halo);
        maxZ = Math.toIntExact(Math.floorDiv((long)z + radius, spacing) + halo);
        centerX = Math.floorDiv(x, spacing); centerZ = Math.floorDiv(z, spacing);
        total = Math.multiplyExact((long)maxX - minX + 1, (long)maxZ - minZ + 1);
    }

    public boolean complete() { return visited == total; }
    public long visited() { return visited; }
    public long total() { return total; }

    public ClusterLayout.Cell next() {
        if (complete()) throw new NoSuchElementException("Structure owner traversal complete");
        while (true) {
            long x = centerX + dx, z = centerZ + dz;
            advance();
            if (x >= minX && x <= maxX && z >= minZ && z <= maxZ) {
                visited++; return new ClusterLayout.Cell((int)x, (int)z);
            }
        }
    }

    private void advance() {
        if (ring == 0) { ring = 1; dx = dz = -1; return; }
        if (Math.abs(dx) == ring) {
            if (dz < ring) { dz++; return; }
        } else if (dz == -ring) { dz = ring; return; }
        if (++dx > ring) { ring++; dx = -ring; }
        dz = -ring;
    }
}
