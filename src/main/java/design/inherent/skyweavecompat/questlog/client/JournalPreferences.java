package design.inherent.skyweavecompat.questlog.client;

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
    public static void toggleImages() { set(ITEM_IMAGES, !ITEM_IMAGES.get()); }
    public static void toggleSymbols() { set(CONDITION_SYMBOLS, !CONDITION_SYMBOLS.get()); }
    public static void cycleNotifications() {
        var options = Notifications.values(); set(NOTIFICATIONS, options[(NOTIFICATIONS.get().ordinal() + 1) % options.length]);
    }
    private static <T> void set(ModConfigSpec.ConfigValue<T> value, T next) {
        try {
            var edits=new design.inherent.skyweavecompat.configuration.RegisteredEdits(
                    design.inherent.skyweavecompat.configuration.ConfigLocations.ownedDirectory(net.neoforged.fml.loading.FMLPaths.CONFIGDIR.get()),FILE,SPEC);
            if(!edits.nativeMatchesFile())throw new java.io.IOException("Preference file and registered values differ; let the native reload finish and review the configuration screen");
            edits.set(String.join(".",value.getPath()),next instanceof Enum<?> choice?choice.name():String.valueOf(next));
            edits.save();
            if(edits.warning()!=null)com.mojang.logging.LogUtils.getLogger().warn("Journal preferences: {}",edits.warning());
        } catch(Exception failure){throw new IllegalStateException("Cannot save journal preferences: "+failure.getMessage(),failure);}
    }
    private JournalPreferences() {}
}
