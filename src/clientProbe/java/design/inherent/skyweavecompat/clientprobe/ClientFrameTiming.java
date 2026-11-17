package design.inherent.skyweavecompat.clientprobe;

import com.google.gson.JsonObject;
import java.lang.management.ManagementFactory;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.lwjgl.opengl.GL;
import static org.lwjgl.opengl.GL15C.*;
import static org.lwjgl.opengl.GL33C.*;

/** Disposable measurements: asynchronous timestamps, never a render-thread query-result wait. */
final class ClientFrameTiming {
    private record Pair(int start, int end, String stage) {}
    private final ArrayDeque<Pair> pending = new ArrayDeque<>();
    private final java.util.Map<String, ArrayList<Double>> gpu = new java.util.HashMap<>();
    private final ArrayList<Double> wall = new ArrayList<>(), cpu = new ArrayList<>(), allocation = new ArrayList<>(), intervals = new ArrayList<>();
    private final java.lang.management.ThreadMXBean threads = ManagementFactory.getThreadMXBean();
    private String stage;
    private Pair active;
    private long start, cpuStart, bytesStart, since, previous;
    private int timestampBits = -1;
    ClientFrameTiming() { NeoForge.EVENT_BUS.addListener(this::before); NeoForge.EVENT_BUS.addListener(this::after); }
    void begin(String name) {
        if (stage != null) throw new IllegalStateException("Measurement already active");
        stage = name; wall.clear(); cpu.clear(); allocation.clear(); intervals.clear(); previous = 0; since = System.nanoTime();
    }
    private void before(RenderFrameEvent.Pre event) {
        collect();
        if (stage == null || Minecraft.getInstance().level == null) return;
        start = System.nanoTime(); cpuStart = threads.getCurrentThreadCpuTime(); bytesStart = allocated();
        if (previous != 0 && intervals.size() < 4096) intervals.add((start - previous) / 1e6);
        previous = start;
        var caps = GL.getCapabilities();
        if (timestampBits < 0) timestampBits = caps.OpenGL33 || caps.GL_ARB_timer_query ? glGetQueryi(GL_TIMESTAMP, GL_QUERY_COUNTER_BITS) : 0;
        if (timestampBits > 0 && pending.size() < 8) {
            active = new Pair(glGenQueries(), glGenQueries(), stage); glQueryCounter(active.start, GL_TIMESTAMP);
        }
    }
    private void after(RenderFrameEvent.Post event) {
        if (start == 0) return;
        if (wall.size() < 4096) {
            wall.add((System.nanoTime() - start) / 1e6); cpu.add((threads.getCurrentThreadCpuTime() - cpuStart) / 1e6);
            long now = allocated(); if (bytesStart >= 0 && now >= bytesStart) allocation.add((double) (now - bytesStart));
        }
        if (active != null) { glQueryCounter(active.end, GL_TIMESTAMP); pending.add(active); active = null; }
        start = 0;
    }
    private long allocated() {
        return threads instanceof com.sun.management.ThreadMXBean memory && memory.isThreadAllocatedMemorySupported()
                ? memory.getThreadAllocatedBytes(Thread.currentThread().threadId()) : -1;
    }
    private void collect() {
        while (!pending.isEmpty() && glGetQueryObjecti(pending.peek().end, GL_QUERY_RESULT_AVAILABLE) != 0) {
            var pair = pending.remove();
            double millis = (glGetQueryObjectui64(pair.end, GL_QUERY_RESULT) - glGetQueryObjectui64(pair.start, GL_QUERY_RESULT)) / 1e6;
            var values = gpu.computeIfAbsent(pair.stage, ignored -> new ArrayList<>()); if (values.size() < 4096) values.add(millis);
            glDeleteQueries(pair.start); glDeleteQueries(pair.end);
        }
    }
    JsonObject finish() {
        collect(); var result = new JsonObject(); result.addProperty("stage", stage);
        result.addProperty("seconds", (System.nanoTime() - since) / 1e9);
        result.addProperty("wallScope", "RenderFrameEvent Pre to Post, excludes frame pacing; intervals include pacing");
        result.add("frameIntervalMillis", distribution(intervals));
        result.add("wallMillis", distribution(wall)); result.add("threadCpuMillis", distribution(cpu));
        result.add("allocatedBytes", distribution(allocation));
        var gpuValues = gpu.getOrDefault(stage, new ArrayList<>());
        boolean usefulGpu = gpuValues.stream().anyMatch(value -> value > 0);
        var gpuResult = distribution(usefulGpu ? gpuValues : List.of()); gpuResult.addProperty("timestampBits", timestampBits);
        gpuResult.addProperty("usable", usefulGpu); if (!usefulGpu) gpuResult.addProperty("reason", "Native GPU timestamps unavailable or zero; no GPU timing claim");
        result.add("gpuMillis", gpuResult);
        var window = Minecraft.getInstance().getWindow(); result.addProperty("width", window.getWidth()); result.addProperty("height", window.getHeight());
        result.addProperty("pendingGpuSamples", pending.size()); stage = null; return result;
    }
    private static JsonObject distribution(List<Double> values) {
        var result = new JsonObject(); result.addProperty("samples", values.size()); if (values.isEmpty()) return result;
        var sorted = values.stream().sorted().toList();
        result.addProperty("median", sorted.get(sorted.size() / 2)); result.addProperty("p95", sorted.get(Math.min(sorted.size() - 1, (int) (sorted.size() * .95))));
        result.addProperty("p99", sorted.get(Math.min(sorted.size() - 1, (int) (sorted.size() * .99))));
        result.addProperty("mean", values.stream().mapToDouble(Double::doubleValue).average().orElseThrow()); return result;
    }
    void close() {
        for (var pair : pending) { glDeleteQueries(pair.start); glDeleteQueries(pair.end); } pending.clear();
    }
}
