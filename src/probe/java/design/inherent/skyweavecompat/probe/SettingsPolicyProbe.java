package design.inherent.skyweavecompat.probe;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import design.inherent.skyweavecompat.Feature;
import design.inherent.skyweavecompat.OptionalIntegrations;
import design.inherent.skyweavecompat.configuration.ConfigLocations;
import design.inherent.skyweavecompat.wayfinder.Wayfinder;
import design.inherent.skyweavecompat.wayfinder.WayfinderSettings;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.MinecraftServer;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Explicit disposable native command/owner controls; does not simulate in-flight lease qualification. */
final class SettingsPolicyProbe {
    private boolean ready, checked;
    SettingsPolicyProbe() {
        NeoForge.EVENT_BUS.addListener((ServerStartedEvent event) -> ready = true);
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post event) -> {
            if (!ready || checked) return;
            checked = true;
            var report = new JsonObject();
            try {
                var root = FMLPaths.GAMEDIR.get().toRealPath();
                var marker = root.resolve("settings-policy-fixture.json");
                if (!Files.isRegularFile(marker) || !Files.readString(marker).contains("mc-modpacks/config-system"))
                    throw new IllegalStateException("Missing owned policy fixture marker");
                run(event.getServer(), report);
                report.addProperty("complete",true);
            } catch (Throwable failure) { report.addProperty("complete",false); report.addProperty("failure",failure.toString()); failure.printStackTrace(); }
            try { Files.writeString(Path.of(System.getProperty("neighbors.probe.output")),new GsonBuilder().setPrettyPrinting().create().toJson(report)); }
            catch (Exception failure) { throw new IllegalStateException(failure); }
            event.getServer().halt(false);
        });
    }
    private static void check(boolean value, String reason) { if (!value) throw new IllegalStateException(reason); }
    private static void refused(Runnable run, Class<? extends RuntimeException> type) {
        try { run.run(); } catch (RuntimeException failure) { if (type.isInstance(failure)) return; throw failure; }
        throw new IllegalStateException("Expected " + type.getSimpleName());
    }
    private static int command(CommandSourceStack source, String destination) throws Exception {
        return source.getServer().getCommands().getDispatcher().execute("skyweavecompat settings reload " + destination,source);
    }
    private static void run(MinecraftServer server, JsonObject report) throws Exception {
        boolean terrain=OptionalIntegrations.enabled(Feature.TERRAIN);
        check(terrain==Boolean.getBoolean("neighbors.probe.settingsTerrain"),"Unexpected terrain admission; audit launch classpaths");
        report.add("loaded_mods",new GsonBuilder().create().toJsonTree(net.neoforged.fml.ModList.get().getMods().stream()
                .map(mod -> mod.getModId()+"@"+mod.getVersion()).sorted().toList()));
        check(!net.neoforged.fml.ModList.get().isLoaded("sable") && !net.neoforged.fml.ModList.get().isLoaded("skyvillages"),
                "Unexpected optional runtime on native fixture classpath");
        var source=server.createCommandSourceStack(); var denied=source.withPermission(0);
        var before=Wayfinder.readPolicy(source);
        var root=ConfigLocations.ownedDirectory(FMLPaths.CONFIGDIR.get());
        var file=root.resolve(WayfinderSettings.FILE); String original=Files.readString(file);
        var candidate=new WayfinderSettings(true,12,8,128,2,80,before.value().blockedBlocks());
        int chunks=server.overworld().getChunkSource().getLoadedChunksCount();
        Files.writeString(file,new GsonBuilder().setPrettyPrinting().create().toJson(candidate));
        check(command(source,"wayfinder")==1,"Native reload failed");
        var changed=Wayfinder.readPolicy(source);
        check(changed.value().equals(candidate) && changed.revision()!=before.revision(),"Policy not published");
        check(before.value().equals(WayfinderSettings.parse(original)),"Existing snapshot changed");
        check(command(source,"wayfinder")==1 && Wayfinder.readPolicy(source).revision()==changed.revision(),"No-op changed revision");
        refused(() -> Wayfinder.readPolicy(denied),SecurityException.class);
        refused(() -> Wayfinder.applyPolicy(denied,changed.revision(),before.value()),SecurityException.class);
        boolean blocked=false;
        try { command(denied,"wayfinder"); } catch (com.mojang.brigadier.exceptions.CommandSyntaxException expected) { blocked=true; }
        check(blocked,"Non-operator command executed");
        refused(() -> Wayfinder.applyPolicy(source,before.revision(),before.value()),IllegalStateException.class);
        Files.writeString(file,"{\"operatorsOnly\":false}");
        check(command(source,"wayfinder")==0 && Wayfinder.readPolicy(source).revision()==changed.revision(),"Malformed reload mutated policy");
        check(Files.readString(file).equals("{\"operatorsOnly\":false}"),"Refused reload rewrote file");
        Files.writeString(file,original); check(command(source,"wayfinder")==1,"Restore reload failed");
        report.addProperty("wayfinder_reload_noop_permission_stale_and_invalid",true);
        var threadFailure=new java.util.concurrent.atomic.AtomicBoolean();
        var thread=new Thread(() -> { try {Wayfinder.readPolicy(source);} catch (IllegalStateException expected) {threadFailure.set(true);} });
        thread.start(); thread.join(); check(threadFailure.get(),"Foreign-thread policy read accepted");
        report.addProperty("actual_server_thread_guard",true);
        if (OptionalIntegrations.enabled(Feature.TERRAIN)) search(source,root,report);
        else report.addProperty("search","not selected without terrain; no search policy resolution requested");
        check(chunks==server.overworld().getChunkSource().getLoadedChunksCount(),"Policy operations loaded chunks");
        report.addProperty("loaded_chunk_count_unchanged",true);
        report.addProperty("limits","No active job, lease-drain, integrated/LAN, two-server-process-lifecycle, UI Save or full-pack claim");
    }
    private static void search(CommandSourceStack source, Path root, JsonObject report) throws Exception {
        var before=design.inherent.skyweavecompat.terrain.structure.StructureLocator.readPolicy(source);
        var file=root.resolve(design.inherent.skyweavecompat.terrain.structure.SearchSettings.FILE); String original=Files.readString(file);
        var value=new design.inherent.skyweavecompat.terrain.structure.SearchSettings(128,5,1,1);
        Files.writeString(file,new GsonBuilder().setPrettyPrinting().create().toJson(value));
        check(command(source,"search")==1,"Search reload failed");
        var changed=design.inherent.skyweavecompat.terrain.structure.StructureLocator.readPolicy(source);
        check(changed.value().equals(value) && changed.revision()!=before.revision(),"Search policy not applied");
        check(command(source,"search")==1 && changed.revision()==design.inherent.skyweavecompat.terrain.structure.StructureLocator.readPolicy(source).revision(),"Search no-op changed revision");
        refused(() -> design.inherent.skyweavecompat.terrain.structure.StructureLocator.readPolicy(source.withPermission(0)),SecurityException.class);
        Files.writeString(file,"{}"); check(command(source,"search")==0,"Invalid search reload accepted");
        check(changed.revision()==design.inherent.skyweavecompat.terrain.structure.StructureLocator.readPolicy(source).revision(),"Invalid search changed policy");
        Files.writeString(file,original); check(command(source,"search")==1,"Search restore failed");
        report.addProperty("search_reload_noop_permission_and_invalid",true);
    }
}
