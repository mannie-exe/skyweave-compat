package design.inherent.skyweavecompat.probe;

import com.google.gson.*;
import java.nio.file.*;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

/** Parks only the owned admission executor before real command requests are queued. */
final class LocatorShutdownProbe {
    private static Object state;
    private static Path output;
    private static final JsonObject report = new JsonObject();
    private static int ticks;
    private static boolean active;
    private static Object field(Object owner, String name) throws Exception {
        var field = owner.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(owner);
    }
    static void start(MinecraftServer server, Path destination) throws Exception {
        output = destination;
        var type = Class.forName("design.inherent.skyweavecompat.terrain.structure.StructureLocator");
        var states = type.getDeclaredField("servers"); states.setAccessible(true);
        state = ((Map<?, ?>)states.get(null)).get(server);
        var executor = (ThreadPoolExecutor)field(state, "requests");
        executor.execute(() -> {
            try { new CountDownLatch(1).await(); }
            catch (InterruptedException expected) { Thread.currentThread().interrupt(); }
        });
        NeoForge.EVENT_BUS.addListener(net.neoforged.bus.api.EventPriority.LOWEST, (ServerStoppedEvent event) -> {
            if (event.getServer() != server) return;
            try {
                int pending = ((AtomicInteger)field(state, "nativePending")).get();
                int reserved = ((design.inherent.reform.operation.WorkBudget)field(state, "work")).inUse();
                report.addProperty("pendingAfterStopped", pending); report.addProperty("reservedAfterStopped", reserved);
                report.addProperty("queueAfterStopped", executor.getQueue().size());
                StructureLocatorProbe.assertNoSearchTickets(server);
                report.addProperty("searchTicketsReleased", true);
                if (pending != 0 || reserved != 0 || !executor.getQueue().isEmpty())
                    throw new AssertionError("Queued admissions did not settle on actual server shutdown");
                report.addProperty("complete", true);
            } catch (Throwable failure) { report.addProperty("complete", false); report.addProperty("error", failure.toString()); }
            write();
        });
        int result = server.getCommands().getDispatcher().execute(
                "skyweavecompat structures search 131072 family=villages", server.createCommandSourceStack());
        if (result != 1) throw new AssertionError("Actual shutdown query was not admitted");
        active = true;
    }
    static void tick(MinecraftServer server) {
        if (!active) return;
        try {
            var executor = (ThreadPoolExecutor)field(state, "requests");
            int pending = ((AtomicInteger)field(state, "nativePending")).get();
            if (pending >= 2 && executor.getQueue().size() >= 2) {
                report.addProperty("queuedBeforeStop", executor.getQueue().size());
                report.addProperty("pendingBeforeStop", pending);
                report.addProperty("onlyOwnedExecutorParked", true);
                active = false; write(); server.halt(false);
            } else if (++ticks >= 400) throw new AssertionError("No actual queued admission pair observed");
        } catch (Throwable failure) {
            report.addProperty("error", failure.toString()); report.addProperty("complete", false);
            active = false; write(); server.halt(false);
        }
    }
    private static void write() {
        try { Files.writeString(output, new GsonBuilder().setPrettyPrinting().create().toJson(report) + "\n"); }
        catch (java.io.IOException error) { throw new java.io.UncheckedIOException(error); }
    }
}
