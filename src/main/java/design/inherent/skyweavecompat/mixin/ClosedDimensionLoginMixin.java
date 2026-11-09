package design.inherent.skyweavecompat.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import design.inherent.skyweavecompat.access.DimensionAccess;
import net.minecraft.network.Connection;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.portal.DimensionTransition;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Saved Nether logins recover through normal allowed respawn placement, preserving inventory/progress. */
@Mixin(PlayerList.class)
public abstract class ClosedDimensionLoginMixin {
    @WrapOperation(method = "placeNewPlayer", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/server/MinecraftServer;getLevel(Lnet/minecraft/resources/ResourceKey;)Lnet/minecraft/server/level/ServerLevel;"),
            require = 1, allow = 1)
    private ServerLevel skyweave$recoverLogin(MinecraftServer server, ResourceKey<Level> requested,
            Operation<ServerLevel> original, Connection connection, ServerPlayer player, CommonListenerCookie cookie) {
        if (!DimensionAccess.denied(server, requested)) return original.call(server, requested);
        var recovery = player.findRespawnPositionAndUseSpawnBlock(true, DimensionTransition.DO_NOTHING);
        player.moveTo(recovery.pos().x, recovery.pos().y, recovery.pos().z, recovery.yRot(), recovery.xRot());
        com.mojang.logging.LogUtils.getLogger().warn("Recovered {} from closed Nether login to {} at {}",
                player.getGameProfile().getName(), recovery.newLevel().dimension().location(), recovery.pos());
        return recovery.newLevel();
    }
}
