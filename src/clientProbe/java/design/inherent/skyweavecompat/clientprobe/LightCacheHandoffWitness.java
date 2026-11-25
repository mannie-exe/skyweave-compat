/* SPDX-License-Identifier: Apache-2.0 */
package design.inherent.skyweavecompat.clientprobe;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import design.inherent.skyweavecompat.clientprobe.mixin.LightCacheSectionAccess;
import design.inherent.skyweavecompat.clientprobe.mixin.LightCacheMapAccess;
import java.util.HashMap;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.world.level.lighting.DataLayerStorageMap;

/** Test-only bounded trace. Reads actual native maps; never substitutes light or owns a level. */
public final class LightCacheHandoffWitness {
    private static final JsonArray rows=new JsonArray();
    private static final HashMap<Integer,String> previous=new HashMap<>();
    private static int phase=-1;
    private static final boolean ORDER_CONTROL=Boolean.getBoolean("skyweave_compat.probe.lightCache.deferInitial");
    private static final it.unimi.dsi.fastutil.longs.LongOpenHashSet deferred=new it.unimi.dsi.fastutil.longs.LongOpenHashSet();
    private static net.minecraft.world.level.lighting.LevelLightEngine deferredEngine;
    private static boolean queuedInitial,committed;
    private static int deferredCount,replayedCount;
    private LightCacheHandoffWitness() {}
    public static synchronized void phase(int value){phase=value;}
    public static int value(DataLayer data,BlockPos position) {
        return data==null?-1:data.get(position.getX()&15,position.getY()&15,position.getZ()&15);
    }
    public static DataLayer layer(DataLayerStorageMap<?> map,long section) {
        return ((LightCacheMapAccess)map).skyweave$map().get(section);
    }
    public static synchronized void record(LightCacheSectionAccess access,String event,int incoming,boolean force) {
        if(access.skyweave$layer()!=LightLayer.BLOCK || !(access.skyweave$source().getLevel() instanceof ClientLevel level))return;
        long section=SectionPos.asLong(-2,-4,-4);var pos=new BlockPos(-26,-59,-62);
        int visible=value(layer(access.skyweave$visible(),section),pos),updating=value(layer(access.skyweave$updating(),section),pos),queued=value(access.skyweave$queued().get(section),pos);
        if(event.equals("queue"))queuedInitial=true;
        if(event.equals("publish") && queuedInitial && !committed && queued==-1 && visible>=0) {
            committed=true;force=true;var engine=deferredEngine;deferredEngine=null;var positions=deferred.toLongArray();deferred.clear();
            if(engine!=null)for(long position:positions){engine.checkBlock(BlockPos.of(position));replayedCount++;}
        }
        int identity=System.identityHashCode(access);String state=phase+":"+visible+":"+updating+":"+queued;
        if(!force && state.equals(previous.get(identity)))return;
        if(previous.size()<4 || previous.containsKey(identity))previous.put(identity,state);
        if(rows.size()>=512)return;
        var row=new JsonObject();row.addProperty("event",event);row.addProperty("phase",phase);row.addProperty("levelId",System.identityHashCode(level));
        row.addProperty("storageId",identity);row.addProperty("visible",visible);row.addProperty("updating",updating);row.addProperty("queued",queued);row.addProperty("incoming",incoming);row.addProperty("thread",Thread.currentThread().getName());rows.add(row);
    }
    public static synchronized JsonArray snapshot(){return rows.deepCopy();}
    public static synchronized void packet(String event,net.minecraft.network.protocol.game.ClientboundLightUpdatePacketData data,int minimum) {
        if(rows.size()>=512)return;
        int index=-4-minimum,value=-1;var mask=data.getBlockYMask();var empty=data.getEmptyBlockYMask();
        if(index>=0 && mask.get(index)) {
            int entry=mask.get(0,index).cardinality();
            value=new DataLayer(data.getBlockUpdates().get(entry)).get(6,5,2);
        } else if(index>=0 && empty.get(index))value=0;
        var row=new JsonObject();row.addProperty("event",event);row.addProperty("phase",phase);row.addProperty("incoming",value);
        row.addProperty("minimumSection",minimum);row.addProperty("sectionIndex",index);row.addProperty("packetId",System.identityHashCode(data));
        if(event.equals("packet-build")) {
            var stack=new JsonArray();for(var frame:Thread.currentThread().getStackTrace()) {
                if(stack.size()==24)break;stack.add(frame.getClassName()+"."+frame.getMethodName()+":"+frame.getLineNumber());
            }row.add("producerStack",stack);
        }
        row.addProperty("blockSections",mask.cardinality());row.addProperty("emptyBlockSections",empty.cardinality());row.addProperty("thread",Thread.currentThread().getName());rows.add(row);
    }
    public static synchronized boolean defer(net.minecraft.world.level.lighting.LevelLightEngine engine,net.minecraft.world.level.LevelHeightAccessor view,BlockPos position) {
        if(!(view instanceof ClientLevel) || SectionPos.blockToSection(position.asLong())!=SectionPos.asLong(-2,-4,-4))return false;
        if(position.equals(new BlockPos(-27,-59,-62)) && rows.size()<512) {
            var row=new JsonObject();row.addProperty("event","check");row.addProperty("phase",phase);row.addProperty("committed",committed);row.addProperty("thread",Thread.currentThread().getName());rows.add(row);
        }
        if(!ORDER_CONTROL || committed)return false;
        if(deferredEngine!=null && deferredEngine!=engine)throw new AssertionError("Fixture crossed client engine owners");
        deferredEngine=engine;if(deferred.add(position.asLong()))deferredCount++;return true;
    }
    public static synchronized void emission(LightCacheSectionAccess access,long position,int value) {
        if(!(access.skyweave$source().getLevel() instanceof ClientLevel) || rows.size()>=512)return;
        var row=new JsonObject();row.addProperty("event","emission");row.addProperty("phase",phase);row.addProperty("value",value);
        row.addProperty("lightEnabled",access.skyweave$lightEnabled(SectionPos.blockToSection(position)));row.addProperty("thread",Thread.currentThread().getName());rows.add(row);
    }
    public static synchronized JsonObject ordering() {
        var value=new JsonObject();value.addProperty("testOnly",true);value.addProperty("enabled",ORDER_CONTROL);value.addProperty("committed",committed);
        value.addProperty("deferred",deferredCount);value.addProperty("replayed",replayedCount);value.addProperty("remaining",deferred.size());return value;
    }
}
