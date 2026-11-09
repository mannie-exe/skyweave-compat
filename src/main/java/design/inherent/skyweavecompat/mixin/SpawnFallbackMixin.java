package design.inherent.skyweavecompat.mixin;

import design.inherent.skyweavecompat.terrain.*;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import org.sathrek.sky_archipelago.worldgen.spawn.SkyIslandSpawnFallback;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = SkyIslandSpawnFallback.class, remap = false)
public abstract class SpawnFallbackMixin {
    @Inject(method = "onServerStarted", at = @At("HEAD"), cancellable = true, require = 1)
    private static void skyweave$preserveInitialSelection(ServerStartedEvent event, CallbackInfo callback) {
        var level = event.getServer().overworld();
        if (level == null || level.getGameTime() > 40
                || !(level.getChunkSource().getGenerator() instanceof GeologicalGenerator generator)
                || !generator.skyweave$isEnabled()) return;
        // Upstream can replace the saved companion before our return hook observes the selection flag.
        if (level.getDataStorage().computeIfAbsent(InitialSpawnState.FACTORY, InitialSpawnState.ID).selected()) callback.cancel();
    }
    @Inject(method = "onServerStarted", at = @At("RETURN"), require = 1)
    private static void skyweave$companionLanding(ServerStartedEvent event, CallbackInfo callback) {
        var level = event.getServer().overworld();
        if (level == null || level.getGameTime() > 40
                || !(level.getChunkSource().getGenerator() instanceof GeologicalGenerator generator)
                || !generator.skyweave$isEnabled()) return;
        var selection = level.getDataStorage().computeIfAbsent(InitialSpawnState.FACTORY, InitialSpawnState.ID);
        if (selection.selected()) return;
        var position = SpawnLanding.resolve(level, level.getSharedSpawnPos());
        level.setDefaultSpawnPos(position, 0);
        selection.markSelected();
    }
}
