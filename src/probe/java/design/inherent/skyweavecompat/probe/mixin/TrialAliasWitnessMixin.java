package design.inherent.skyweavecompat.probe.mixin;

import design.inherent.skyweavecompat.probe.TrialAliasProbe;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets="design.inherent.skyweavecompat.encounters.TrialAliases")
public abstract class TrialAliasWitnessMixin {
    @Inject(method="prepare",at=@At("HEAD"),require=1)
    private static void before(MinecraftServer server,CallbackInfo ci) {
        if(Boolean.getBoolean("neighbors.probe.encounters")&&design.inherent.skyweavecompat.OptionalIntegrations.loaded("lithostitched"))TrialAliasProbe.capture(server);
    }
}
