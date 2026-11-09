package design.inherent.skyweavecompat.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.*;
import design.inherent.skyweavecompat.client.HoofprintViews;
import design.inherent.skyweavecompat.maps.MapZoomSettings;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.*;

/** Exact Connector/Mojmap 1.3.0 screen contract; all native coordinate helpers share the float scale getter. */
@Mixin(targets = "garden.hestia.hoofprint.HoofprintScreen", remap = false)
public abstract class HoofprintZoomMixin extends Screen {
    @Shadow private double centreX, centreZ;
    @Shadow private int guiScale;
    @Shadow private ResourceKey<Level> dim;
    @Shadow abstract double screenXToWorldX(double x);
    @Shadow abstract double screenYToWorldZ(double y);
    @Unique private double skyweave$scale = Double.NaN;
    @Unique private MapZoomSettings skyweave$settings = MapZoomSettings.DEFAULT;
    @Unique private HoofprintViews.View skyweave$beforeInit;
    protected HoofprintZoomMixin() { super(Component.empty()); }

    @Unique private HoofprintViews.View skyweave$view() { return new HoofprintViews.View(dim, centreX, centreZ, skyweave$scale); }
    @Unique private void skyweave$remember() {
        if (minecraft != null && dim != null && Double.isFinite(skyweave$scale))
            HoofprintViews.save(minecraft.getConnection(), skyweave$view());
    }
    @Inject(method = "init()V", at = @At("HEAD"), require = 1)
    private void skyweave$beforeResize(CallbackInfo callback) {
        skyweave$beforeInit = Double.isFinite(skyweave$scale) ? skyweave$view() : null;
    }
    @Inject(method = "init()V", at = @At("TAIL"), require = 1)
    private void skyweave$restoreView(CallbackInfo callback) {
        skyweave$settings = MapZoomSettings.load();
        var previous = skyweave$beforeInit != null ? skyweave$beforeInit : HoofprintViews.get(minecraft.getConnection());
        if (previous != null) {
            dim = previous.dimension(); centreX = previous.x(); centreZ = previous.z();
            skyweave$scale = skyweave$settings.clamp(previous.scale());
        } else skyweave$scale = skyweave$settings.clamp(skyweave$settings.zoomInitial() == 0 ? guiScale : skyweave$settings.zoomInitial());
        skyweave$beforeInit = null;
        skyweave$remember();
    }
    @Inject(method = "getScaleFactor()F", at = @At("HEAD"), cancellable = true, require = 1)
    private void skyweave$floatScale(CallbackInfoReturnable<Float> result) {
        if (Double.isFinite(skyweave$scale) && minecraft != null)
            result.setReturnValue((float)(skyweave$scale / minecraft.getWindow().getGuiScale()));
    }
    @Inject(method = "mouseScrolled(DDDD)Z", at = @At("HEAD"), cancellable = true, require = 1)
    private void skyweave$anchoredZoom(double x, double y, double horizontal, double vertical, CallbackInfoReturnable<Boolean> result) {
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(vertical) || vertical == 0
                || !Double.isFinite(skyweave$scale)) { result.setReturnValue(false); return; }
        double next = skyweave$settings.scroll(skyweave$scale, vertical);
        if (next == skyweave$scale) { result.setReturnValue(true); return; }
        double beforeX = screenXToWorldX(x), beforeZ = screenYToWorldZ(y);
        skyweave$scale = next;
        centreX += beforeX - screenXToWorldX(x);
        centreZ += beforeZ - screenYToWorldZ(y);
        skyweave$remember();
        result.setReturnValue(true);
    }
    @WrapOperation(method = "changeDim", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/Mth;clamp(DDD)D", remap = true),
            require = 1, allow = 1)
    private double skyweave$dimensionScale(double nativeScale, double minimum, double maximum, Operation<Double> original) {
        // This invocation exists only for two positive dimension ratios. Preserve its native center conversion.
        if (Double.isFinite(skyweave$scale) && nativeScale > 0 && guiScale > 0)
            skyweave$scale = skyweave$settings.clamp(skyweave$scale * nativeScale / guiScale);
        return original.call(nativeScale, minimum, maximum);
    }
    @Inject(method = {"changeDim", "onClose()V"}, at = @At("TAIL"), require = 1)
    private void skyweave$saveView(CallbackInfo callback) { skyweave$remember(); }
}
