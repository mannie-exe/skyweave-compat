package design.inherent.skyweavecompat.mixin;

import design.inherent.skyweavecompat.terrain.structure.YungReplacements;
import design.inherent.skyweavecompat.terrain.structure.YungWorldPolicy;
import java.util.Map;
import org.sathrek.sky_archipelago.worldgen.generator.core.SkyIslandChunkGenerator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(value = SkyIslandChunkGenerator.class, remap = false)
public abstract class YungPolicyStateMixin implements YungWorldPolicy {
    @Unique private volatile Map<String, Boolean> skyweave$yungReplacements;
    @Override public Map<String, Boolean> skyweave$yungReplacements() {
        var snapshot = skyweave$yungReplacements;
        if (snapshot == null) synchronized (this) {
            snapshot = skyweave$yungReplacements;
            if (snapshot == null) skyweave$yungReplacements = snapshot = YungReplacements.capture();
        }
        return snapshot;
    }
}
