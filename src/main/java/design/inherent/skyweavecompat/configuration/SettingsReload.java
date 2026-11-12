package design.inherent.skyweavecompat.configuration;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/** Authored command destinations only; there is no caller-selected path or generic config mutation. */
public final class SettingsReload {
    @FunctionalInterface public interface Reload { boolean run(CommandSourceStack source) throws Exception; }
    public static void register(String destination, Reload reload) {
        if (!destination.equals("wayfinder") && !destination.equals("search")) throw new IllegalArgumentException("Unknown policy destination");
        NeoForge.EVENT_BUS.addListener((RegisterCommandsEvent event) -> event.getDispatcher().register(
                Commands.literal("skyweavecompat").then(Commands.literal("settings").requires(source -> source.hasPermission(2))
                        .then(Commands.literal("reload").then(Commands.literal(destination).executes(context -> {
                            var source = context.getSource();
                            try {
                                boolean changed = reload.run(source);
                                source.sendSuccess(() -> Component.translatable(changed ? "skyweave_compat.settings.policy_reloaded"
                                        : "skyweave_compat.settings.policy_unchanged", destination), false);
                                return 1;
                            } catch (Exception failure) {
                                source.sendFailure(Component.translatable("skyweave_compat.settings.policy_refused", destination,
                                        reason(failure)));
                                return 0;
                            }
                        }))))));
    }
    private static String reason(Exception failure) {
        String message = failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage();
        return message.length() <= 512 ? message : message.substring(0,512) + "…";
    }
    public static void requireThread(net.minecraft.server.MinecraftServer server) {
        if (!server.isSameThread()) throw new IllegalStateException("Settings must run on the owning server thread");
    }
    private SettingsReload() {}
}
