package design.inherent.skyweavecompat.client.combat;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Client presentation only. Values are captured at new playback, not polled from files or the server. */
public final class CombatPreferences {
    public static final String FILE = "skyweave_compat-combat-client.toml";
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.BooleanValue PUNCHY_PACING, MOVES_PACING;
    static {
        var builder = new ModConfigSpec.Builder();
        PUNCHY_PACING = builder.comment("Pace supported new Punchy attacks using captured attack speed. Does not change damage or cooldowns.")
                .translation("skyweave_compat.settings.combat.punchy_pacing").define("punchyPacing", true);
        MOVES_PACING = builder.comment("Pace supported new Moves attacks and let admitted attacks interrupt recovery. Does not change movement or gameplay.")
                .translation("skyweave_compat.settings.combat.moves_pacing").define("movesPacing", true);
        SPEC = builder.build();
    }
    public static boolean punchy() { return SPEC.isLoaded() ? PUNCHY_PACING.get() : PUNCHY_PACING.getDefault(); }
    public static boolean moves() { return SPEC.isLoaded() ? MOVES_PACING.get() : MOVES_PACING.getDefault(); }
    private CombatPreferences() {}
}
