/* SPDX-License-Identifier: Apache-2.0 */
package design.inherent.skyweavecompat.clientprobe;

import com.google.gson.JsonObject;
import com.mojang.blaze3d.systems.RenderSystem;
import com.seibel.distanthorizons.core.dependencyInjection.SingletonInjector;
import com.seibel.distanthorizons.core.render.RenderThreadTaskHandler;
import com.seibel.distanthorizons.core.wrapperInterfaces.minecraft.IMinecraftRenderWrapper;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

/** Passive frame/owner/task observations, never a task pump or a window-policy change. */
final class ClientDhRuntimeState {
    private final Map<Integer,Map<String,AtomicLong>> events=new ConcurrentHashMap<>();
    private final Map<String,AtomicLong> windowStates=new ConcurrentHashMap<>();
    private final AtomicLong lastNativeService=new AtomicLong();
    private final long observationStarted=System.nanoTime();
    boolean nativeServiceObserved() { return lastNativeService.get()!=0; }

    void event(int epoch,String name) {
        events.computeIfAbsent(epoch,ignored->new ConcurrentHashMap<>())
                .computeIfAbsent(name,ignored->new AtomicLong()).incrementAndGet();
    }

    void nativeService(int epoch,long budget) {
        event(epoch,"privateNativeServiceCalls");lastNativeService.set(System.nanoTime());
        event(epoch,budget==500_000_000L ? "nativeCleanupBudgetCalls" : "nativeFrameBudgetCalls");
        event(epoch,RenderSystem.isOnRenderThread() && GLFW.glfwGetCurrentContext()!=0
                ? "serviceOnRenderThreadWithContext" : "serviceWithoutRenderOwnerContext");
    }

    JsonObject snapshot(Minecraft mc,Integer queued) {
        var result=new JsonObject();var counters=new JsonObject();
        events.forEach((epoch,values)->{var row=new JsonObject();values.forEach((name,count)->row.addProperty(name,count.get()));counters.add(epoch.toString(),row);});
        result.add("nativeMethodEntriesByEpoch",counters);
        if(queued!=null)result.addProperty("queuedNativeRenderTasks",queued);
        else result.addProperty("nativeTaskSubsystemNotYetObserved",true);
        try {
            var queue=RenderSystem.class.getDeclaredField("recordingQueue");queue.setAccessible(true);
            result.addProperty("queuedMinecraftRenderCalls",((java.util.Queue<?>)queue.get(null)).size());
        } catch(ReflectiveOperationException | RuntimeException unavailable) {
            result.addProperty("minecraftRenderQueueObservationFailure",unavailable.toString());
        }
        long last=lastNativeService.get();if(last!=0)result.addProperty("millisSinceNativeTaskService",(System.nanoTime()-last)/1_000_000L);
        result.addProperty("actualFps",mc.getFps());result.addProperty("windowActive",mc.isWindowActive());
        result.addProperty("observationElapsedNanos",System.nanoTime()-observationStarted);
        result.addProperty("minecraftFrameCap",mc.options.framerateLimit().get());
        result.addProperty("minecraftVsync",mc.options.enableVsync().get());
        result.add("dynamicFps",dynamicFps());
        result.addProperty("noRender",mc.noRender);result.addProperty("gamePaused",mc.isPaused());
        if(queued!=null)result.addProperty("nativeDhFrameLimit",SingletonInjector.INSTANCE.get(IMinecraftRenderWrapper.class).getFrameLimit());
        if(mc.level!=null && queued!=null) {
            result.addProperty("deliveredClientChunks",mc.level.getChunkSource().getLoadedChunksCount());
            var pools=new JsonObject();
            pool(pools,"fileHandler",com.seibel.distanthorizons.core.util.threading.ThreadPoolUtil.getFileHandlerExecutor());
            pool(pools,"renderLoader",com.seibel.distanthorizons.core.util.threading.ThreadPoolUtil.getRenderLoadingExecutor());
            pool(pools,"updatePropagation",com.seibel.distanthorizons.core.util.threading.ThreadPoolUtil.getUpdatePropagatorExecutor());
            pool(pools,"worldGeneration",com.seibel.distanthorizons.core.util.threading.ThreadPoolUtil.getWorldGenExecutor());
            pool(pools,"networkCompression",com.seibel.distanthorizons.core.util.threading.ThreadPoolUtil.getNetworkCompressionExecutor());
            pool(pools,"chunkBuilder",com.seibel.distanthorizons.core.util.threading.ThreadPoolUtil.getChunkToLodBuilderExecutor());
            result.add("nativeSharedExecutors",pools);
            result.addProperty("nativeConfiguredThreads",com.seibel.distanthorizons.core.config.Config.Common.MultiThreading.numberOfThreads.get());
            try {
                var field=com.seibel.distanthorizons.core.util.threading.ThreadPoolUtil.class.getDeclaredField("taskPicker");
                field.setAccessible(true);var picker=field.get(null);
                if(picker!=null) {
                    var occupied=picker.getClass().getDeclaredField("occupiedThreadsRef");occupied.setAccessible(true);
                    result.addProperty("nativeOccupiedThreads",((java.util.concurrent.atomic.AtomicInteger)occupied.get(picker)).get());
                }
            } catch(ReflectiveOperationException | RuntimeException unavailable) {
                result.addProperty("nativePickerObservationFailure",unavailable.toString());
            }
        }
        boolean owner=RenderSystem.isOnRenderThread();result.addProperty("snapshotOnRenderThread",owner);
        if(owner && mc.getWindow()!=null) {
            long window=mc.getWindow().getWindow();
            boolean focused=GLFW.glfwGetWindowAttrib(window,GLFW.GLFW_FOCUSED)==GLFW.GLFW_TRUE;
            boolean visible=GLFW.glfwGetWindowAttrib(window,GLFW.GLFW_VISIBLE)==GLFW.GLFW_TRUE;
            boolean iconified=GLFW.glfwGetWindowAttrib(window,GLFW.GLFW_ICONIFIED)==GLFW.GLFW_TRUE;
            result.addProperty("glfwFocused",focused);result.addProperty("glfwVisible",visible);result.addProperty("glfwIconified",iconified);
            result.addProperty("sameNativeWindowContext",GLFW.glfwGetCurrentContext()==window);
            windowStates.computeIfAbsent("focused="+focused+",visible="+visible+",iconified="+iconified
                    +",noRender="+mc.noRender+",paused="+mc.isPaused(),ignored->new AtomicLong()).incrementAndGet();
        }
        var windows=new JsonObject();windowStates.forEach((name,count)->windows.addProperty(name,count.get()));
        result.add("observedWindowStateCounts",windows);
        result.addProperty("scope","Passive native method entries and sampled current window/queue state; no rendered-pixel, timing comparison, queue service, focus or policy modification");
        return result;
    }

    private static JsonObject dynamicFps() {
        var result=new JsonObject();
        boolean loaded=net.neoforged.fml.ModList.get().isLoaded("dynamic_fps");
        result.addProperty("loaded",loaded);
        if (!loaded) return result;
        try {
            var config=Class.forName("dynamic_fps.impl.config.DynamicFPSConfig");
            var instance=config.getField("INSTANCE").get(null);
            result.addProperty("enabled",(Boolean)config.getMethod("enabled").invoke(instance));
            var mod=Class.forName("dynamic_fps.impl.DynamicFPSMod");
            result.addProperty("disabled",(Boolean)mod.getMethod("isDisabled").invoke(null));
        } catch (ReflectiveOperationException | RuntimeException unavailable) {
            result.addProperty("observationFailure",unavailable.toString());
        }
        return result;
    }

    private static void pool(JsonObject output,String name,com.seibel.distanthorizons.core.util.threading.PriorityTaskPicker.Executor executor) {
        if(executor==null)return;
        var row=new JsonObject();row.addProperty("running",executor.getRunningTaskCount());
        row.addProperty("queued",executor.getQueueSize());row.addProperty("completed",executor.getCompletedTaskCount());
        row.addProperty("poolSize",executor.getPoolSize());row.addProperty("shutdown",executor.isShutdown());
        output.add(name,row);
    }
}
