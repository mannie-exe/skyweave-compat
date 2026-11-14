package design.inherent.skyweavecompat.probe;

import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;

/** Native GameTest-style players and connections, confined to disposable probes. */
final class ProbePlayers {
    static ServerPlayer stationary(MinecraftServer server,String name,net.minecraft.core.BlockPos at) {
        server.setDifficulty(net.minecraft.world.Difficulty.PEACEFUL,true);
        var cookie=new CommonListenerCookie(new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(),name),0,
                net.minecraft.server.level.ClientInformation.createDefault(),false,net.neoforged.neoforge.network.connection.ConnectionType.NEOFORGE);
        var player=login(server,cookie);player.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
        player.teleportTo(at.getX()+.5,at.getY(),at.getZ()+.5);player.getAbilities().flying=true;
        server.overworld().getChunkSource().move(player);return player;
    }
    static ServerPlayer login(MinecraftServer server, CommonListenerCookie cookie) {
        var player = new ServerPlayer(server, server.overworld(), cookie.gameProfile(), cookie.clientInformation());
        return login(server,cookie,player);
    }
    static ServerPlayer login(MinecraftServer server, CommonListenerCookie cookie, ServerPlayer player) {
        var connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        net.neoforged.neoforge.network.registration.NetworkRegistry.configureMockConnection(connection);
        server.getPlayerList().placeNewPlayer(connection, player, cookie);
        return player;
    }
}
