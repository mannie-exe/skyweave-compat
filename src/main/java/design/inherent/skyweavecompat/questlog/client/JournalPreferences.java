package design.inherent.skyweavecompat.questlog.client;

import design.inherent.reform.neoforge.client.ClientRegisteredSettings;
import design.inherent.reform.operation.DocumentActions;
import design.inherent.reform.operation.OperationDescriptor;
import design.inherent.reform.owner.PolicyRefusal;
import java.util.concurrent.CompletionStage;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.common.ModConfigSpec;

/** Presentation preferences are independent from immutable optional-hook selection. */
public final class JournalPreferences {
    public static final String FILE="skyweave_compat-journal-client.toml";
    public enum Notifications implements net.neoforged.neoforge.common.TranslatableEnum {
        OFF, COMPLETION, PROGRESS_AND_COMPLETION;
        @Override public net.minecraft.network.chat.Component getTranslatedName() {
            return net.minecraft.network.chat.Component.translatable("skyweave_compat.journal.notifications." + name().toLowerCase(java.util.Locale.ROOT));
        }
    }
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.BooleanValue ITEM_IMAGES, CONDITION_SYMBOLS;
    public static final ModConfigSpec.EnumValue<Notifications> NOTIFICATIONS;
    static {
        var builder = new ModConfigSpec.Builder();
        ITEM_IMAGES = builder.comment("Show actual item and block images in journal rows.")
                .translation("skyweave_compat.journal.images").define("itemImages", true);
        CONDITION_SYMBOLS = builder.comment("Show a compact condition symbol alongside its explicit text label.")
                .translation("skyweave_compat.journal.symbols").define("conditionSymbols", true);
        NOTIFICATIONS = builder.comment("First objective progress and completion; never prerequisite unlocks.")
                .translation("skyweave_compat.journal.notifications").defineEnum("notifications", Notifications.PROGRESS_AND_COMPLETION);
        SPEC = builder.build();
    }
    private static ClientRegisteredSettings owner;
    private static boolean busy;

    public static void bind(ClientRegisteredSettings binding) {
        if (owner != null) throw new IllegalStateException("Journal preferences already have a native owner");
        owner = java.util.Objects.requireNonNull(binding);
    }

    public static boolean busy() { return busy; }
    public static CompletionStage<Void> toggleImages() { return set(ITEM_IMAGES, !ITEM_IMAGES.get()); }
    public static CompletionStage<Void> toggleSymbols() { return set(CONDITION_SYMBOLS, !CONDITION_SYMBOLS.get()); }
    public static CompletionStage<Void> cycleNotifications() {
        var options = Notifications.values(); return set(NOTIFICATIONS, options[(NOTIFICATIONS.get().ordinal() + 1) % options.length]);
    }
    private static <T> CompletionStage<Void> set(ModConfigSpec.ConfigValue<T> value, T next) {
        if (owner == null || busy) throw new IllegalStateException("Journal preferences are not ready for another save");
        var actions = owner.openActions();
        busy = true;
        var client = Minecraft.getInstance();
        var entry = OperationDescriptor.EntryPoint.GUI;
        String text = next instanceof Enum<?> choice ? choice.name() : String.valueOf(next);
        return DocumentActions.result(actions.edit(entry, String.join(".", value.getPath()), text))
                .thenComposeAsync(ignored -> DocumentActions.result(actions.save(entry)), client)
                .thenAcceptAsync(saved -> {
                    if (!saved.editorReport().valid()) throw new PolicyRefusal(saved.editorReport());
                    var receipt = saved.receipt().orElseThrow(() -> new IllegalStateException("Native save returned no receipt"));
                    if (!receipt.acknowledgesBaseline()) throw new IllegalStateException("Native save was " + receipt.persistence());
                }, client).whenCompleteAsync((ignored, failure) -> {
                    busy = false;
                    actions.close();
                    if (failure != null) com.mojang.logging.LogUtils.getLogger().warn("Cannot save journal preferences", failure);
                }, client);
    }
    private JournalPreferences() {}
}
