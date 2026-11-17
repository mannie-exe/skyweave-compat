package design.inherent.skyweavecompat.clientprobe.mixin;

import design.inherent.skyweavecompat.client.MovesTouchMotionCape;
import design.inherent.skyweavecompat.clientprobe.ClientPoseProbe;
import java.util.UUID;
import java.util.function.Function;
import net.minecraft.client.model.geom.ModelPart;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = MovesTouchMotionCape.class, remap = false)
abstract class PoseCapeProbeMixin {
    @Inject(method = "keep", at = @At("RETURN"), require = 1)
    private static void pose$kept(UUID id, Matrix4f before, ModelPart after, CallbackInfo ci) { ClientPoseProbe.keptCape(id); }

    @Inject(method = "carry", at = @At("RETURN"), require = 1)
    private static void pose$carried(UUID id, Function<String, ModelPart> parts, Matrix4f body, Matrix4f unused, CallbackInfo ci) { ClientPoseProbe.carried(id); }
}
