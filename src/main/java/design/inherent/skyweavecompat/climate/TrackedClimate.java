package design.inherent.skyweavecompat.climate;

import java.util.UUID;
import net.minecraft.resources.ResourceLocation;

/** Policy/incarnation gate. Loaded chunks own metadata; this class cannot evict live answers. */
public final class TrackedClimate {
    private Object world;
    private UUID epoch;
    private ClimateProfile policy;
    public void clear() { world=null;epoch=null;policy=null; }
    public void unloaded(Object unloaded) { if(unloaded==world)clear(); }
    public boolean accept(Object current,ResourceLocation dimension,ClimatePacket packet) {
        if(current==null || !dimension.equals(packet.dimension()))return false;
        if(packet.operation()==ClimatePacket.RESET) {
            clear();world=current;epoch=packet.epoch();policy=packet.policy();return true;
        }
        return current==world && packet.epoch().equals(epoch);
    }
    public boolean synchronizedPolicy(Object current) { return current==world && policy!=null; }
    public Integer referenceY(Object current,UUID sourceEpoch,PackedClimateChunk chunk,int x,int y,int z) {
        return synchronizedPolicy(current) && sourceEpoch.equals(epoch) ? chunk.referenceY(x,y,z,policy):null;
    }
}
