package design.inherent.skyweavecompat.mixin.dh;

import com.seibel.distanthorizons.common.wrappers.world.ServerLevelWrapper_neoforge;
import com.seibel.distanthorizons.core.config.Config;
import design.inherent.skyweavecompat.dh.WorldIdentity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.storage.LevelResource;
import org.sathrek.sky_archipelago.worldgen.generator.core.SkyIslandChunkGenerator;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Supply DH's normal pre-cache level key without changing its dimension identifier or protocol. */
@Mixin(value = ServerLevelWrapper_neoforge.class, remap = false)
abstract class WorldLevelKeyMixin {
    @Shadow @Final private ServerLevel level;

    @Inject(method = "createKeyedLevelDimensionName", at = @At("HEAD"), cancellable = true)
    private void skyweave$worldNamespace(CallbackInfoReturnable<String> result) {
        if (!Config.Server.sendLevelKeys.get()
                || !(level.getChunkSource().getGenerator() instanceof SkyIslandChunkGenerator)) return;
        try {
            var key = WorldIdentity.selectKey(level.getServer().getWorldPath(LevelResource.ROOT),
                    true, level.dimension().location().toString());
            if (key != null) result.setReturnValue(key);
        } catch (java.io.IOException invalid) {
            // Never silently fall back to a seed-derived name that could select another world's cache.
            throw new IllegalStateException("Cannot establish the Sky Archipelago DH cache namespace. "
                    + design.inherent.skyweavecompat.OptionalIntegrations.describe(design.inherent.skyweavecompat.Feature.DH_CACHE), invalid);
        }
    }
}
