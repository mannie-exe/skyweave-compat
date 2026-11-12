package design.inherent.skyweavecompat.wayfinder;

import design.inherent.skyweavecompat.configuration.SettingSpec;
import design.inherent.skyweavecompat.configuration.RecordSettings;
import com.google.gson.GsonBuilder;
import design.inherent.skyweavecompat.configuration.ConfigFile;
import design.inherent.skyweavecompat.configuration.ProfileJson;
import java.nio.file.*;
import java.util.List;

/** Server-owned access and work policy. Neither a support whitelist nor a distance setting. */
public record WayfinderSettings(
        boolean operatorsOnly,
        @SettingSpec(minimum="0", maximum="12000", unit="ticks", group="access") int cooldownTicks,
        @SettingSpec(minimum="0", maximum="64", unit="blocks", group="placement") int placementLookupHeight,
        @SettingSpec(minimum="16", maximum="2048", unit="steps_per_tick", group="work") int stepsPerTick,
        @SettingSpec(minimum="1", maximum="8", unit="requests", group="work") int concurrentRequests,
        @SettingSpec(minimum="20", maximum="1200", unit="ticks", group="work") int requestTimeoutTicks,
        @SettingSpec(maximumEntries=256, maximumLength=256, unique=true, pattern="#?[a-z0-9_.-]+:[a-z0-9_./-]+", group="hazards") List<String> blockedBlocks) {
    public static final String FILE = "skyweave_compat-wayfinder.json";
    public static final WayfinderSettings DEFAULT = new WayfinderSettings(true, 0, 16, 256, 4, 200, List.of(
            "minecraft:water", "minecraft:lava", "minecraft:bubble_column", "minecraft:fire", "minecraft:soul_fire",
            "minecraft:magma_block", "minecraft:cactus", "minecraft:sweet_berry_bush", "minecraft:wither_rose",
            "minecraft:powder_snow", "minecraft:campfire", "minecraft:soul_campfire", "minecraft:pointed_dripstone",
            "minecraft:cobweb", "minecraft:nether_portal", "minecraft:end_portal", "minecraft:end_gateway"));

    public WayfinderSettings {
        RecordSettings.validate(WayfinderSettings.class, operatorsOnly, cooldownTicks, placementLookupHeight, stepsPerTick, concurrentRequests, requestTimeoutTicks, blockedBlocks);
        blockedBlocks = List.copyOf(blockedBlocks);
    }

    public static WayfinderSettings parse(String text) {
        var value=ProfileJson.object(text);
        if(value.has("debugMessages") || value.has("hideHotkeyPermissionMessage"))
            throw new IllegalArgumentException("Wayfinder feedback is client-owned: remove retired server display fields; set debugMessages in skyweave/skyweave_compat-wayfinder-client.toml instead");
        try {return ProfileJson.record(value.toString(),WayfinderSettings.class);}
        catch(RuntimeException invalid) {
            if(invalid.getCause() instanceof IllegalArgumentException validation)throw validation;
            throw invalid;
        }
    }
    public static String normalize(String text) { return new GsonBuilder().setPrettyPrinting().create().toJson(parse(text)) + "\n"; }

    public static WayfinderSettings load(Path directory) {
        var path = directory.resolve(FILE);
        try {
            if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) Files.writeString(path,
                    new GsonBuilder().setPrettyPrinting().create().toJson(DEFAULT) + "\n", StandardOpenOption.CREATE_NEW);
            return parse(ConfigFile.read(path));
        } catch (java.io.IOException | RuntimeException failure) { throw new IllegalStateException("Cannot read " + path, failure); }
    }
}
