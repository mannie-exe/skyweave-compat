package design.inherent.skyweavecompat.mixin.water;

import com.AdamNew.dynamicwaters.async.MountainRegionStore;
import com.AdamNew.dynamicwaters.async.MountainRiverScheduler;
import design.inherent.skyweavecompat.water.IslandWaterTerrain;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Keep the native store/watch scheduler without starting an unrequested world-border-wide scan. */
@Mixin(value = MountainRiverScheduler.class, remap = false)
public abstract class WaterSchedulerMixin {
    @Shadow private static MountainRegionStore store;

    @Inject(method = "startPreWarm", at = @At("HEAD"), cancellable = true)
    private static void islandWork(MinecraftServer server, ServerLevel level, CallbackInfo callback) {
        if (!IslandWaterTerrain.applies(level.getChunkSource().getGenerator())) return;
        store = MountainRegionStore.open(level);
        callback.cancel();
    }
}
