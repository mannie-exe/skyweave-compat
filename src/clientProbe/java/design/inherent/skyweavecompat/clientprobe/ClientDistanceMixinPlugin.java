package design.inherent.skyweavecompat.clientprobe;

import java.util.List;
import java.util.Set;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

public final class ClientDistanceMixinPlugin implements IMixinConfigPlugin {
    public void onLoad(String name) {}
    public String getRefMapperConfig() { return null; }
    public boolean shouldApplyMixin(String target, String mixin) {
        if (mixin.substring(mixin.lastIndexOf('.')+1).startsWith("LightCache")) return Boolean.getBoolean("skyweave_compat.probe.lightCache");
        if (mixin.endsWith(".MouseTweaksWitnessMixin")) return Boolean.getBoolean("skyweave_compat.probe.ipn")
                && net.neoforged.fml.loading.LoadingModList.get().getModFileById("mousetweaks") != null;
        if (mixin.endsWith(".EffortlessHistoryWitnessMixin")) return Boolean.getBoolean("skyweave_compat.probe.modifiers")
                && net.neoforged.fml.loading.LoadingModList.get().getModFileById("effortlessbuilding") != null;
        if (mixin.endsWith(".MovesPerspectiveWitnessMixin")) return Boolean.getBoolean("skyweave_compat.probe.modifiers")
                && net.neoforged.fml.loading.LoadingModList.get().getModFileById("punchy_moves") != null;
        if (mixin.endsWith(".ModifierPollingFixtureMixin")) return Boolean.getBoolean("skyweave_compat.probe.modifiers");
        if (mixin.endsWith(".IpnReloadWitnessMixin") || mixin.endsWith(".IpnSortWitnessMixin")) return Boolean.getBoolean("skyweave_compat.probe.ipn");
        if (mixin.endsWith(".FoliageDrawProbeMixin")) return Boolean.getBoolean("skyweave_compat.probe.foliage");
        if (mixin.substring(mixin.lastIndexOf('.') + 1).startsWith("Combat")) return Boolean.getBoolean("skyweave_compat.probe.combat");
        if (mixin.endsWith(".HandEffectDrawMixin")) return Boolean.getBoolean("skyweave_compat.probe.handEffects.trace");
        if (mixin.endsWith(".CoverageDrawProbeMixin")) return Boolean.getBoolean("skyweave_compat.probe.coverage");
        if (mixin.endsWith(".PoseSkinProbeMixin") || mixin.endsWith(".PoseCapeProbeMixin") || mixin.endsWith(".PoseCapeDrawProbeMixin")) return Boolean.getBoolean("skyweave_compat.probe.playerPoses");
        return Boolean.getBoolean("skyweave_compat.probe.distance");
    }
    public void acceptTargets(Set<String> mine, Set<String> other) {}
    public List<String> getMixins() { return null; }
    public void preApply(String target, ClassNode node, String name, IMixinInfo info) {}
    public void postApply(String target, ClassNode node, String name, IMixinInfo info) {}
}
