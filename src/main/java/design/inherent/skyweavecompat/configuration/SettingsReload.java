package design.inherent.skyweavecompat.configuration;

import design.inherent.reform.definition.DocumentId;
import design.inherent.reform.neoforge.command.SearchRecipient;
import design.inherent.reform.neoforge.configuration.ServerPolicyCommands;
import design.inherent.reform.neoforge.configuration.ServerSettings;
import design.inherent.reform.owner.ApplicationEffects;
import design.inherent.reform.owner.CommitReceipt;
import design.inherent.reform.owner.LiveApplication;
import design.inherent.skyweavecompat.SkyweaveCompat;
import java.util.concurrent.CompletionException;
import java.util.function.Function;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

/** Authored command destinations only; there is no caller-selected path or generic config mutation. */
public final class SettingsReload {
    public static <T extends Record> void registerPolicy(String destination, String filename, Class<T> type, T defaults,
            Function<MinecraftServer, SettingsPolicy<T>> domain) {
        var definition = ReformDocuments.record(filename, type, defaults);
        NeoForge.EVENT_BUS.addListener((ServerStartedEvent event) -> {
            var owner = domain.apply(event.getServer());
            var container = ModList.get().getModContainerById(SkyweaveCompat.ID).orElseThrow();
            var store = ReformDocuments.json(FMLPaths.CONFIGDIR.get(), filename, definition);
            var application = new LiveApplication<T>(owner::matches, owner::publish);
            ServerSettings.forServer(event.getServer()).serverPolicy(container, store, ApplicationEffects.OWNER_PUBLICATION, application);
        });
        NeoForge.EVENT_BUS.addListener((RegisterCommandsEvent event) -> event.getDispatcher().register(
                Commands.literal("skyweavecompat").then(Commands.literal("settings").requires(source -> source.hasPermission(2))
                        .then(ServerPolicyCommands.branch(destination, definition))
                        .then(Commands.literal("reload").then(Commands.literal(destination)
                                .executes(context -> reload(context.getSource(), destination, definition.id(), domain)))))));
    }

    private static <T> int reload(CommandSourceStack source, String destination, DocumentId definition,
            Function<MinecraftServer, SettingsPolicy<T>> domain) {
        try {
            var server = source.getServer();
            var recipient = SearchRecipient.capture(source, 2);
            var document = ServerSettings.admittedDocument(server, definition)
                    .orElseThrow(() -> new IllegalStateException("Policy is unavailable in this server context"));
            var owner = domain.apply(server);
            var before = owner.read(() -> source.hasPermission(2)).revision();
            document.reloadFromSaved(source, 2).whenComplete((receipt, failure) -> server.execute(() ->
                    recipient.current().ifPresent(current -> {
                        if (failure != null) refuse(current, destination, reason(failure));
                        else if (receipt.application() != CommitReceipt.Application.APPLIED) {
                            String message = receipt.applicationReport().issues().stream().findFirst()
                                    .or(() -> receipt.persistenceReport().issues().stream().findFirst())
                                    .map(issue -> issue.fallback()).orElse(receipt.application().name());
                            refuse(current, destination, message);
                        } else {
                            boolean changed = before != owner.read(() -> current.hasPermission(2)).revision();
                            current.sendSuccess(() -> Component.translatable(changed ? "skyweave_compat.settings.policy_reloaded"
                                    : "skyweave_compat.settings.policy_unchanged", destination), false);
                        }
                    })));
            return 1; // Admission only. The later native reply carries the actual application outcome.
        } catch (RuntimeException failure) {
            refuse(source, destination, reason(failure));
            return 0;
        }
    }

    private static void refuse(CommandSourceStack source, String destination, String message) {
        source.sendFailure(Component.translatable("skyweave_compat.settings.policy_refused", destination,
                message.length() <= 512 ? message : message.substring(0, 512) + "…"));
    }

    private static String reason(Throwable failure) {
        while (failure instanceof CompletionException && failure.getCause() != null) failure = failure.getCause();
        return failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage();
    }

    public static void requireThread(MinecraftServer server) {
        if (!server.isSameThread()) throw new IllegalStateException("Settings must run on the owning server thread");
    }
    private SettingsReload() {}
}
