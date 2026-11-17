package design.inherent.skyweavecompat.mixin.encounters;

import design.inherent.skyweavecompat.encounters.TrialAliases;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets="dev.worldgen.lithostitched.impl.LithostitchedInternalHooks")
public abstract class VariantsTrialAliasesMixin {
    @Inject(method="onServerAboutToStart",at=@At("RETURN"),require=1)
    private static void nativeExtensionSlots(MinecraftServer server,CallbackInfo ci) {TrialAliases.prepare(server);}
}
