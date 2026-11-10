package design.inherent.skyweavecompat.terrain.structure;

import design.inherent.skyweavecompat.mixin.JigsawSearchAccess;
import design.inherent.skyweavecompat.mixin.TemplatePoolSearchAccess;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.OptionalLong;
import java.util.Set;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.pools.EmptyPoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraft.world.level.levelgen.structure.structures.*;

/** Version-qualified layout bounds; missing metadata must never establish radius completeness. */
final class StructureSearchBounds {
    private final ServerLevel level;
    private final java.util.function.BooleanSupplier live;
    private final IdentityHashMap<StructurePoolElement, Long> extents = new IdentityHashMap<>();
    private final Map<String, Long> templateExtents = new HashMap<>();
    private Long fallbackExtent;
    private static final int MAX_ELEMENTS = 4096;

    StructureSearchBounds(ServerLevel level) { this(level, () -> true); }
    StructureSearchBounds(ServerLevel level, java.util.function.BooleanSupplier live) { this.level = level; this.live = live; }

    OptionalLong displacement(Structure structure) {
        if (!live.getAsBoolean()) return OptionalLong.empty();
        List<Holder<StructureTemplatePool>> roots;
        int reach, offset;
        if (structure.getClass() == JigsawStructure.class) {
            var access = (JigsawSearchAccess)structure;
            reach = access.skyweave$maximumDistance();
            offset = 16;
            roots = new ArrayList<>();
            roots.add(access.skyweave$startPool());
            var pools = level.registryAccess().registryOrThrow(Registries.TEMPLATE_POOL);
            access.skyweave$poolAliases().stream().flatMap(binding -> binding.allTargets()).distinct().limit(MAX_ELEMENTS)
                    .forEach(key -> roots.add(pools.getHolderOrThrow(key)));
        } else if (structure instanceof IslandJigsaw custom) {
            reach = custom.skyweave$horizontalReach();
            offset = custom.skyweave$maximumRootOffset();
            roots = custom.skyweave$possibleRoots(level.registryAccess());
        } else return nativeDisplacement(structure);
        if (reach < 0 || offset < 0 || roots.isEmpty() || roots.size() > MAX_ELEMENTS) return OptionalLong.empty();
        long root = 0;
        try {
            for (var pool : roots) root = Math.max(root, extent(pool.value()));
            if (fallbackExtent == null) {
                long maximum = 0;
                Set<StructureTemplatePool> fallbacks = Collections.newSetFromMap(new IdentityHashMap<>());
                var pools = level.registryAccess().registryOrThrow(Registries.TEMPLATE_POOL);
                pools.forEach(pool -> {
                    requireLive();
                    fallbacks.add(pool.getFallback().value());
                    if (fallbacks.size() > MAX_ELEMENTS) throw new IllegalStateException("Too many distinct fallback pools");
                });
                for (var pool : fallbacks) maximum = Math.max(maximum, extent(pool));
                fallbackExtent = maximum;
            }
        } catch (IllegalArgumentException | IllegalStateException unavailable) {
            return OptionalLong.empty();
        }
        // A named starting connector can move the root by one root extent. Children
        // stay inside the native root-center reach; caps add at most one fallback
        // extent on each side of their connector. Reference-chunk rounding adds <16.
        return OptionalLong.of(Math.addExact(Math.addExact(offset + (long)reach, 2 * root), 2 * fallbackExtent + 16));
    }

    private OptionalLong nativeDisplacement(Structure structure) {
        if (structure instanceof IslandUnderground custom) {
            long displacement = custom.skyweave$maximumLayoutDisplacement();
            return displacement < 0 ? OptionalLong.empty() : OptionalLong.of(displacement);
        }
        var type = structure.getClass();
        // Pinned 1.21.1 legacy assemblers bound branch origins to 80/112 blocks.
        // Their longest outgoing corridor/room fits in32; include source and
        // reference-chunk rounding separately. Exact classes exclude foreign assemblers.
        if (type == MineshaftStructure.class) return OptionalLong.of(80 + 32 + 32);
        if (type == StrongholdStructure.class || type == NetherFortressStructure.class)
            return OptionalLong.of(112 + 32 + 32);
        if (type == BuriedTreasureStructure.class) return OptionalLong.of(32);
        // These native constructors use fixed envelopes (largest is58x58).
        if (type == DesertPyramidStructure.class || type == JungleTempleStructure.class
                || type == SwampHutStructure.class || type == OceanMonumentStructure.class)
            return OptionalLong.of(64 + 32);
        try {
            String folder;
            long assemblyOffset = 0;
            if (type == ShipwreckStructure.class) folder = "shipwreck/";
            else if (type == RuinedPortalStructure.class) folder = "ruined_portal/";
            else if (type == IglooStructure.class) folder = "igloo/";
            else if (type == NetherFossilStructure.class) folder = "nether_fossils/";
            else if (type == OceanRuinStructure.class) { folder = "underwater_ruin/"; assemblyOffset = 64; }
            else if (type == WoodlandMansionStructure.class) {
                folder = "woodland_mansion/";
                // Native eleven-cell grids, eight blocks per cell, plus one border.
                assemblyOffset = 12L * 8;
            } else return OptionalLong.empty();
            return OptionalLong.of(assemblyOffset + 2 * templates(folder) + 32);
        } catch (IllegalArgumentException | IllegalStateException unavailable) {
            return OptionalLong.empty();
        }
    }

    private long templates(String folder) {
        var known = templateExtents.get(folder);
        if (known != null) return known;
        List<net.minecraft.resources.ResourceLocation> names;
        try (var resources = level.getStructureManager().listTemplates()) {
            names = resources.filter(id -> id.getNamespace().equals("minecraft") && id.getPath().startsWith(folder))
                    .limit(MAX_ELEMENTS + 1L).toList();
        }
        if (names.isEmpty() || names.size() > MAX_ELEMENTS) throw new IllegalStateException("Native template metadata unavailable");
        long extent = 0;
        for (var id : names) {
            requireLive();
            var template = level.getStructureManager().getOrCreate(id);
            for (var rotation : Rotation.values()) {
                var box = template.getBoundingBox(new net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings()
                        .setRotation(rotation), BlockPos.ZERO);
                extent = Math.max(extent, Math.max(Math.max(Math.abs((long)box.minX()), Math.abs((long)box.maxX())),
                        Math.max(Math.abs((long)box.minZ()), Math.abs((long)box.maxZ()))));
            }
        }
        templateExtents.put(folder, extent + 1);
        return extent + 1;
    }

    private long extent(StructureTemplatePool pool) {
        long extent = 0;
        for (var entry : ((TemplatePoolSearchAccess)pool).skyweave$entries()) {
            requireLive();
            var element = entry.getFirst();
            if (element == EmptyPoolElement.INSTANCE) continue;
            var known = extents.get(element);
            if (known == null) {
                if (extents.size() >= MAX_ELEMENTS) throw new IllegalStateException("Layout metadata exceeds 4096 distinct elements");
                long measured = 0;
                for (var rotation : Rotation.values()) {
                    var box = element.getBoundingBox(level.getStructureManager(), BlockPos.ZERO, rotation);
                    measured = Math.max(measured, Math.max(Math.max(Math.abs((long)box.minX()), Math.abs((long)box.maxX())),
                            Math.max(Math.abs((long)box.minZ()), Math.abs((long)box.maxZ()))));
                }
                known = measured + 1;
                extents.put(element, known);
            }
            extent = Math.max(extent, known);
        }
        return extent;
    }

    private void requireLive() {
        if (!live.getAsBoolean()) throw new java.util.concurrent.CancellationException("Layout inspection cancelled");
    }

    /** Squared lower bound to any point in an axis-aligned displacement envelope. */
    static long minimumDistance(int x, int z, int originX, int originZ, long shift) {
        if (shift < 0) throw new IllegalArgumentException("Negative layout displacement");
        long dx = Math.max(0, Math.abs((long)x - originX) - shift);
        long dz = Math.max(0, Math.abs((long)z - originZ) - shift);
        return dx * dx + dz * dz;
    }

    static boolean withinWorld(int chunkX, int chunkZ) {
        long x = 16L * chunkX, z = 16L * chunkZ;
        return x >= -30_000_000 && x < 30_000_000 && z >= -30_000_000 && z < 30_000_000;
    }
}
