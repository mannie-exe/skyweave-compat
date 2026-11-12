package design.inherent.skyweavecompat.wayfinder;

import com.google.gson.GsonBuilder;
import design.inherent.skyweavecompat.configuration.ConfigFile;
import design.inherent.skyweavecompat.configuration.ProfileJson;
import java.nio.file.*;
import java.util.List;

/** Server-owned access and work policy. Neither a support whitelist nor a distance setting. */
public record WayfinderSettings(boolean operatorsOnly, int cooldownTicks, int placementLookupHeight, int stepsPerTick,
        int concurrentRequests, int requestTimeoutTicks, List<String> blockedBlocks) {
    public static final String FILE = "skyweave_compat-wayfinder.json";
    public static final WayfinderSettings DEFAULT = new WayfinderSettings(true, 0, 16, 256, 4, 200, List.of(
            "minecraft:water", "minecraft:lava", "minecraft:bubble_column", "minecraft:fire", "minecraft:soul_fire",
            "minecraft:magma_block", "minecraft:cactus", "minecraft:sweet_berry_bush", "minecraft:wither_rose",
            "minecraft:powder_snow", "minecraft:campfire", "minecraft:soul_campfire", "minecraft:pointed_dripstone",
            "minecraft:cobweb", "minecraft:nether_portal", "minecraft:end_portal", "minecraft:end_gateway"));

    public WayfinderSettings {
        if (placementLookupHeight < 0 || placementLookupHeight > 64)
            throw new IllegalArgumentException("Placement lookup height must be between 0 and 64");
        if (cooldownTicks < 0 || cooldownTicks > 12000 || stepsPerTick < 16 || stepsPerTick > 2048
                || concurrentRequests < 1 || concurrentRequests > 8 || requestTimeoutTicks < 20 || requestTimeoutTicks > 1200)
            throw new IllegalArgumentException("Invalid Wayfinder cooldown or work limits");
        if (blockedBlocks == null || blockedBlocks.size() > 256 || blockedBlocks.stream().anyMatch(id ->
                id == null || id.length() > 256 || !id.matches("#?[a-z0-9_.-]+:[a-z0-9_./-]+"))
                || blockedBlocks.stream().distinct().count() != blockedBlocks.size())
            throw new IllegalArgumentException("Expected up to 256 unique block IDs or #tags");
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
