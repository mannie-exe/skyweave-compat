/* SPDX-License-Identifier: Apache-2.0 */
package design.inherent.skyweavecompat.probe;

import com.google.gson.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.server.level.*;
import net.minecraft.world.level.ChunkPos;

/** One after-mutation failure against actual native tickets, with an independent foreign ticket. */
public final class AdqTicketFaultProbe {
    private static final AtomicBoolean FIRED=new AtomicBoolean();
    private static final TicketType<UUID> FOREIGN=TicketType.create("skyweave_adq_fault_foreign",UUID::compareTo);
    private static final UUID KEY=new UUID(0,19);
    private static final JsonObject REPORT=new JsonObject();
    private static DistanceManager manager;private static TicketType<?> own;private static ChunkPos position;private static int level;
    private AdqTicketFaultProbe(){}
    public static boolean fired(){return FIRED.get();}
    public static void search(DistanceManager target,TicketType<?> type,ChunkPos at,int ticketLevel) {
        if(!System.getProperty("neighbors.probe.adqFault","").equals("search-ticket") || !FIRED.compareAndSet(false,true))return;
        try {
            var clazz=Class.forName("design.inherent.skyweavecompat.placement.DeliverySearch");var field=clazz.getDeclaredField("STATES");field.setAccessible(true);
            var state=((Map<?,?>)field.get(null)).get(AdqSearchProbe.server());field=state.getClass().getDeclaredField("active");field.setAccessible(true);var job=field.get(state);
            field=job.getClass().getDeclaredField("pending");field.setAccessible(true);var pending=field.get(job);field=pending.getClass().getDeclaredField("ticket");field.setAccessible(true);
            record(target,type,at,ticketLevel,field.getBoolean(pending));
        }catch(ReflectiveOperationException error){throw new IllegalStateException(error);}
        throw new IllegalStateException("Injected ADQ search ticket failure after native mutation");
    }
    public static void endpoint(Object lease,ServerChunkCache cache,TicketType<?> type,ChunkPos at,int radius) {
        if(!System.getProperty("neighbors.probe.adqFault","").equals("endpoint-ticket") || !FIRED.compareAndSet(false,true))return;
        try {
            var field=lease.getClass().getDeclaredField("chunks");field.setAccessible(true);boolean tracked=((Set<?>)field.get(lease)).contains(at);
            var access=(design.inherent.skyweavecompat.mixin.StructureSearchCacheAccess)cache;
            record(access.skyweave$searchTickets(),type,at,33-radius,tracked);
        }catch(ReflectiveOperationException error){throw new IllegalStateException(error);}
        throw new IllegalStateException("Injected ADQ endpoint ticket failure after native mutation");
    }
    private static void record(DistanceManager target,TicketType<?> type,ChunkPos at,int ticketLevel,boolean tracked) {
        manager=target;own=type;position=at;level=ticketLevel;
        manager.addTicket(FOREIGN,at,ticketLevel,KEY);
        REPORT.addProperty("mode",System.getProperty("neighbors.probe.adqFault"));REPORT.addProperty("nativeOwnTicketsAtFault",count(own));
        REPORT.addProperty("trackedBeforeFailure",tracked);REPORT.addProperty("nativeForeignTicketsAtFault",count(FOREIGN));save();
        if(count(own)!=1 || count(FOREIGN)!=1)throw new AssertionError("Fault witness lacks actual independent native tickets");
    }
    public static JsonObject observe() {
        REPORT.addProperty("nativeOwnTicketsAfterCleanup",count(own));REPORT.addProperty("nativeForeignTicketsAfterCleanup",count(FOREIGN));
        REPORT.addProperty("passed",count(own)==0 && count(FOREIGN)==1);save();return REPORT.deepCopy();
    }
    public static void releaseForeign(){if(manager!=null)manager.removeTicket(FOREIGN,position,level,KEY);}
    private static int count(TicketType<?> type) {
        if(manager==null)return 0;
        try {
            var field=DistanceManager.class.getDeclaredField("tickets");field.setAccessible(true);
            var tickets=((Map<?,?>)field.get(manager)).get(position.toLong());int count=0;
            if(tickets instanceof Iterable<?> values)for(var value:values)if(((Ticket<?>)value).getType()==type)count++;
            return count;
        }catch(ReflectiveOperationException error){throw new IllegalStateException(error);}
    }
    private static void save() {
        try{Files.writeString(Path.of(System.getProperty("neighbors.probe.output")+".fault.json"),new GsonBuilder().setPrettyPrinting().create().toJson(REPORT));}
        catch(java.io.IOException error){throw new java.io.UncheckedIOException(error);}
    }
}
