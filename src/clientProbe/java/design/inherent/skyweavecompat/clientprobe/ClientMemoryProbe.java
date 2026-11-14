package design.inherent.skyweavecompat.clientprobe;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.lang.management.ManagementFactory;
import net.minecraft.client.Minecraft;

/** Bounded observations for a marked owned client, separate from retained-size claims. */
final class ClientMemoryProbe {
    private ClientMemoryProbe() {}
    static JsonObject snapshot(Minecraft mc) throws Exception {
        var heap = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage();
        var row = new JsonObject();
        row.addProperty("heapInitialBytes", heap.getInit()); row.addProperty("heapUsedBytes", heap.getUsed());
        row.addProperty("heapCommittedBytes", heap.getCommitted()); row.addProperty("heapMaximumBytes", heap.getMax());
        row.addProperty("loadedChunks", mc.level == null ? 0 : mc.level.getChunkSource().getLoadedChunksCount());
        var collectors = new JsonArray();
        for (var gc : ManagementFactory.getGarbageCollectorMXBeans()) {
            var value = new JsonObject(); value.addProperty("name", gc.getName());
            value.addProperty("collections", gc.getCollectionCount()); value.addProperty("collectionMillis", gc.getCollectionTime());
            collectors.add(value);
        }
        row.add("collectors", collectors);
        var pools = new JsonArray();
        for (var pool : ManagementFactory.getMemoryPoolMXBeans()) {
            if (pool.getType() != java.lang.management.MemoryType.HEAP) continue;
            var collected = pool.getCollectionUsage();
            if (collected == null) continue;
            var value = new JsonObject(); value.addProperty("name", pool.getName());
            value.addProperty("usedBytesAfterLastCollection", collected.getUsed()); pools.add(value);
        }
        row.add("lastCollectionPools", pools);
        if (net.neoforged.fml.ModList.get().isLoaded("iris")) {
            var api = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
            row.addProperty("shaderPackInUse", (boolean)api.getMethod("isShaderPackInUse")
                    .invoke(api.getMethod("getInstance").invoke(null)));
        }
        return row;
    }
}
