package design.inherent.skyweavecompat.terrain.structure;

import java.util.function.Supplier;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import org.sathrek.sky_archipelago.worldgen.generator.field.model.IslandPreview;
import org.sathrek.sky_archipelago.worldgen.generator.structure.StructurePlacementPassContext;

/** A native attempt retains its selected destination through every specialized planner. */
public record StructurePlacementScope(StructureDistribution distribution, StructureFamily family,
        StructurePlacementPassContext pass, boolean planning) {
    public StructurePlacementScope(StructureDistribution distribution, StructureFamily family, StructurePlacementPassContext pass) {
        this(distribution, family, pass, false);
    }
    private static final ThreadLocal<StructurePlacementScope> ACTIVE = new ThreadLocal<>();
    public static StructurePlacementScope current() { return ACTIVE.get(); }
    public ChunkPos destination() { return pass.chunkPos(); }

    public boolean owns(IslandPreview host) {
        var descriptor = host.terrainPlan() == null ? null : host.terrainPlan().descriptor();
        return descriptor != null && descriptor.equals(pass.islandField().sampleDominantIslandDescriptor(
                destination().getMiddleBlockX(), destination().getMiddleBlockZ(), pass.settings()));
    }

    public static <T> T run(StructurePlacementPassContext pass, StructureSet.StructureSelectionEntry entry, Supplier<T> action) {
        var placement = pass.structureState().getPlacementsForStructure(entry.structure()).stream()
                .filter(candidate -> candidate instanceof ArchipelagoStructurePlacement).findFirst().orElse(null);
        var scope = placement instanceof ArchipelagoStructurePlacement adapted && adapted.distribution().manages(entry)
                ? new StructurePlacementScope(adapted.distribution(), adapted.family(), pass) : null;
        return with(scope, action);
    }

    static <T> T with(StructurePlacementScope scope, Supplier<T> action) {
        var previous = ACTIVE.get();
        if (scope == null) ACTIVE.remove(); else ACTIVE.set(scope);
        try { return action.get(); }
        finally { if (previous == null) ACTIVE.remove(); else ACTIVE.set(previous); }
    }
}
