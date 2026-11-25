/* SPDX-License-Identifier: Apache-2.0 */
package design.inherent.skyweavecompat.mixin;

import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.gen.Accessor;

@Pseudo
@Mixin(targets="com.ladderstar.adq.QuestGenerator",remap=false)
public interface DeliverySearchAccess {
    @Invoker("finishGeneration")
    static void skyweave$finish(ServerLevel level,UUID recipient){throw new AssertionError();}
    @Accessor("isGenerating")
    static java.util.concurrent.atomic.AtomicBoolean skyweave$generating(){throw new AssertionError();}
}
