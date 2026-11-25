/* SPDX-License-Identifier: Apache-2.0 */
package design.inherent.skyweavecompat.mixin.client;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import design.inherent.skyweavecompat.lighting.InitialClientLight;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.lighting.LightEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(LevelLightEngine.class)
public abstract class InitialLightEngineMixin implements InitialClientLight.EngineOwner {
    @Shadow @Final protected LevelHeightAccessor levelHeightAccessor;
    @Unique private InitialClientLight.Gate skyweave$initialLight;
    @Override public InitialClientLight.Gate skyweave$initialLight(ClientLevel owner,ClientPacketListener listener) {
        if(levelHeightAccessor!=owner)return null;
        if(skyweave$initialLight==null)skyweave$initialLight=new InitialClientLight.Gate(owner,(LevelLightEngine)(Object)this,listener);
        return skyweave$initialLight.belongs(listener)?skyweave$initialLight:null;
    }
    @Override public InitialClientLight.Gate skyweave$existingInitialLight(){return skyweave$initialLight;}
    @WrapWithCondition(method="checkBlock",at=@At(value="INVOKE",target="Lnet/minecraft/world/level/lighting/LightEngine;checkBlock(Lnet/minecraft/core/BlockPos;)V"),require=2,allow=2)
    private boolean skyweave$check(LightEngine<?,?> engine,BlockPos position) {
        return skyweave$initialLight==null || !skyweave$initialLight.defer(position);
    }
    // Other light adapters can introduce early returns. Wrap the completed native
    // operation once, rather than depending on its transformed return count.
    @WrapMethod(method="hasLightWork()Z")
    private boolean skyweave$ready(Operation<Boolean> original) {
        boolean nativeWork=original.call();
        return nativeWork || skyweave$initialLight!=null && skyweave$initialLight.hasReady();
    }
    @WrapMethod(method="runLightUpdates()I")
    private int skyweave$committed(Operation<Integer> original) {
        int nativeWork=original.call();
        if(skyweave$initialLight!=null)skyweave$initialLight.committed();return nativeWork;
    }
}
