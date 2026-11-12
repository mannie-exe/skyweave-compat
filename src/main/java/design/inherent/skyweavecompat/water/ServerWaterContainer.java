package design.inherent.skyweavecompat.water;

import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import net.minecraft.server.level.ServerLevel;
import java.util.List;

/** Reflection surface containing only the two server operations used by pinned Dynamic Waters. */
public final class ServerWaterContainer {
    private final SubLevelContainer container;
    private ServerWaterContainer(SubLevelContainer container) {this.container=container;}
    public static ServerWaterContainer getContainer(ServerLevel level) {
        var nativeContainer=SubLevelContainer.getContainer(level);
        return nativeContainer==null?null:new ServerWaterContainer(nativeContainer);
    }
    public List<?> getAllSubLevels() {return container.getAllSubLevels();}
}
