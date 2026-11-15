package design.inherent.skyweavecompat.mixin.client;

import design.inherent.skyweavecompat.client.TransparencyProfiles;
import java.util.concurrent.Executor;
import net.minecraft.client.resources.model.ModelManager;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.concurrent.CompletableFuture;

@Mixin(ModelManager.class)
abstract class TransparencyResourcesMixin {
    @org.spongepowered.asm.mixin.Unique private TransparencyProfiles.Snapshot skyweave$pendingProfiles;
    @Inject(method = "reload", at = @At("HEAD"), require = 1)
    private void skyweave$profiles(PreparableReloadListener.PreparationBarrier barrier, ResourceManager resources,
            ProfilerFiller preparation, ProfilerFiller application, Executor background, Executor game,
            CallbackInfoReturnable<CompletableFuture<Void>> callback) {
        skyweave$pendingProfiles = TransparencyProfiles.reload(resources);
    }

    @Inject(method = "apply", at = @At("HEAD"), require = 1)
    private void skyweave$publishing(org.spongepowered.asm.mixin.injection.callback.CallbackInfo callback) {
        TransparencyProfiles.beginPublication();
    }
    @Inject(method = "apply", at = @At("RETURN"), require = 1)
    private void skyweave$published(org.spongepowered.asm.mixin.injection.callback.CallbackInfo callback) {
        TransparencyProfiles.publish(skyweave$pendingProfiles);
    }
}
