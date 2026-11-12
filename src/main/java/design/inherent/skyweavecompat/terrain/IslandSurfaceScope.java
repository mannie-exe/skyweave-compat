package design.inherent.skyweavecompat.terrain;

import org.sathrek.sky_archipelago.worldgen.generator.surface.SurfaceBlockSelector.SurfaceLayer;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.Heightmap;
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
        IslandContext natural;
        java.util.List<org.sathrek.sky_archipelago.worldgen.generator.field.TerrainColumn> segments;
        final IslandStyles styles;
        org.sathrek.sky_archipelago.worldgen.generator.field.model.IslandDescriptor owner;
        org.sathrek.sky_archipelago.worldgen.generator.river.RiverColumnSample river;
        org.sathrek.sky_archipelago.worldgen.generator.field.WaterArchetypeColumnProfile water;
        final java.util.Map<ChunkAccess, PaintedSkin[]> painted = new java.util.IdentityHashMap<>();
        Context(IslandField field, SkyIslandSettings settings) {
            this.field = field; this.settings = settings; styles = ((StyledTerrain)(Object)field).skyweave$styles();
        }
        void column(int x, int z) {
            if (this.x == x && this.z == z) return;
            this.x = x; this.z = z; surface = null;
            var column = field.sampleChunkTerrainSnapshot(new net.minecraft.world.level.ChunkPos(Math.floorDiv(x, 16), Math.floorDiv(z, 16)), settings).columnDataAt(x, z);
            top = column.profile().topY();
            water = column.waterArchetype();
            owner = column.dominantDescriptor(); river = column.river();
            var interiors = (InteriorField)(Object)field;
            natural = interiors.skyweave$contextAt(x, z, settings);
            segments = column.segments();
            // The native resolver chooses the actual top contributor before union/snapshot.
            // Do not repaint a lower overlay using another contributor's top palette.
            if (owner != null && column.profile().exists() && interiors.skyweave$isFringe(owner, x, z, settings)
                    && interiors.skyweave$exteriorOf(owner, x, z, settings).topY() == top)
                surface = SurfaceProvenance.fringe(owner);
        }

        IslandContext.Body solidOwner(int y) {
            int floor=river.terrainModified() ? river.solidSurfaceY()-InteriorConfiguration.profile().shellThickness()+1
                    : Integer.MAX_VALUE;
            return natural.solidOwner(y,owner,floor);
        }
        int roof(int y) {
            for (var segment : segments) if (segment.contains(y)) return segment.topY();
            return Integer.MIN_VALUE;
        }

        BlockState material(BlockState selected, int x, int y, int z, int roof) {
            var selectedOwner = solidOwner(y);
            return DeepDarkMaterials.surface(selected, styles, selectedOwner, x, z, roof-y);
        }

        PaintedSkin skin(ChunkAccess chunk, BlockPos position) {
            // buildSurface finishes every skin column before calling either foundation helper.
            // Freeze all 256 columns on the first foundation request, before any foundation
            // writes can influence the palette of an overlapping structure.
            var columns = painted.computeIfAbsent(chunk, source -> {
                var snapshot = new PaintedSkin[256];
                int minX = source.getPos().getMinBlockX(), minZ = source.getPos().getMinBlockZ();
                for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) {
                    // ChunkAccess already converts first-available height to the occupied Y.
                    int top = source.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z);
                    if (top < source.getMinBuildHeight()) continue;
                    var at = new BlockPos(minX + x, top, minZ + z);
                    snapshot[x * 16 + z] = new PaintedSkin(source.getBlockState(at),
                            source.getBlockState(at.below()), source.getBlockState(at.below(2)));
                }
                return snapshot;
            });
            return columns[(position.getX() & 15) * 16 + (position.getZ() & 15)];
        }
    }
    private record PaintedSkin(BlockState top, BlockState under, BlockState deep) {}
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
        var owner = context.solidOwner(pos.getY());
        if (DeepDarkMaterials.special(context.styles, owner)) {
            int roof = context.roof(pos.getY());
            return roof == Integer.MIN_VALUE ? selected
                    : DeepDarkMaterials.surface(selected, context.styles, owner, pos.getX(), pos.getZ(), roof-pos.getY());
        }
        if (context.surface != null) selected = context.surface.material(pos.getX(), pos.getZ(), top - pos.getY(), selected);
        var biome = chunk.getNoiseBiome(pos.getX() >> 2, top >> 2, pos.getZ() >> 2);
        return BiomeMaterials.surface(selected, biome, top - pos.getY());
    }

    public static int referenceY(int x, int y, int z) {
        var context = CURRENT.get();
        if (context == null) return y;
        context.column(x,z);
        var owner = context.solidOwner(y);
        return owner == null ? y : owner.geology().toReference(y);
    }

    /** Capture after skin painting, before either foundation fills or monument cuts. */
    public static void captureFoundationSkin(ChunkAccess chunk) {
        var context = CURRENT.get();
        if (context != null) context.skin(chunk, chunk.getPos().getWorldPosition());
    }

    /** Select materials only: native foundation blending owns its separate permission to fill air. */
    public static SurfaceLayer foundation(ChunkAccess chunk, net.minecraft.core.Holder<net.minecraft.world.level.biome.Biome> biome,
            BlockPos position, SurfaceLayer nativeLayer) {
        var context = CURRENT.get();
        if (context == null) return nativeLayer;
        context.column(position.getX(), position.getZ());
        var skin = context.skin(chunk, position);
        var surfaceBiome = chunk.getNoiseBiome(position.getX() >> 2, context.top >> 2, position.getZ() >> 2);
        if (!surfaceBiome.equals(biome)) nativeLayer = org.sathrek.sky_archipelago.worldgen.generator.surface.SurfaceBlockSelector
                .forBiome(surfaceBiome, position.atY(context.top));
        var layer = context.surface != null && context.surface.role() != SurfaceProvenance.Role.LAND
                ? context.surface.palette(position.getX(), position.getZ())
                : context.water.exists() ? org.sathrek.sky_archipelago.worldgen.generator.surface.SurfaceBlockSelector
                        .forWaterArchetype(surfaceBiome, position.atY(context.top), context.water)
                        : paintedLayer(skin, nativeLayer);
        return new SurfaceLayer(context.material(BiomeMaterials.surface(layer.top(), surfaceBiome, 0), position.getX(), context.top, position.getZ(), context.top),
                context.material(BiomeMaterials.surface(layer.under(), surfaceBiome, 1), position.getX(), context.top-1, position.getZ(), context.top),
                context.material(BiomeMaterials.surface(layer.deep(), surfaceBiome, 2), position.getX(), context.top-2, position.getZ(), context.top), layer.soilDepth());
    }

    private static SurfaceLayer paintedLayer(PaintedSkin skin, SurfaceLayer fallback) {
        // Vanilla surface rules also paint ordinary islands above ocean biomes. Their actual
        // skin can differ from the generic biome selector even without an ocean archetype.
        if (skin == null) return fallback;
        var surface = skin.top();
        if (!NaturalMaterials.skin(surface) || surface.equals(fallback.top())) return fallback;
        var under = skin.under();
        var deep = skin.deep();
        return new SurfaceLayer(surface, NaturalMaterials.skin(under) ? under : surface,
                NaturalMaterials.skin(deep) ? deep : fallback.deep(), fallback.soilDepth());
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
