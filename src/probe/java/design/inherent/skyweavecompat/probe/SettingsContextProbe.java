package design.inherent.skyweavecompat.probe;

import com.google.gson.JsonObject;
import design.inherent.skyweavecompat.configuration.*;
import design.inherent.skyweavecompat.wayfinder.Wayfinder;
import design.inherent.skyweavecompat.wayfinder.WayfinderSettings;
import java.nio.file.Files;
import java.util.List;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.neoforged.fml.loading.FMLPaths;

/** Actual native player-list/permission/owner controls, confined to the marked policy fixture. */
final class SettingsContextProbe {
    private static void check(boolean value,String reason) { if (!value) throw new IllegalStateException(reason); }
    private static void refused(Throwing action,Class<? extends Exception> type) throws Exception {
        try { action.run(); } catch (Exception failure) { if (type.isInstance(failure)) return; throw failure; }
        throw new IllegalStateException("Expected "+type.getSimpleName());
    }
    private interface Throwing { void run() throws Exception; }
    static void run(MinecraftServer server,JsonObject report) throws Exception {
        var cookie=new CommonListenerCookie(new com.mojang.authlib.GameProfile(UUID.randomUUID(),"ConfigGuest"),0,
                net.minecraft.server.level.ClientInformation.createDefault(),false,net.neoforged.neoforge.network.connection.ConnectionType.NEOFORGE);
        var player=ProbePlayers.login(server,cookie); player.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
        var directory=ConfigLocations.ownedDirectory(FMLPaths.CONFIGDIR.get());
        var path=directory.resolve(WayfinderSettings.FILE); String original=Files.readString(path);
        try {
            refused(() -> ServerSettingsContext.open(player),SecurityException.class);
            server.getPlayerList().op(player.getGameProfile());
            var context=ServerSettingsContext.open(player);
            var edits=new JsonEdits(directory,JsonSettings.find(WayfinderSettings.FILE)); edits.set("stepsPerTick","144");
            var prepared=context.prepare(List.of(edits.prepare()));
            server.getPlayerList().deop(player.getGameProfile());
            var denied=prepared.commit().getFirst();
            check(denied.persistence()==SettingsCommit.Persistence.REFUSED && Files.readString(path).equals(original),"Revoked operator wrote policy");
            server.getPlayerList().op(player.getGameProfile());
            refused(prepared::commit,IllegalStateException.class);
            context=ServerSettingsContext.open(player);
            if (design.inherent.skyweavecompat.OptionalIntegrations.enabled(design.inherent.skyweavecompat.Feature.TERRAIN)) {
                var searchPath=directory.resolve("skyweave_compat-search.json"); String searchOriginal=Files.readString(searchPath);
                try {
                    var searchEdits=new JsonEdits(directory,JsonSettings.find("skyweave_compat-search.json")); searchEdits.set("timeLimitSeconds","15");
                    var searchResult=context.prepare(List.of(searchEdits.prepare())).commit().getFirst();
                    check(searchResult.persistence()==SettingsCommit.Persistence.WRITTEN && searchResult.application()==ServerSettingsContext.Application.APPLIED,
                            "Search context did not persist and publish");
                    check(design.inherent.skyweavecompat.terrain.structure.StructureLocator.readPolicy(player.createCommandSourceStack()).value().timeLimitSeconds()==15,
                            "Search consumer policy did not observe context publication");
                    report.addProperty("context_search_persistence_and_publication",true);
                } finally {
                    Files.writeString(searchPath,searchOriginal);
                    var current=design.inherent.skyweavecompat.terrain.structure.StructureLocator.readPolicy(server.createCommandSourceStack());
                    design.inherent.skyweavecompat.terrain.structure.StructureLocator.applyPolicy(server.createCommandSourceStack(),current.revision(),
                            design.inherent.skyweavecompat.terrain.structure.SearchSettings.parse(searchOriginal));
                }
                // This view's external search revision is intentionally stale after restoration; unrelated Wayfinder still qualifies.
            }
            var saved=context.prepare(List.of(edits.prepare())).commit().getFirst();
            check(saved.persistence()==SettingsCommit.Persistence.WRITTEN && saved.application()==ServerSettingsContext.Application.APPLIED,"Connected save did not apply");
            check(saved.saved()!=null && saved.saved().text().equals(Files.readString(path)),"Saved revision did not bind observed bytes");
            check(Wayfinder.readPolicy(player.createCommandSourceStack()).value().stepsPerTick()==144 && !edits.dirty(),"Actual owner/editor were not acknowledged");
            var other=ServerSettingsContext.open(player); var sameContext=context;
            refused(() -> other.retry(saved.saved()),IllegalArgumentException.class);
            edits.set("stepsPerTick","160"); var frozen=edits.prepare();
            var file=new ConfigFile(directory,WayfinderSettings.FILE);
            var injected=new SettingsCommit.Write(frozen.filename(),frozen.text(),frozen::check,() -> {
                frozen.save();
                // Fault control: a real competing publication after persistence, before this plan's application.
                var current=Wayfinder.readPolicy(player.createCommandSourceStack()); var value=current.value();
                Wayfinder.applyPolicy(player.createCommandSourceStack(),current.revision(),new WayfinderSettings(value.operatorsOnly(),value.cooldownTicks(),value.placementLookupHeight(),176,value.concurrentRequests(),value.requestTimeoutTicks(),value.blockedBlocks()));
            },() -> new SettingsCommit.Observation(SettingsCommit.Persistence.WRITTEN,file.read()),
                    () -> SettingsCommit.observe(file,frozen.text().baseline(),frozen.text().candidate()::equals));
            var failed=context.prepare(List.of(injected)).commit().getFirst();
            check(failed.persistence()==SettingsCommit.Persistence.WRITTEN && failed.application()==ServerSettingsContext.Application.FAILED && failed.saved()!=null,"Post-write refusal lost persistence");
            var pending=context.status().stream().filter(receipt -> receipt.document().equals(ServerSettingsContext.Document.WAYFINDER.id())).findFirst().orElseThrow();
            check(pending.persistence()==SettingsCommit.Persistence.SAVED && pending.application()==ServerSettingsContext.Application.PENDING,
                    "Reopened file/effective mismatch was not visible");
            String beforeRetry=Files.readString(path); var modified=Files.getLastModifiedTime(path);
            var retried=context.retry(failed.saved());
            check(retried.application()==ServerSettingsContext.Application.APPLIED && Wayfinder.readPolicy(player.createCommandSourceStack()).value().stepsPerTick()==160,"Application-only retry failed");
            check(Files.readString(path).equals(beforeRetry) && Files.getLastModifiedTime(path).equals(modified),"Retry rewrote file");
            Files.writeString(path,beforeRetry+" ");
            refused(() -> sameContext.retry(failed.saved()),ConfigFile.ChangedOnDisk.class);
            Files.writeString(path,beforeRetry);
            edits.reload(); edits.set("stepsPerTick","192"); var closed=context.prepare(List.of(edits.prepare())); context.close();
            var noWrite=closed.commit().getFirst();
            check(noWrite.persistence()==SettingsCommit.Persistence.REFUSED && Files.readString(path).equals(beforeRetry),"Closed context wrote");
            var replaced=ServerSettingsContext.open(player);
            server.getPlayerList().remove(player);
            var replacement=ProbePlayers.login(server,cookie); server.getPlayerList().op(replacement.getGameProfile());
            refused(replaced::read,IllegalStateException.class);
            replaced.close(); other.close(); sameContext.close();
            server.getPlayerList().remove(replacement);
            report.addProperty("context_native_permission_revocation_and_one_use",true);
            report.addProperty("context_observed_save_and_effective_receipt",true);
            report.addProperty("context_readonly_saved_pending_reconciliation",true);
            report.addProperty("context_post_write_failure_and_application_only_retry",true);
            report.addProperty("context_retry_file_conflict_closed_and_same_uuid_replacement",true);
            report.addProperty("context_scope","Embedded native player connections and ordinary op/deop, not an actual LAN client or two server lifecycles");
        } finally {
            Files.writeString(path,original);
            var before=Wayfinder.readPolicy(server.createCommandSourceStack());
            Wayfinder.applyPolicy(server.createCommandSourceStack(),before.revision(),WayfinderSettings.parse(original));
            if (server.getPlayerList().getPlayer(player.getUUID())!=null) server.getPlayerList().remove(server.getPlayerList().getPlayer(player.getUUID()));
        }
    }
}
