package design.inherent.skyweavecompat.mixin.client;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.blaze3d.platform.GlStateManager;
import design.inherent.skyweavecompat.client.IrisHandEffects;
import net.minecraft.client.renderer.ShaderInstance;
import org.spongepowered.asm.mixin.Dynamic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import static org.lwjgl.opengl.GL11C.glGetInteger;
import static org.lwjgl.opengl.GL30C.*;

/** Runs after Iris merges its program-admission methods. Other unknown programs retain Iris policy. */
@Mixin(value = ShaderInstance.class, priority = 900)
public abstract class OutlineIrisShaderMixin {
    @Unique private boolean skyweave$effectProgram, skyweave$effectOwnTarget;
    @Unique private int skyweave$effectApplyDepth;

    @WrapMethod(method = "apply")
    private void skyweave$effectApply(Operation<Void> original) {
        var shader = (ShaderInstance) (Object) this;
        skyweave$effectProgram = IrisHandEffects.program(shader);
        skyweave$effectOwnTarget = skyweave$effectProgram && IrisHandEffects.independentTarget(shader);
        int read = skyweave$effectOwnTarget ? glGetInteger(GL_READ_FRAMEBUFFER_BINDING) : -1;
        int draw = skyweave$effectOwnTarget ? glGetInteger(GL_DRAW_FRAMEBUFFER_BINDING) : -1;
        boolean success = false;
        skyweave$effectApplyDepth++;
        try { original.call(); success = true; }
        finally {
            skyweave$effectApplyDepth--;
            if (!success) skyweave$effectProgram = skyweave$effectOwnTarget = false;
            if (draw != -1) {
                GlStateManager._glBindFramebuffer(GL_READ_FRAMEBUFFER, read);
                GlStateManager._glBindFramebuffer(GL_DRAW_FRAMEBUFFER, draw);
            }
        }
    }

    @Dynamic("Iris 1.8.14's merged ShaderInstance admission method")
    @Inject(method = "iris$shouldSkipThis", at = @At("HEAD"), remap = false, cancellable = true, require = 1, allow = 1)
    private void skyweave$admitEffect(CallbackInfoReturnable<Boolean> callback) {
        if (skyweave$effectProgram && !net.irisshaders.iris.shadows.ShadowRenderer.ACTIVE) callback.setReturnValue(false);
    }

    @Dynamic("Iris 1.8.14's merged custom-target routing predicate")
    @Inject(method = "isKnownShader", at = @At("HEAD"), remap = false, cancellable = true, require = 1, allow = 1)
    private void skyweave$keepEffectTarget(CallbackInfoReturnable<Boolean> callback) {
        if (skyweave$effectOwnTarget) callback.setReturnValue(true);
    }

    @WrapMethod(method = "clear")
    private void skyweave$effectClear(Operation<Void> original) {
        try { original.call(); }
        finally {
            // Iris clears its last shader at apply HEAD, including a repeated apply of this instance.
            if (skyweave$effectApplyDepth == 0) skyweave$effectProgram = skyweave$effectOwnTarget = false;
        }
    }
}
