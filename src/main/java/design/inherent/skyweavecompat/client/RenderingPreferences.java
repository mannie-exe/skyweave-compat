package design.inherent.skyweavecompat.client;

import design.inherent.skyweavecompat.Feature;
import design.inherent.skyweavecompat.OptionalIntegrations;
import design.inherent.skyweavecompat.climate.CloudPlacement;
import net.minecraft.core.BlockPos;
import net.neoforged.neoforge.common.ModConfigSpec;

/** One native CLIENT owner; already-admitted hooks read its values at their normal render boundary. */
public final class RenderingPreferences {
    public static final String FILE = "skyweave_compat-rendering-client.toml";
    // Match the pinned DimensionType codec without initializing its registry-dependent codecs in a mod constructor.
    public static final int NATIVE_HEIGHT = (1 << BlockPos.PACKED_Y_LENGTH) - 32;
    public static final int MAXIMUM_Y = (NATIVE_HEIGHT >> 1) - 1;
    public static final int MINIMUM_Y = MAXIMUM_Y - NATIVE_HEIGHT + 1;
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.EnumValue<CloudPlacement.Mode> CLOUD_MODE;
    public static final ModConfigSpec.DoubleValue CLOUD_OFFSET, CLOUD_ABSOLUTE;
    public static final ModConfigSpec.BooleanValue VOID_FOG, VOID_PLANE, TEXT_LIGHTING, NAMETAG_LIGHTING,
            TEXT_SHADOW, NAMETAG_SHADOW;
    static {
        var builder = new ModConfigSpec.Builder();
        builder.push("clouds");
        CLOUD_MODE = builder.comment("Island-world native cloud placement. Shader-owned clouds may use their own height.")
                .translation("skyweave_compat.settings.rendering.cloud_mode").defineEnum("mode", CloudPlacement.Mode.MAPPED);
        CLOUD_OFFSET = builder.comment("World-block offset for Vanilla and Mapped modes; final height stays in Minecraft's supported vertical range.")
                .translation("skyweave_compat.settings.rendering.cloud_offset")
                .defineInRange("offset", 0d, -NATIVE_HEIGHT, NATIVE_HEIGHT);
        CLOUD_ABSOLUTE = builder.comment("World Y used only in Absolute mode. A native no-cloud dimension still has no clouds.")
                .translation("skyweave_compat.settings.rendering.cloud_absolute")
                .defineInRange("absolute", 192d, MINIMUM_Y, MAXIMUM_Y);
        builder.pop().push("voidAtmosphere");
        VOID_FOG = builder.comment("Omit below-world fog darkening in island worlds, including supported generated shader recipes. Status effects remain.")
                .translation("skyweave_compat.settings.rendering.void_fog").define("excludeFogDarkening", true);
        VOID_PLANE = builder.comment("Omit the native below-world dark sky plane in island worlds. Shader sky geometry remains shader-owned.")
                .translation("skyweave_compat.settings.rendering.void_plane").define("excludeDarkPlane", true);
        builder.pop().push("worldText");
        TEXT_LIGHTING = builder.comment("Use the admitted Iris unlit policy for text displays; retain ordinary camera rendering.")
                .translation("skyweave_compat.settings.rendering.text_lighting").define("unlitTextDisplays", true);
        NAMETAG_LIGHTING = builder.comment("Use the admitted Iris unlit policy for entity nametags.")
                .translation("skyweave_compat.settings.rendering.nametag_lighting").define("unlitNametags", true);
        TEXT_SHADOW = builder.comment("Exclude text-display glyphs and backgrounds from Iris's shadow pass.")
                .translation("skyweave_compat.settings.rendering.text_shadow").define("excludeTextDisplayShadows", true);
        NAMETAG_SHADOW = builder.comment("Exclude entity nametags from Iris's shadow pass, preserving entity-body shadows.")
                .translation("skyweave_compat.settings.rendering.nametag_shadow").define("excludeNametagShadows", true);
        builder.pop();
        SPEC = builder.build();
    }
    private RenderingPreferences() {}
    public static boolean admitted() {
        return java.util.stream.Stream.of(Feature.ISLAND_CLOUDS, Feature.ISLAND_VOID, Feature.ISLAND_SHADER_ENVIRONMENT,
                Feature.TEXT_DISPLAY_LIGHTING, Feature.NAMETAG_LIGHTING, Feature.TEXT_DISPLAY_SHADOW, Feature.NAMETAG_SHADOW)
                .anyMatch(OptionalIntegrations::enabled);
    }
    public static boolean enabled(Feature feature, ModConfigSpec.BooleanValue preference) {
        return OptionalIntegrations.enabled(feature) && preference.get();
    }
    public static float cloudHeight(float nativeHeight, int minimumY, int height) {
        return CloudPlacement.height(CLOUD_MODE.get(), nativeHeight, minimumY, height,
                CLOUD_OFFSET.get(), CLOUD_ABSOLUTE.get(), MINIMUM_Y, MAXIMUM_Y);
    }
}
