package design.inherent.skyweavecompat.terrain.structure;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.LevelHeightAccessor;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import org.sathrek.sky_archipelago.worldgen.generator.structure.v2.common.placement.StructurePlacementPostProcessContextManager;

/** Saved levels must have planning metadata even when every visited chunk already has its starts. */
public final class StructureLevelIntegration {
    private StructureLevelIntegration() {}

    public static void register() {
        // Vanilla publishes each rotation map before populating it. Detached parallel jigsaw fits can
        // observe a null direction. Initialize the immutable lookup tables before worldgen workers run.
        for (var rotation : com.mojang.math.OctahedralGroup.values())
            for (var direction : net.minecraft.core.Direction.values()) rotation.rotate(direction);
        // Upstream's reset clears only ephemeral descriptor/delivery claims, never saved starts or blocks.
        // Those keys omit a server-session identity; match a fresh JVM when an integrated server reopens.
        NeoForge.EVENT_BUS.addListener((ServerAboutToStartEvent event) -> StructurePlacementPostProcessContextManager.clearForTests());
        NeoForge.EVENT_BUS.addListener((ServerStoppedEvent event) -> StructurePlacementPostProcessContextManager.clearForTests());
        NeoForge.EVENT_BUS.addListener((LevelEvent.Load event) -> {
            if (!(event.getLevel() instanceof ServerLevel level)) return;
            var source = level.getChunkSource();
            var state = source.getGeneratorState();
            var distribution = ((StructurePlacementState)state).skyweave$structureDistribution();
            if (distribution == null) return;
            distribution.bind(level.registryAccess(), level.getServer().getStructureManager(),
                    LevelHeightAccessor.create(level.getMinBuildHeight(), level.getHeight()), state);
        });
    }
}
