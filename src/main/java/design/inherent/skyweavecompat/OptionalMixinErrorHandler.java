package design.inherent.skyweavecompat;

import org.spongepowered.asm.mixin.extensibility.IMixinConfig;
import org.spongepowered.asm.mixin.extensibility.IMixinErrorHandler;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

/** Add dependency context without changing Mixin's failure action or original exception. */
public final class OptionalMixinErrorHandler implements IMixinErrorHandler {
    @Override public ErrorAction onPrepareError(IMixinConfig config, Throwable cause, IMixinInfo mixin, ErrorAction action) {
        if (mixin != null) report("prepare", mixin.getClassName(), cause);
        else if (config != null && "skyweave_compat.mixins.json".equals(config.getName()))
            com.mojang.logging.LogUtils.getLogger().error("Cannot prepare Skyweave: Compatibility Patches mixins", cause);
        return action;
    }

    @Override public ErrorAction onApplyError(String target, Throwable cause, IMixinInfo mixin, ErrorAction action) {
        if (mixin != null) report("apply to " + target, mixin.getClassName(), cause);
        return action;
    }

    private static void report(String operation, String mixin, Throwable cause) {
        if (!mixin.startsWith("design.inherent.skyweavecompat.mixin.")) return;
        com.mojang.logging.LogUtils.getLogger().error("Cannot {} {}. {}", operation, mixin, DependencyDiagnostics.forMixin(mixin), cause);
    }
}
