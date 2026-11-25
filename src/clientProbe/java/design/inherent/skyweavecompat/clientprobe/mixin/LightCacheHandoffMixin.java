/* SPDX-License-Identifier: Apache-2.0 */
package design.inherent.skyweavecompat.clientprobe.mixin;

import design.inherent.skyweavecompat.clientprobe.LightCacheHandoffWitness;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.world.level.lighting.LayerLightSectionStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LayerLightSectionStorage.class)
abstract class LightCacheHandoffMixin {
    @Inject(method="queueSectionData",at=@At("HEAD"),require=1,allow=1)
    private void queued(long section,DataLayer data,CallbackInfo callback) {
        if(section==SectionPos.asLong(-2,-4,-4))
            LightCacheHandoffWitness.record((LightCacheSectionAccess)this,"queue",data==null?-1:data.get(6,5,2),true);
    }
    @Inject(method="swapSectionMap",at=@At("RETURN"),require=1,allow=1)
    private void published(CallbackInfo callback) {
        LightCacheHandoffWitness.record((LightCacheSectionAccess)this,"publish",-1,false);
    }
    @Inject(method="setStoredLevel",at=@At("HEAD"),require=1,allow=1)
    private void changed(long position,int level,CallbackInfo callback) {
        if(position==BlockPos.asLong(-26,-59,-62))
            LightCacheHandoffWitness.record((LightCacheSectionAccess)this,"write",level,true);
    }
}
