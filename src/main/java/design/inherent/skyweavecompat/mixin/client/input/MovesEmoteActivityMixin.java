package design.inherent.skyweavecompat.mixin.client.input;

import design.inherent.skyweavecompat.client.input.MovesBindingActivity;
import java.util.List;
import java.util.Map;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "punchymoves.client.emote.EmoteKeybindManager", remap = false)
abstract class MovesEmoteActivityMixin {
    @Shadow @Final private static Map<String, ?> KNOWN;
    @Shadow private static List<?> active;
    @Inject(method = "install", at = @At("TAIL"), require = 1, allow = 1)
    private static void published(CallbackInfo callback) { MovesBindingActivity.publish(KNOWN.values(), active); }
}
