package design.inherent.skyweavecompat.client;

import design.inherent.reform.definition.DocumentId;
import design.inherent.reform.minecraft.client.ClientWorkflowLayout;
import design.inherent.reform.minecraft.client.OperationScreen;
import design.inherent.reform.operation.OperationDescriptor;
import design.inherent.reform.operation.OperationScope;
import design.inherent.reform.owner.OwnerContext;
import design.inherent.reform.owner.PolicyRefusal;
import design.inherent.reform.value.ValueTypes;
import design.inherent.skyweavecompat.configuration.ConfigLocations;
import design.inherent.skyweavecompat.configuration.SettingsCatalog;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.GameShuttingDownEvent;

/** Explicit local file inspection and OS handoff; source metadata is never a path capability. */
final class SettingsFiles {
    private final OwnerContext context;
    private final OperationScope operations;
    private final List<ClientWorkflowLayout.Action<?>> actions;

    SettingsFiles() {
        var client = Minecraft.getInstance();
        context = new OwnerContext(client::execute, Runnable::run, () -> {
            if (!client.isSameThread()) throw new IllegalStateException("Settings file actions require the client thread");
        }, OwnerContext.Limits.STANDARD);
        operations = new OperationScope(context, context.access(() -> true, () -> true),
                new OperationScope.Limits(SettingsCatalog.FILES.size() + 2, 1, 8, OperationScope.Limits.STANDARD.input()));
        Path root = FMLPaths.CONFIGDIR.get();
        var entries = new ArrayList<ClientWorkflowLayout.Action<?>>();
        entries.add(action("status", text("status_files"), text("files_status_help"), () ->
                new OperationScreen.Outcome(SettingsCatalog.FILES.stream().map(file -> {
                    var path = ConfigLocations.file(root, file.filename());
                    return new OperationScreen.Detail(Component.literal(file.filename()),
                            Component.literal(path.toString()).append("\n").append(text(
                                    Files.isRegularFile(path) ? "files_present" : "not_created")));
                }).toList())));
        Path ownedDirectory = ConfigLocations.ownedDirectory(root);
        entries.add(action("folder", text("open_folder"), Component.literal(ownedDirectory.toString()), () -> {
            if (!Files.isDirectory(ownedDirectory)) throw new PolicyRefusal("settings.folder_missing", "The config folder is unavailable");
            Util.getPlatform().openFile(ownedDirectory.toFile());
            return requested(ownedDirectory);
        }));
        for (var file : SettingsCatalog.FILES) {
            var path = ConfigLocations.file(root, file.filename());
            entries.add(action("file/" + file.filename(), text("files_open_named", file.filename()),
                    Component.literal(path.toString()), () -> {
                        if (!Files.isRegularFile(path)) throw new PolicyRefusal("settings.file_missing",
                                "The file has not been created: " + path);
                        Util.getPlatform().openFile(path.toFile());
                        return requested(path);
                    }));
        }
        operations.freeze();
        actions = List.copyOf(entries);
        NeoForge.EVENT_BUS.addListener((GameShuttingDownEvent event) -> context.close());
    }

    List<ClientWorkflowLayout.Action<?>> actions() { return actions; }

    private ClientWorkflowLayout.Action<OperationScreen.Outcome> action(String key, Component title,
            Component help, Supplier<OperationScreen.Outcome> invoke) {
        var descriptor = new OperationDescriptor<Boolean, OperationScreen.Outcome>(
                new DocumentId("skyweave_compat", "settings/files/" + key), ValueTypes.bool(),
                OwnerContext.Right.READ, Set.of(OperationDescriptor.Effect.INSPECT), Set.of(OperationDescriptor.EntryPoint.GUI));
        operations.register(descriptor, (execution, ignored) -> context.onOwner(() -> {
            execution.checkpoint();
            return invoke.get();
        }));
        return new ClientWorkflowLayout.Action<>(descriptor.id(), title, help,
                () -> operations.hasActiveOperations() ? Optional.of(text("files_busy")) : Optional.empty(),
                () -> operations.submit(descriptor, OperationDescriptor.EntryPoint.GUI, true), context::isOpen,
                result -> result);
    }

    private static OperationScreen.Outcome requested(Path path) {
        return new OperationScreen.Outcome(text("files_open_requested"),
                List.of(new OperationScreen.Detail(text("open_file"), Component.literal(path.toString()))));
    }

    private static Component text(String key, Object... arguments) {
        return Component.translatable("skyweave_compat.settings." + key, arguments);
    }
}
