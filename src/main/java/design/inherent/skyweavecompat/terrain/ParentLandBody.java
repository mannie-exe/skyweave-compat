package design.inherent.skyweavecompat.terrain;

import org.sathrek.sky_archipelago.worldgen.generator.field.TerrainColumn;

/** Original solid envelope and the exact translation already admitted by the exterior sampler. */
public interface ParentLandBody {
    record Frame(TerrainColumn parent, int shift) {
        public Frame {
            if (parent == null || !parent.exists()) throw new IllegalArgumentException("Missing parent land body");
            Math.addExact(parent.bottomY(), shift);
            Math.addExact(parent.topY(), shift);
        }
        public int parentY(int physicalY) { return Math.subtractExact(physicalY, shift); }
        public int physicalY(int parentY) { return Math.addExact(parentY, shift); }
    }
    Frame skyweave$parentLandBody();
    void skyweave$parentLandBody(Frame frame);
}
