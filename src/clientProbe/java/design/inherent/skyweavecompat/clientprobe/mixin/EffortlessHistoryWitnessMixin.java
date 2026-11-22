package design.inherent.skyweavecompat.clientprobe.mixin;

import design.inherent.skyweavecompat.clientprobe.ClientModifierProbe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Count the actual native outgoing calls without suppressing their transport or server handler. */
@Mixin(targets = "neoforge.nl.requios.effortlessbuilding.network.PacketHandler", remap = false)
abstract class EffortlessHistoryWitnessMixin {
    @Inject(method = {
            "sendToServer(Lneoforge/nl/requios/effortlessbuilding/network/UndoPacket;)V",
            "sendToServer(Lneoforge/nl/requios/effortlessbuilding/network/RedoPacket;)V"},
            at = @At("HEAD"), require = 2, allow = 2)
    private static void send(@Coerce Object packet, CallbackInfo callback) {
        if (!ClientModifierProbe.controlledInput) return;
        if (packet.getClass().getSimpleName().equals("UndoPacket")) ClientModifierProbe.undoPackets++;
        else ClientModifierProbe.redoPackets++;
    }
}
