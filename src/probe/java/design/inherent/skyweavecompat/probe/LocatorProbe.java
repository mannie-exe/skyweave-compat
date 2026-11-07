package design.inherent.skyweavecompat.probe;

import com.google.gson.*;
import net.minecraft.commands.CommandSource;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.phys.Vec3;
import java.nio.file.*;
import java.util.*;

/** Actual command registration, worker continuation, permissions and observational chunk contract. */
final class LocatorProbe {
    static void run(MinecraftServer server, Path output) throws Exception {
        var messages = new ArrayList<Component>();
        CommandSource sink = new CommandSource() {
            public void sendSystemMessage(Component message) { messages.add(message); }
            public boolean acceptsSuccess() { return true; }
            public boolean acceptsFailure() { return true; }
            public boolean shouldInformAdmins() { return false; }
        };
        var source = server.createCommandSourceStack().withSource(sink).withPosition(new Vec3(.5, 200, .5));
        var dispatcher = server.getCommands().getDispatcher();
        int before = server.overworld().getChunkSource().getLoadedChunksCount();
        long began = System.nanoTime();
        try {
            dispatcher.execute("skyweavecompat islands search 8000", source.withPermission(0));
            throw new AssertionError("Unauthorized search accepted");
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException expected) { }
        dispatcher.execute("skyweavecompat islands search 8000 family=any", source);
        boolean complete = false;
        int batches = 0;
        while (!complete && batches++ < 32) {
            long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(10);
            server.managedBlock(() -> messages.stream().anyMatch(message -> key(message).equals("complete")
                    || key(message).equals("partial") || key(message).equals("failed")) || System.nanoTime() > deadline);
            if (messages.stream().anyMatch(message -> key(message).equals("failed"))) throw new AssertionError(messages.toString());
            var status = messages.stream().filter(message -> Set.of("complete", "partial").contains(key(message))).findFirst()
                    .orElseThrow(() -> new AssertionError("Metadata command did not finish a batch"));
            complete = key(status).equals("complete");
            if (!complete) {
                messages.clear(); dispatcher.execute("skyweavecompat islands more", source);
            }
        }
        if (!complete) throw new AssertionError("Small metadata search never completed");
        long matches = messages.stream().filter(message -> key(message).equals("result")).count();
        if (matches == 0) throw new AssertionError("No positive locator result");
        dispatcher.execute("skyweavecompat islands page 2", source);
        dispatcher.execute("skyweavecompat islands search 1000 archetype=classic edge=coastal", source);
        if (messages.stream().noneMatch(message -> key(message).equals("invalid"))) throw new AssertionError("Conflicting filters accepted");
        messages.clear();
        dispatcher.execute("skyweavecompat islands search 48000 biome=minecraft:the_void", source);
        waitForBatch(server, messages);
        int visited = progress(messages);
        if (messages.stream().noneMatch(message -> key(message).equals("partial"))) throw new AssertionError("Large search did not pause");
        messages.clear(); dispatcher.execute("skyweavecompat islands more", source);
        waitForBatch(server, messages);
        if (progress(messages) <= visited) throw new AssertionError("Continuation did not advance the same cursor");
        messages.clear(); dispatcher.execute("skyweavecompat islands more", source);
        // Cancel while the next batch is queued/running, not only after completion.
        dispatcher.execute("skyweavecompat islands cancel", source);
        messages.clear();
        dispatcher.execute("skyweavecompat islands more", source);
        if (messages.stream().noneMatch(message -> key(message).equals("missing"))) throw new AssertionError("Cancelled cursor remained active");
        messages.clear(); dispatcher.execute("skyweavecompat islands search 1 biome=minecraft:the_void", source);
        waitForBatch(server, messages);
        if (messages.stream().noneMatch(message -> key(message).equals("no_match"))) throw new AssertionError("Completed absence was not reported");
        dispatcher.execute("skyweavecompat islands cancel", source);
        int after = server.overworld().getChunkSource().getLoadedChunksCount();
        if (before != after) throw new AssertionError("Metadata search changed loaded chunks: " + before + " -> " + after);
        var report = new JsonObject(); report.addProperty("complete", true);
        report.addProperty("batches", batches); report.addProperty("firstPageMatches", matches);
        report.addProperty("loadedChunksBefore", before); report.addProperty("loadedChunksAfter", after);
        report.addProperty("permissionDenied", true); report.addProperty("cancelledCursorRejected", true);
        report.addProperty("continuationAdvanced", true); report.addProperty("completedNoMatch", true);
        report.addProperty("elapsedSeconds", (System.nanoTime() - began) / 1e9);
        Files.writeString(output, new GsonBuilder().setPrettyPrinting().create().toJson(report) + "\n");
    }
    private static void waitForBatch(MinecraftServer server, List<Component> messages) {
        long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(10);
        server.managedBlock(() -> messages.stream().anyMatch(message -> Set.of("complete", "partial", "failed").contains(key(message)))
                || System.nanoTime() > deadline);
        if (messages.stream().anyMatch(message -> key(message).equals("failed"))) throw new AssertionError(messages.toString());
        progress(messages);
    }
    private static int progress(List<Component> messages) {
        var status = messages.stream().filter(message -> Set.of("complete", "partial").contains(key(message))).findFirst()
                .orElseThrow(() -> new AssertionError("Metadata batch did not reply"));
        return (Integer)((TranslatableContents)status.getContents()).getArgs()[0];
    }
    private static String key(Component message) {
        if (message.getContents() instanceof TranslatableContents contents)
            return contents.getKey().replace("skyweave_compat.islands.", "");
        return message.getSiblings().stream().map(LocatorProbe::key).filter(key -> !key.isEmpty()).findFirst().orElse("");
    }
    private LocatorProbe() {}
}
