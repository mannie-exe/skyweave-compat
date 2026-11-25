/* SPDX-License-Identifier: Apache-2.0 */
package design.inherent.skyweavecompat.clientprobe.mixin;

import design.inherent.skyweavecompat.clientprobe.LightCacheHandoffWitness;
import java.util.BitSet;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundLightUpdatePacketData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientboundLightUpdatePacketData.class)
abstract class LightCachePacketMixin {
    @Unique private boolean skyweave$selected;
    @Unique private int skyweave$minSection;
    @Inject(method="<init>(Lnet/minecraft/world/level/ChunkPos;Lnet/minecraft/world/level/lighting/LevelLightEngine;Ljava/util/BitSet;Ljava/util/BitSet;)V",at=@At("RETURN"),require=1,allow=1)
    private void constructed(ChunkPos chunk,LevelLightEngine engine,BitSet sky,BitSet block,CallbackInfo callback) {
        skyweave$selected=chunk.x==-2 && chunk.z==-4;
        if(skyweave$selected) {
            skyweave$minSection=engine.getMinLightSection();
            LightCacheHandoffWitness.packet("packet-build",(ClientboundLightUpdatePacketData)(Object)this,skyweave$minSection);
        }
    }
    @Inject(method="write",at=@At("HEAD"),require=1,allow=1)
    private void written(FriendlyByteBuf buffer,CallbackInfo callback) {
        if(skyweave$selected)LightCacheHandoffWitness.packet("packet-write",(ClientboundLightUpdatePacketData)(Object)this,skyweave$minSection);
    }
}
