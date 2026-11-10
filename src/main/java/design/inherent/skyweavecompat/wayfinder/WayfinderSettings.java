package design.inherent.skyweavecompat.wayfinder;

import com.google.gson.GsonBuilder;
import design.inherent.skyweavecompat.configuration.ConfigFile;
import design.inherent.skyweavecompat.configuration.ProfileJson;
import java.nio.file.*;
import java.util.List;

/** Server-owned access and work policy. Neither a support whitelist nor a distance setting. */
public record WayfinderSettings(boolean operatorsOnly, boolean hideHotkeyPermissionMessage, int cooldownTicks, int stepsPerTick,
        int concurrentRequests, int requestTimeoutTicks, List<String> blockedBlocks) {
    public static final String FILE = "skyweave_compat-wayfinder.json";
    public static final WayfinderSettings DEFAULT = new WayfinderSettings(true, true, 40, 256, 4, 200, List.of(
            "minecraft:water", "minecraft:lava", "minecraft:bubble_column", "minecraft:fire", "minecraft:soul_fire",
            "minecraft:magma_block", "minecraft:cactus", "minecraft:sweet_berry_bush", "minecraft:wither_rose",
            "minecraft:powder_snow", "minecraft:campfire", "minecraft:soul_campfire", "minecraft:pointed_dripstone",
            "minecraft:cobweb", "minecraft:nether_portal", "minecraft:end_portal", "minecraft:end_gateway"));

    public WayfinderSettings {
        if (cooldownTicks < 0 || cooldownTicks > 12000 || stepsPerTick < 16 || stepsPerTick > 2048
                || concurrentRequests < 1 || concurrentRequests > 8 || requestTimeoutTicks < 20 || requestTimeoutTicks > 1200)
            throw new IllegalArgumentException("Invalid Wayfinder cooldown or work limits");
        if (blockedBlocks == null || blockedBlocks.size() > 256 || blockedBlocks.stream().anyMatch(id ->
                id == null || id.length() > 256 || !id.matches("#?[a-z0-9_.-]+:[a-z0-9_./-]+"))
                || blockedBlocks.stream().distinct().count() != blockedBlocks.size())
            throw new IllegalArgumentException("Expected up to 256 unique block IDs or #tags");
        blockedBlocks = List.copyOf(blockedBlocks);
    }

    public static WayfinderSettings parse(String text) { return ProfileJson.record(text, WayfinderSettings.class); }

    public static WayfinderSettings load(Path directory) {
        var path = directory.resolve(FILE);
        try {
            if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) Files.writeString(path,
                    new GsonBuilder().setPrettyPrinting().create().toJson(DEFAULT) + "\n", StandardOpenOption.CREATE_NEW);
            return parse(new ConfigFile(directory, FILE).read());
        } catch (java.io.IOException | RuntimeException failure) { throw new IllegalStateException("Cannot read " + path, failure); }
    }
}
