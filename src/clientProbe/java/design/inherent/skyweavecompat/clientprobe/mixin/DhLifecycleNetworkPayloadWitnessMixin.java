/* SPDX-License-Identifier: Apache-2.0 */
package design.inherent.skyweavecompat.clientprobe.mixin;

import com.seibel.distanthorizons.core.multiplayer.fullData.FullDataPayload;
import com.seibel.distanthorizons.core.multiplayer.fullData.FullDataPayloadReceiver;
import com.seibel.distanthorizons.core.network.messages.fullData.FullDataSplitMessage;
import com.seibel.distanthorizons.core.sql.dto.FullDataSourceV2DTO;
import design.inherent.skyweavecompat.clientprobe.ClientDhLifecycleProbe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value=FullDataPayloadReceiver.class, remap=false)
abstract class DhLifecycleNetworkPayloadWitnessMixin {
    @Inject(method="receiveChunk",at=@At("RETURN"))
    private void split(FullDataSplitMessage message, CallbackInfo ci) { ClientDhLifecycleProbe.networkSplitReceived(); }
    @Inject(method="decodeDataSource",at=@At("RETURN"))
    private void decoded(FullDataPayload payload, CallbackInfoReturnable<FullDataSourceV2DTO> ci) {
        if(ci.getReturnValue()!=null) ClientDhLifecycleProbe.networkPayloadDecoded();
    }
}
