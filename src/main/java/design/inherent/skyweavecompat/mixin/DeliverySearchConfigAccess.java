/* SPDX-License-Identifier: Apache-2.0 */
package design.inherent.skyweavecompat.mixin;

import net.neoforged.neoforge.common.ModConfigSpec;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.gen.Accessor;

@Pseudo
@Mixin(targets="com.ladderstar.adq.ADQConfig",remap=false)
public interface DeliverySearchConfigAccess {
    @Accessor("QUEST_LOCATION_MODE")
    static ModConfigSpec.EnumValue<?> skyweave$locationMode(){throw new AssertionError();}
}
