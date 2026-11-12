package design.inherent.skyweavecompat.client;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Live local presentation only; never permission or server work policy. */
public final class WayfinderPreferences {
    public static final String FILE="skyweave_compat-wayfinder-client.toml";
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.BooleanValue DEBUG_MESSAGES;
    static {
        var builder=new ModConfigSpec.Builder();
        DEBUG_MESSAGES=builder.comment("Show private Wayfinder status for operators. Applies immediately; does not grant permission.")
                .translation("skyweave_compat.wayfinder.debug_messages").define("debugMessages",false);
        SPEC=builder.build();
    }
    private WayfinderPreferences() {}
}
