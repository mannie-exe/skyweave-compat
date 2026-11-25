/* SPDX-License-Identifier: Apache-2.0 */
package design.inherent.skyweavecompat.accessories;

import com.bawnorton.mixinsquared.MixinSquaredBootstrap;
import com.bawnorton.mixinsquared.adjuster.MixinAnnotationAdjusterRegistrar;
import com.bawnorton.mixinsquared.adjuster.tools.AdjustableAnnotationNode;
import com.bawnorton.mixinsquared.api.MixinAnnotationAdjuster;
import java.util.List;
import org.objectweb.asm.tree.MethodNode;

/** Allows the layer's original break-notification replacement to cancel as intended. */
public final class CurioBreakAdjuster implements MixinAnnotationAdjuster {
    public static void register() {
        MixinSquaredBootstrap.init();
        MixinAnnotationAdjusterRegistrar.register(new CurioBreakAdjuster());
    }

    @Override public AdjustableAnnotationNode adjust(List<String> targets, String mixin, MethodNode method, AdjustableAnnotationNode annotation) {
        if (!mixin.equals("io.wispforest.accessories_compat.curios.mixin.CuriosImplMixinHooksMixin")
                || !targets.contains("top.theillusivec4.curios.mixin.CuriosImplMixinHooks")
                || !method.name.equals("broadcastAsAccessoryEvent")
                || !method.desc.equals("(Ltop/theillusivec4/curios/api/SlotContext;Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfo;)V")
                || !annotation.desc.equals("Lorg/spongepowered/asm/mixin/injection/Inject;"))
            return annotation;
        var selector = List.of("broadcastCurioBreakEvent(Ltop/theillusivec4/curios/api/SlotContext;)V");
        if (!annotation.get("method").filter(selector::equals).isPresent()
                || annotation.get("cancellable").filter(Boolean.TRUE::equals).isPresent())
            throw new IllegalStateException("Changed Accessories Curios break injector; requalify the optional repair");
        annotation.set("cancellable", true);
        return annotation;
    }
}
