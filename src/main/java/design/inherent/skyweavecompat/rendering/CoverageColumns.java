package design.inherent.skyweavecompat.rendering;

import java.util.Objects;

/** Immutable observations for the exact 64 by 64 source used to build one published DH mesh. */
public final class CoverageColumns {
    public enum State { UNKNOWN, EMPTY, READY }
    public static final int WIDTH = 64;
    private final long section;
    private final int originX, originZ, scale;
    private final byte[] states;
    private final Object owner, source;

    public CoverageColumns(long section, int originX, int originZ, int scale, byte[] states) {
        if (scale < 1 || scale > 1 << 20 || Integer.bitCount(scale) != 1 || states.length != WIDTH * WIDTH)
            throw new IllegalArgumentException("Invalid coverage grid");
        for (byte state : states) if (state < 0 || state > State.READY.ordinal())
            throw new IllegalArgumentException("Invalid column state");
        this.section = section; this.originX = originX; this.originZ = originZ; this.scale = scale;
        this.states = states.clone(); owner = null; source = new Object();
    }

    private CoverageColumns(CoverageColumns columns, Object owner) {
        section = columns.section; originX = columns.originX; originZ = columns.originZ;
        scale = columns.scale; states = columns.states; source = columns.source; this.owner = Objects.requireNonNull(owner);
    }

    public CoverageColumns ownedBy(Object owner) { return new CoverageColumns(this, owner); }
    public Object owner() { return owner; }
    public Object source() { return source; }
    public long section() { return section; }

    /** All source columns covering this complete 16-block cell must have qualified observations. */
    public State cell(int chunkX, int chunkZ) {
        long x0 = (long) chunkX * 16 - originX, z0 = (long) chunkZ * 16 - originZ;
        long x1 = x0 + 15, z1 = z0 + 15;
        if (x0 < 0 || z0 < 0 || x1 >= (long) WIDTH * scale || z1 >= (long) WIDTH * scale) return State.UNKNOWN;
        boolean populated = false;
        for (int x = (int) (x0 / scale); x <= x1 / scale; x++)
            for (int z = (int) (z0 / scale); z <= z1 / scale; z++) {
                byte state = states[x * WIDTH + z];
                if (state == State.UNKNOWN.ordinal()) return State.UNKNOWN;
                populated |= state == State.READY.ordinal();
            }
        return populated ? State.READY : State.EMPTY;
    }
}
