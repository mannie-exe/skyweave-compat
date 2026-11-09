package design.inherent.skyweavecompat.terrain;

import org.sathrek.sky_archipelago.worldgen.generator.surface.SurfaceBlockSelector.SurfaceLayer;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import org.sathrek.sky_archipelago.config.SkyIslandSettings;
import org.sathrek.sky_archipelago.worldgen.generator.field.IslandField;

/** Per-call context for native surface helpers; restored even when painting fails. */
public final class IslandSurfaceScope {
    private static final class Context {
        final IslandField field;
        final SkyIslandSettings settings;
        int x = Integer.MIN_VALUE, z, top;
        SurfaceProvenance surface;
        Context(IslandField field, SkyIslandSettings settings) { this.field = field; this.settings = settings; }
        void column(int x, int z) {
            if (this.x == x && this.z == z) return;
            this.x = x; this.z = z; surface = null;
            var column = field.sampleChunkTerrainSnapshot(new net.minecraft.world.level.ChunkPos(Math.floorDiv(x, 16), Math.floorDiv(z, 16)), settings).columnDataAt(x, z);
            top = column.profile().topY();
            var owner = column.dominantDescriptor();
            var interiors = (InteriorField)(Object)field;
            // The native resolver chooses the actual top contributor before union/snapshot.
            // Do not repaint a lower overlay using another contributor's top palette.
            if (owner != null && column.profile().exists() && interiors.skyweave$isFringe(owner, x, z, settings)
                    && interiors.skyweave$exteriorOf(owner, x, z, settings).topY() == top)
                surface = SurfaceProvenance.fringe(owner);
        }
    }
    private static final ThreadLocal<Context> CURRENT = new ThreadLocal<>();

    private IslandSurfaceScope() {}

    public static void run(IslandField field, SkyIslandSettings settings, Runnable action) {
        var previous = CURRENT.get();
        if (((ProfiledTerrain)(Object)field).skyweave$profile() == null) CURRENT.remove();
        else CURRENT.set(new Context(field, settings));
        try { action.run(); }
        finally {
            if (previous == null) CURRENT.remove();
            else CURRENT.set(previous);
        }
    }

    /** Null means the native write would refill an authored opening or fluid. */
    public static BlockState surface(ChunkAccess chunk, BlockPos pos, BlockState selected) {
        var context = CURRENT.get();
        if (context == null) return selected;
        var current = chunk.getBlockState(pos);
        if (current.isAir() || !current.getFluidState().isEmpty()) return null;
        context.column(pos.getX(), pos.getZ());
        int top = context.top;
        if (context.surface != null) selected = context.surface.material(pos.getX(), pos.getZ(), top - pos.getY(), selected);
        var biome = chunk.getNoiseBiome(pos.getX() >> 2, top >> 2, pos.getZ() >> 2);
        return BiomeMaterials.surface(selected, biome, top - pos.getY());
    }

    public static int referenceY(int x, int y, int z) {
        var context = CURRENT.get();
        if (context == null) return y;
        var frame = ((GeologicalField)(Object)context.field).skyweave$frameAt(x, z, context.settings);
        return frame == null ? y : frame.toReference(y);
    }

    public static SurfaceLayer soilLayer(
            int x, int z, int roofY,
            SurfaceLayer nativeLayer) {
        var context = CURRENT.get();
        if (context == null) return nativeLayer;
        context.column(x, z);
        return roofY == context.top && context.surface != null && context.surface.role() != SurfaceProvenance.Role.LAND
                ? context.surface.palette(x, z) : nativeLayer;
    }
}
