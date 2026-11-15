package design.inherent.skyweavecompat.mixin.client;

import design.inherent.skyweavecompat.client.MainMenuLayout;
import net.minecraft.client.gui.screens.TitleScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TitleScreen.class)
public abstract class MainMenuLayoutMixin {
    @Inject(method = "init", at = @At("RETURN"), require = 1)
    private void reserveFooter(CallbackInfo ci) {
        MainMenuLayout.reserveFooter((TitleScreen) (Object) this);
    }
}
