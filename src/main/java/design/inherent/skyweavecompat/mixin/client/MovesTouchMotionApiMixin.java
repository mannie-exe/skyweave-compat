package design.inherent.skyweavecompat.mixin.client;

import java.util.UUID;
import java.util.function.Function;
import net.minecraft.client.model.geom.ModelPart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Bridges only the two released API differences; Moves retains body, arm and overlay ownership. */
@Mixin(targets = "punchymoves.client.compat.TouchMotionApi", remap = false)
public abstract class MovesTouchMotionApiMixin {
    @ModifyConstant(method = "<init>(Ljava/util/function/Function;Lpunchymoves/client/compat/TouchMotionApi$NativeCalls;)V",
            constant = @Constant(stringValue = "strm.touchnmotion.cape.CapeFollow"),
            require = 1, allow = 1)
    private String skyweave$releasedCapeApi(String original) {
        return "design.inherent.skyweavecompat.client.MovesTouchMotionCape";
    }

    @Redirect(method = "remember", at = @At(value = "INVOKE",
            target = "Lpunchymoves/client/compat/TouchMotionApi$NativeCalls;remember(Ljava/util/UUID;Ljava/util/function/Function;)V"),
            require = 1, allow = 1)
    private void skyweave$singleNativePoseRecord(@Coerce Object calls, UUID player, Function<String, ModelPart> parts) {
        // This method has already invoked 1.0.5's Posed.keep. The following legacy Drawn.keep
        // invoker targets a class absent from that release. Preserve the completed native snapshot.
    }
}
