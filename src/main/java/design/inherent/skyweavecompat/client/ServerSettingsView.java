package design.inherent.skyweavecompat.client;

import design.inherent.skyweavecompat.configuration.RecordSettings;
import design.inherent.skyweavecompat.configuration.ServerSettingsContext;
import design.inherent.skyweavecompat.configuration.SettingsCatalog;
import design.inherent.skyweavecompat.configuration.SettingsCommit;
import design.inherent.skyweavecompat.configuration.SettingsPolicy;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionException;
import java.util.function.Function;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;

/** Client projection of one real integrated-server context; never a remote-server file editor. */
final class ServerSettingsView {
    private final Minecraft client;
    private final Runnable changed;
    private boolean selected, busy;
    private long generation;
    private ServerSettingsContext context;
    private MinecraftServer server;
    private ClientPacketListener connection;
    private List<ServerSettingsContext.Receipt> receipts=List.of();
    private final Map<String,Map<String,Component>> observed=new LinkedHashMap<>();
    private Component message=Component.empty();
    private record Opened(ServerSettingsContext context,Map<String,SettingsPolicy.Snapshot<?>> values,List<ServerSettingsContext.Receipt> receipts) {}
    ServerSettingsView(Minecraft client, Runnable changed) { this.client=client; this.changed=changed; }
    boolean selected() { return selected; }
    boolean busy() { return busy; }
    boolean ready() { return selected && context!=null && !context.isClosed() && !busy; }
    boolean available() { return client.getSingleplayerServer()!=null && client.player!=null; }
    Component message() { return message; }
    List<ServerSettingsContext.Receipt> receipts() { return receipts; }
    Component observed(String filename, String field) { return observed.getOrDefault(filename,Map.of()).get(field); }
    boolean retryable() { return receipts.stream().anyMatch(receipt -> receipt.saved()!=null && !effective(receipt)); }
    private static boolean effective(ServerSettingsContext.Receipt receipt) { return receipt.application()==ServerSettingsContext.Application.APPLIED || receipt.application()==ServerSettingsContext.Application.EFFECTIVE; }
    void close() {
        if (context!=null) context.close();
        context=null; generation++; busy=false; selected=false; server=null; connection=null;
        receipts=List.of(); observed.clear(); message=Component.empty();
    }
    private boolean attached(long operation) {
        return selected && generation==operation && client.getSingleplayerServer()==server && client.getConnection()==connection;
    }
    void open() {
        close(); selected=true; server=client.getSingleplayerServer(); connection=client.getConnection();
        if (!available() || connection==null || !server.isRunning()) { message=ConfigurationOverview.text("context_unavailable"); changed.run(); return; }
        busy=true; message=ConfigurationOverview.text("context_loading");
        long operation=generation; var actor=client.player.getUUID(); var targetServer=server;
        targetServer.submit(() -> {
            var player=targetServer.getPlayerList().getPlayer(actor);
            if (player==null) throw new IllegalStateException("The requesting player is unavailable");
            var opened=ServerSettingsContext.open(player);
            try { return new Opened(opened,opened.read(),opened.status()); }
            catch (Exception failure) { opened.close(); throw new CompletionException(failure); }
        }).whenComplete((opened,error) -> client.execute(() -> {
            if (!attached(operation)) { if (opened!=null) opened.context().close(); return; }
            busy=false;
            if (error!=null) message=failure(error);
            else {
                context=opened.context(); receipts=opened.receipts(); observed.clear();
                opened.values().forEach((id,snapshot) -> observed.put(SettingsCatalog.document(id).filename(),fields(snapshot)));
                message=ConfigurationOverview.text(retryable() ? "context_pending" : "context_ready");
            }
            changed.run();
        }));
        changed.run();
    }
    private static Map<String,Component> fields(SettingsPolicy.Snapshot<?> snapshot) {
        var values=new LinkedHashMap<String,Component>();
        for (var field : RecordSettings.fields((Record)snapshot.value())) {
            Component value=field.defaultValue() instanceof java.util.Collection<?> entries ? ConfigurationOverview.text("collection_count",entries.size())
                    : field.defaultValue() instanceof Boolean flag ? Component.translatable(flag ? "options.on" : "options.off") : Component.literal(field.defaultText());
            values.put(field.key(),value);
        }
        return Map.copyOf(values);
    }
    void submit(List<SettingsCommit.Write> writes) { run(ctx -> {
        try { return ctx.prepare(writes).commit(); } catch (Exception failure) { throw new CompletionException(failure); }
    }); }
    void retry() {
        var previous=receipts;
        run(ctx -> {
        var result=new ArrayList<ServerSettingsContext.Receipt>();
        boolean stopped=false;
        for (var receipt : previous) {
            if (stopped || receipt.saved()==null || effective(receipt)) { result.add(receipt); continue; }
            try { var retried=ctx.retry(receipt.saved()); result.add(retried); if (retried.failure()!=null) stopped=true; }
            catch (Exception failure) {
                result.add(new ServerSettingsContext.Receipt(receipt.document(),receipt.persistence(),receipt.saved(),
                        ServerSettingsContext.Application.FAILED,null,failure)); stopped=true;
            }
        }
        return List.copyOf(result);
    }); }
    private void run(Function<ServerSettingsContext,List<ServerSettingsContext.Receipt>> operation) {
        if (context==null || busy || context.isClosed()) return;
        long token=generation; var owner=context; busy=true; message=ConfigurationOverview.text("context_saving");
        // Immutable receipt input must not be reread from mutable UI state on the server thread.
        owner.server().submit(() -> operation.apply(owner)).whenComplete((result,error) -> client.execute(() -> {
            if (!attached(token) || context!=owner) return;
            busy=false;
            if (error!=null) { message=failure(error); observed.clear(); }
            else {
                var merged=new LinkedHashMap<String,ServerSettingsContext.Receipt>();
                for (var receipt : receipts) merged.put(receipt.document(),receipt);
                for (var receipt : result) merged.put(receipt.document(),receipt);
                receipts=List.copyOf(merged.values());
                for (var receipt : result) if (receipt.effective()!=null)
                    observed.put(SettingsCatalog.document(receipt.document()).filename(),fields(receipt.effective()));
                message=ConfigurationOverview.text("server_result");
            }
            changed.run();
        }));
        changed.run();
    }
    private static Component failure(Throwable error) {
        while (error instanceof CompletionException && error.getCause()!=null) error=error.getCause();
        String detail=error.getMessage()==null ? error.getClass().getSimpleName() : error.getMessage();
        return ConfigurationOverview.text("context_failed",detail.length()<=512 ? detail : detail.substring(0,512));
    }
    void tick() {
        if (!selected || server==null) return;
        if (client.getSingleplayerServer()!=server || client.getConnection()!=connection || !server.isRunning() || server.isStopped()) {
            if (context!=null) context.close();
            context=null; server=null; generation++; busy=false; observed.clear();
            message=ConfigurationOverview.text("context_unavailable"); changed.run();
        }
    }
}
