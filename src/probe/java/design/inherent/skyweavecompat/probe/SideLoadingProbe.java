package design.inherent.skyweavecompat.probe;

import com.google.gson.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.*;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;

/** Bounded diagnostic caller capture; never packaged in the released mod. */
final class SideLoadingProbe {
    private static final ConcurrentLinkedQueue<JsonObject> EVENTS=new ConcurrentLinkedQueue<>();
    private static final AtomicInteger COUNT=new AtomicInteger();
    private static volatile JsonObject physics;
    static void inspectPhysics(net.minecraft.server.MinecraftServer server) {
        if(!net.neoforged.fml.ModList.get().isLoaded("dynamicwaters") || !net.neoforged.fml.ModList.get().isLoaded("sable"))return;
        var result=new JsonObject();
        try {
            var type=Class.forName("com.AdamNew.dynamicwaters.SableCompat");
            var level=server.getLevel(net.minecraft.world.level.Level.END);
            int before=level.getChunkSource().getLoadedChunksCount();
            type.getMethod("tickPhysicsShips",net.minecraft.server.level.ServerLevel.class).invoke(null,level);
            var methods=new JsonObject();boolean ready=true;
            for(var name:List.of("getContainerMethod","getAllSubLevelsMethod","logicalPoseMethod","positionMethod",
                    "orientationMethod","getMassTrackerMethod","getMassMethod","ofMethod","applyLinearImpulseMethod")) {
                var field=type.getDeclaredField(name);field.setAccessible(true);var value=field.get(null);
                methods.addProperty(name,value==null?null:((java.lang.reflect.Method)value).getDeclaringClass().getName());ready&=value!=null;
            }
            result.add("methods",methods);result.addProperty("allMethodsReady",ready);
            if(ready) {
                var field=type.getDeclaredField("getContainerMethod");field.setAccessible(true);
                var container=((java.lang.reflect.Method)field.get(null)).invoke(null,level);
                field=type.getDeclaredField("getAllSubLevelsMethod");field.setAccessible(true);
                var ships=((java.lang.reflect.Method)field.get(null)).invoke(container);
                result.addProperty("returnedContainer",container.getClass().getName());
                result.addProperty("foreignSubLevels",((List<?>)ships).size());
            }
            result.addProperty("foreignChunksBefore",before);
            result.addProperty("foreignChunksAfter",level.getChunkSource().getLoadedChunksCount());
        } catch(Throwable failure) {result.addProperty("error",failure.toString());}
        physics=result;
    }
    private static void save() {
        var report=new JsonObject();report.addProperty("schema",1);report.addProperty("installed",true);
        if(physics!=null)report.add("sablePhysics",physics);
        report.add("events",new Gson().toJsonTree(List.copyOf(EVENTS)));report.addProperty("matchedEvents",COUNT.get());
        try {Files.writeString(Path.of(System.getProperty("neighbors.probe.output")+".dist.json"),report.toString(),StandardCharsets.UTF_8);}
        catch(Exception failure) {System.err.println("Side-loading diagnostic could not be saved: "+failure);}
    }
    static void install() {
        var appender=new AbstractAppender("SkyweaveProbeSideLoading",null,null,true,Property.EMPTY_ARRAY) {
            @Override public void append(LogEvent event) {
                var message=event.getMessage().getFormattedMessage();
                if(!event.getLoggerName().contains("RuntimeDistCleaner") || !message.contains("Attempted to load class"))return;
                if(COUNT.getAndIncrement()>=8)return;
                var row=new JsonObject();row.addProperty("message",message);row.addProperty("eventThread",event.getThreadName());
                row.addProperty("observerThread",Thread.currentThread().getName());
                row.add("stack",new Gson().toJsonTree(StackWalker.getInstance().walk(frames->frames.limit(80).map(StackWalker.StackFrame::toString).toList())));
                EVENTS.add(row);
            }
        };
        appender.start();var context=LoggerContext.getContext(false);
        context.getConfiguration().getRootLogger().addAppender(appender,Level.ERROR,null);context.updateLoggers();
        Runtime.getRuntime().addShutdownHook(new Thread(SideLoadingProbe::save,"skyweave-probe-side-loading"));
    }
}
