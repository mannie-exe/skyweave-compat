package design.inherent.skyweavecompat.accessories;

import com.bawnorton.mixinsquared.MixinSquaredBootstrap;
import com.bawnorton.mixinsquared.adjuster.MixinAnnotationAdjusterRegistrar;
import com.bawnorton.mixinsquared.adjuster.tools.AdjustableAnnotationNode;
import com.bawnorton.mixinsquared.api.MixinAnnotationAdjuster;
import java.util.List;
import org.objectweb.asm.tree.MethodNode;

/** Retains the qualified layer's binding capture at Curios 9.6's resolved builder map. */
public final class CuriosBindingAdjuster implements MixinAnnotationAdjuster {
    public static void register() {
        MixinSquaredBootstrap.init();
        MixinAnnotationAdjusterRegistrar.register(new CuriosBindingAdjuster());
    }

    @Override
    public AdjustableAnnotationNode adjust(List<String> targets, String mixin, MethodNode method, AdjustableAnnotationNode annotation) {
        if (!mixin.equals("io.wispforest.accessories_compat.curios.mixin.CuriosEntityManagerMixin")
                || !targets.contains("top.theillusivec4.curios.common.data.CuriosEntityManager")
                || !method.name.equals("test")
                || !method.desc.equals("(Ljava/util/Map;Lcom/llamalad7/mixinextras/injector/wrapoperation/Operation;)Ljava/util/Set;")
                || !annotation.desc.equals("Lcom/llamalad7/mixinextras/injector/wrapoperation/WrapOperation;"))
            return annotation;
        var previous = List.of("apply(Ljava/util/Map;Lnet/minecraft/server/packs/resources/ResourceManager;Lnet/minecraft/util/profiling/ProfilerFiller;)V");
        if (!annotation.get("method").filter(previous::equals).isPresent())
            throw new IllegalStateException("Changed Accessories Curios capture selector");
        annotation.set("method", List.of("rebuildEntitySlots(Lnet/minecraft/core/Registry;)V"));
        return annotation;
    }
}
