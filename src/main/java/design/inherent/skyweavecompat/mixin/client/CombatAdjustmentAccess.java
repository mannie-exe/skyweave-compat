/* SPDX-License-Identifier: Apache-2.0 */
package design.inherent.skyweavecompat.mixin.client;

import dev.kosmx.playerAnim.api.layered.modifier.AdjustmentModifier;
import java.util.Optional;
import java.util.function.Function;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = AdjustmentModifier.class, remap = false)
public interface CombatAdjustmentAccess {
    @Accessor("source") Function<String, Optional<AdjustmentModifier.PartModifier>> skyweave$source();
    @Accessor("source") void skyweave$source(Function<String, Optional<AdjustmentModifier.PartModifier>> source);
}
