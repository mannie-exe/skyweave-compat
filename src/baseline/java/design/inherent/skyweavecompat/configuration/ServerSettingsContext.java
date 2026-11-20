package design.inherent.skyweavecompat.configuration;

import design.inherent.skyweavecompat.Feature;
import design.inherent.skyweavecompat.OptionalIntegrations;
import design.inherent.skyweavecompat.wayfinder.Wayfinder;
import design.inherent.skyweavecompat.wayfinder.WayfinderSettings;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.neoforged.fml.loading.FMLPaths;

/** Local administrative view of admitted policy owners, bound to one authenticated player and server lifecycle. */
public final class ServerSettingsContext {
    public enum Document {
        WAYFINDER(WayfinderSettings.FILE,Feature.WAYFINDER), SEARCH("skyweave_compat-search.json",Feature.TERRAIN);
        private final String filename;
        private final Feature feature;
        Document(String filename, Feature feature) { this.filename=filename; this.feature=feature; }
        public String filename() { return filename; }
        public String id() { return SettingsCatalog.file(filename).id(); }
        public static Document file(String filename) {
            for (var value : values()) if (value.filename.equals(filename)) return value;
            throw new IllegalArgumentException("This file is not an editable server policy");
        }
        public static boolean supports(String filename) {
            for (var value : values()) if (value.filename.equals(filename)) return OptionalIntegrations.enabled(value.feature);
            return false;
        }
        SettingsPolicy.Snapshot<?> read(CommandSourceStack source) {
            if (!OptionalIntegrations.enabled(feature)) throw new IllegalStateException("Policy adaptation is unavailable");
            return switch (this) {
                case WAYFINDER -> Wayfinder.readPolicy(source);
                case SEARCH -> design.inherent.skyweavecompat.terrain.structure.StructureLocator.readPolicy(source);
            };
        }
        Object parse(String text) {
            return switch (this) {
                case WAYFINDER -> WayfinderSettings.parse(text);
                case SEARCH -> design.inherent.skyweavecompat.terrain.structure.SearchSettings.parse(text);
            };
        }
        SettingsPolicy.Snapshot<?> apply(CommandSourceStack source, SettingsPolicy.Revision expected, Object value) {
            return switch (this) {
                case WAYFINDER -> Wayfinder.applyPolicy(source,expected,(WayfinderSettings)value);
                case SEARCH -> design.inherent.skyweavecompat.terrain.structure.StructureLocator.applyPolicy(source,expected,
                        (design.inherent.skyweavecompat.terrain.structure.SearchSettings)value);
            };
        }
    }
    public enum Application { UNATTEMPTED, PENDING, EFFECTIVE, APPLIED, FAILED, UNKNOWN }
    /** Observed saved bytes bound to this context/document, not a filename or a value-hash authority token. */
    public final class SavedRevision {
        private final Document document;
        private final String text;
        private SavedRevision(Document document, String text) { this.document=document; this.text=text; }
        public String text() { return text; }
        private ServerSettingsContext owner() { return ServerSettingsContext.this; }
    }
    public record Receipt(String document, SettingsCommit.Persistence persistence, SavedRevision saved,
            Application application, SettingsPolicy.Snapshot<?> effective, Exception failure) {}
    private record Change(Document document, SettingsCommit.Write write, SettingsPolicy.Revision expected, Object value) {}
    public final class Prepared {
        private final List<Change> changes;
        private boolean consumed;
        private Prepared(List<Change> changes) { this.changes=List.copyOf(changes); }
        public List<Receipt> commit() {
            SettingsReload.requireThread(server);
            if (consumed) throw new IllegalStateException("Server settings proposal was already attempted");
            consumed=true;
            var receipts=new ArrayList<Receipt>();
            // Every owner is checked before the first effect, then immediately before its own write.
            for (var change : changes) try { check(change); change.write.check(); }
            catch (Exception failure) {
                return changes.stream().map(value -> new Receipt(value.document.id(),value==change ? SettingsCommit.Persistence.REFUSED
                        : SettingsCommit.Persistence.UNATTEMPTED,null,Application.UNATTEMPTED,null,value==change ? failure : null)).toList();
            }
            boolean stopped=false;
            for (var change : changes) {
                if (stopped) { receipts.add(new Receipt(change.document.id(),SettingsCommit.Persistence.UNATTEMPTED,null,Application.UNATTEMPTED,null,null)); continue; }
                try { check(change); }
                catch (Exception failure) {
                    receipts.add(new Receipt(change.document.id(),SettingsCommit.Persistence.REFUSED,null,Application.UNATTEMPTED,null,failure)); stopped=true; continue;
                }
                var saved=SettingsCommit.save(List.of(change.write)).receipts().getFirst();
                if (saved.state()!=SettingsCommit.Persistence.WRITTEN || saved.savedText()==null || saved.failure()!=null) {
                    var revision=saved.state()==SettingsCommit.Persistence.WRITTEN && saved.savedText()!=null ? new SavedRevision(change.document,saved.savedText()) : null;
                    receipts.add(new Receipt(change.document.id(),saved.state(),revision,revision==null ? Application.UNATTEMPTED : Application.PENDING,
                            null,saved.failure())); stopped=true; continue;
                }
                var revision=new SavedRevision(change.document,saved.savedText());
                try {
                    if (!saved.savedText().equals(change.write.text().candidate())) throw new IOException("Saved values require reconciliation before application");
                    var effective=change.document.apply(source(),change.expected,change.value);
                    baselines.put(change.document,effective);
                    receipts.add(new Receipt(change.document.id(),SettingsCommit.Persistence.WRITTEN,revision,Application.APPLIED,effective,null));
                } catch (Exception failure) {
                    receipts.add(new Receipt(change.document.id(),SettingsCommit.Persistence.WRITTEN,revision,Application.FAILED,null,failure)); stopped=true;
                }
            }
            return List.copyOf(receipts);
        }
    }
    private final MinecraftServer server;
    private final ServerPlayer actor;
    private final ServerGamePacketListenerImpl connection;
    private final AtomicBoolean closed=new AtomicBoolean();
    private final Map<Document,SettingsPolicy.Snapshot<?>> baselines=new LinkedHashMap<>();
    private ServerSettingsContext(ServerPlayer actor) {
        this.server=actor.server; this.actor=actor; connection=actor.connection;
        var source=source();
        for (var document : Document.values()) if (OptionalIntegrations.enabled(document.feature)) baselines.put(document,document.read(source));
    }
    public static ServerSettingsContext open(ServerPlayer requester) { return new ServerSettingsContext(requester); }
    public MinecraftServer server() { return server; }
    /** Invalidation can originate on the client; it is checked again on the server before effects. */
    public void close() { closed.set(true); }
    public boolean isClosed() { return closed.get(); }
    private CommandSourceStack source() {
        SettingsReload.requireThread(server);
        if (closed.get() || !server.isRunning() || server.isStopped()) throw new IllegalStateException("Server settings context has stopped");
        if (actor.connection!=connection || server.getPlayerList().getPlayer(actor.getUUID())!=actor)
            throw new IllegalStateException("The requesting player session has changed");
        if (!actor.hasPermissions(2)) throw new SecurityException("Operator permission is required");
        return actor.createCommandSourceStack();
    }
    public Map<String,SettingsPolicy.Snapshot<?>> read() {
        var source=source(); var result=new LinkedHashMap<String,SettingsPolicy.Snapshot<?>>();
        for (var document : baselines.keySet()) result.put(document.id(),document.read(source));
        return Map.copyOf(result);
    }
    /** Read-only reconciliation; observed saved bytes are not a write performed by this view. */
    public List<Receipt> status() throws IOException {
        var source=source(); var result=new ArrayList<Receipt>();
        for (var document : baselines.keySet()) {
            var text=ConfigFile.read(ConfigLocations.file(FMLPaths.CONFIGDIR.get(),document.filename));
            var value=document.parse(text); var actual=document.read(source);
            result.add(new Receipt(document.id(),SettingsCommit.Persistence.SAVED,new SavedRevision(document,text),
                    value.equals(actual.value()) ? Application.EFFECTIVE : Application.PENDING,actual,null));
        }
        return List.copyOf(result);
    }
    private void check(Change change) {
        var actual=change.document.read(source());
        if (actual.revision()!=change.expected) throw new IllegalStateException("Effective policy changed; reload this context before saving");
    }
    public Prepared prepare(List<SettingsCommit.Write> writes) throws Exception {
        source(); var changes=new ArrayList<Change>(); var seen=new java.util.HashSet<Document>();
        for (var write : List.copyOf(writes)) {
            var document=Document.file(write.filename());
            if (!seen.add(document) || !baselines.containsKey(document) || write.text()==null)
                throw new IllegalArgumentException("Unknown, duplicate or unfrozen policy submission");
            var change=new Change(document,write,baselines.get(document).revision(),document.parse(write.text().candidate()));
            check(change); write.check(); changes.add(change);
        }
        return new Prepared(changes);
    }
    /** Application-only retry re-reads the trusted owner, current revision and saved bytes; it never repeats a write. */
    public Receipt retry(SavedRevision saved) throws Exception {
        if (saved.owner()!=this) throw new IllegalArgumentException("Saved revision belongs to another settings context");
        var document=saved.document; var source=source();
        var file=new ConfigFile(ConfigLocations.ownedDirectory(FMLPaths.CONFIGDIR.get()),document.filename);
        file.check(saved.text);
        var candidate=document.parse(saved.text); var before=document.read(source);
        try {
            var applied=document.apply(source(),before.revision(),candidate); baselines.put(document,applied);
            return new Receipt(document.id(),SettingsCommit.Persistence.SAVED,saved,Application.APPLIED,applied,null);
        } catch (Exception failure) { return new Receipt(document.id(),SettingsCommit.Persistence.SAVED,saved,Application.FAILED,null,failure); }
    }
}
