package design.inherent.skyweavecompat.mixin.client;

import com.google.common.collect.ImmutableList;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import design.inherent.skyweavecompat.client.MainMenuIdentity;
import net.neoforged.neoforge.internal.BrandingControl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = BrandingControl.class, remap = false)
public abstract class MainMenuBrandingMixin {
    @WrapOperation(method = "computeBranding", at = @At(value = "INVOKE",
            target = "Lcom/google/common/collect/ImmutableList$Builder;build()Lcom/google/common/collect/ImmutableList;"),
            require = 1, allow = 1)
    private static ImmutableList<String> identity(ImmutableList.Builder<String> builder,
            Operation<ImmutableList<String>> original) {
        var nativeLines = original.call(builder);
        int beforeLast = Math.max(0, nativeLines.size() - 1);
        return ImmutableList.<String>builder().addAll(nativeLines.subList(0, beforeLast))
                .add(MainMenuIdentity.line()).addAll(nativeLines.subList(beforeLast, nativeLines.size())).build();
    }
}
