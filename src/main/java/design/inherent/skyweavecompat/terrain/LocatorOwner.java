package design.inherent.skyweavecompat.terrain;

import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

/** Discovery ownership survives a same-connection respawn, never a different login session. */
public final class LocatorOwner {
    private final UUID player;
    private final ServerGamePacketListenerImpl connection;
    private final CommandSourceStack console;

    public LocatorOwner(CommandSourceStack source) {
        if(source.getEntity() instanceof ServerPlayer current) {
            player=current.getUUID();connection=current.connection;console=null;
        } else {player=null;connection=null;console=source;}
    }
    public boolean player() { return player!=null; }
    public CommandSourceStack current(MinecraftServer server,ResourceKey<Level> dimension) {
        if(player==null)return console.hasPermission(2)
                && (dimension==null || console.getLevel().dimension().equals(dimension))?console:null;
        var current=server.getPlayerList().getPlayer(player);
        return current!=null && current.connection==connection && current.hasPermissions(2)
                && (dimension==null || current.serverLevel().dimension().equals(dimension))
                ?current.createCommandSourceStack():null;
    }
    public boolean owns(CommandSourceStack requester) {
        if(player!=null)return requester.getEntity() instanceof ServerPlayer current
                && current.getUUID().equals(player) && current.connection==connection && current.hasPermissions(2);
        return !(requester.getEntity() instanceof ServerPlayer) && requester.hasPermission(2)
                && requester.getTextName().equals(console.getTextName());
    }
    public void success(MinecraftServer server,ResourceKey<Level> dimension,Supplier<Component> message) {
        var current=current(server,dimension);if(current!=null)current.sendSuccess(message,false);
    }
    public void failure(MinecraftServer server,ResourceKey<Level> dimension,Component message) {
        var current=current(server,dimension);if(current!=null)current.sendFailure(message);
    }
}
