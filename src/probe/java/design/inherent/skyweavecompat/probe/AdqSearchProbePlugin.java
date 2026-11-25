/* SPDX-License-Identifier: Apache-2.0 */
package design.inherent.skyweavecompat.probe;

import java.util.List;
import java.util.Set;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

public final class AdqSearchProbePlugin implements IMixinConfigPlugin {
    @Override public List<String> getMixins() {
        if(!Boolean.getBoolean("neighbors.probe.adqSearch"))return List.of();
        return Boolean.getBoolean("neighbors.probe.adqBridge")
                ? List.of("AdqLocateWitnessMixin","AdqChunkWitnessMixin","AdqBridgeWitnessMixin")
                : List.of("AdqLocateWitnessMixin","AdqChunkWitnessMixin");
    }
    @Override public void onLoad(String name) {}
    @Override public String getRefMapperConfig() { return null; }
    @Override public boolean shouldApplyMixin(String target, String mixin) { return true; }
    @Override public void acceptTargets(Set<String> own, Set<String> others) {}
    @Override public void preApply(String target, ClassNode node, String name, IMixinInfo info) {}
    @Override public void postApply(String target, ClassNode node, String name, IMixinInfo info) {}
}
