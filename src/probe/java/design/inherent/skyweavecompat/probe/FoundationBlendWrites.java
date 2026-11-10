package design.inherent.skyweavecompat.probe;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.state.BlockState;

/** Probe-only accounting of the native filler at the recorded wreck. */
public final class FoundationBlendWrites {
    private static final ConcurrentHashMap<String,LongAdder> WRITES=new ConcurrentHashMap<>();
    public static void record(BlockPos pos,BlockState state) {
        if(!System.getProperty("neighbors.probe.settlementReview","").equals("blend"))return;
        if(pos.getX() < -12912 || pos.getX()>-12872 || pos.getZ()<3205 || pos.getZ()>3226 || pos.getY()<450 || pos.getY()>470)return;
        WRITES.computeIfAbsent(BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString(),k->new LongAdder()).increment();
    }
    static java.util.Map<String,Long> snapshot() {
        var result=new java.util.TreeMap<String,Long>();WRITES.forEach((key,value)->result.put(key,value.longValue()));return result;
    }
}
