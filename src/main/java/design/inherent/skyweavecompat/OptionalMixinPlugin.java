package design.inherent.skyweavecompat;

import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import java.util.List;
import java.util.Set;

/** Select optional mixins before Mixin resolves their target classes. */
public final class OptionalMixinPlugin implements IMixinConfigPlugin {
    @Override public List<String> getMixins() {
        var selected = new java.util.ArrayList<>(OptionalIntegrations.selection().mixins(net.neoforged.fml.loading.FMLEnvironment.dist.isClient()));
        if (OptionalIntegrations.enabled(Feature.SHADER_PATCHES)) {
            var mod = net.neoforged.fml.loading.LoadingModList.get().getMods().stream()
                .filter(m -> m.getModId().equals("euphoria_patcher")).findFirst();
            if (mod.isPresent() && mod.get().getVersion().toString().equals("1.10.5-r5.9.3-neoforge")) {
                try {
                    boolean eligible = design.inherent.skyweavecompat.shader.ShaderWorkspace.recipes(net.neoforged.fml.loading.FMLPaths.GAMEDIR.get()).stream()
                        .anyMatch(r -> r.enabled() && r.json().getAsJsonArray("steps").asList().stream()
                            .anyMatch(step -> step.getAsJsonObject().get("type").getAsString().equals("euphoria-1.10.5")));
                    if (eligible) selected.addAll(List.of("client.EuphoriaAssemblyMixin", "client.EuphoriaOwnedOutputMixin", "client.EuphoriaDetectorMixin"));
                } catch (java.io.IOException | RuntimeException failure) {
                    com.mojang.logging.LogUtils.getLogger().error("Cannot qualify Euphoria shader recipe", failure);
                }
            }
        }
        return List.copyOf(selected);
    }

    @Override public void onLoad(String mixinPackage) {
        org.spongepowered.asm.mixin.injection.selectors.TargetSelector.register(
                design.inherent.skyweavecompat.water.WaterCallbackSelector.class, "skyweave");
    }
    @Override public String getRefMapperConfig() { return null; }
    @Override public boolean shouldApplyMixin(String targetClassName, String mixinClassName) { return true; }
    @Override public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {}
    @Override public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
        if (mixinClassName.equals("design.inherent.skyweavecompat.mixin.linear.LinearBootstrapMixin"))
            design.inherent.skyweavecompat.linear.LinearBootstrapPatch.apply(targetClass);
    }
    @Override public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}
}
