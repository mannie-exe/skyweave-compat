package design.inherent.skyweavecompat.configuration;

import java.util.List;

/** Exact registered COMMON config contracts; safe during early optional-feature selection. */
public final class YungReplacementBindings {
    public record Target(String mod, String version, String file, List<String> path, String nativeType) {}
    public static final List<Target> TARGETS = List.of(
            new Target("betterdeserttemples", "1.21.1-NeoForge-4.1.5", "betterdeserttemples-neoforge-1_21.toml",
                    List.of("YUNG's Better Desert Temples", "General", "Disable Vanilla Pyramids"), "minecraft:desert_pyramid"),
            new Target("betterjungletemples", "1.21.1-NeoForge-3.1.2", "betterjungletemples-neoforge-1_21.toml",
                    List.of("YUNG's Better Jungle Temples", "General", "Disable Vanilla Jungle Temples"), "minecraft:jungle_temple"),
            new Target("bettermineshafts", "1.21.1-NeoForge-5.1.1", "bettermineshafts-neoforge-1_21.toml",
                    List.of("YUNG's Better Mineshafts", "Disable Vanilla Mineshafts"), "minecraft:mineshaft"),
            new Target("betteroceanmonuments", "1.21.1-NeoForge-4.1.2", "betteroceanmonuments-neoforge-1_21.toml",
                    List.of("YUNG's Better Ocean Monuments", "General", "Disable Vanilla Ocean Monuments"), "minecraft:ocean_monument"),
            new Target("betterwitchhuts", "1.21.1-NeoForge-4.1.1", "betterwitchhuts-neoforge-1_21.toml",
                    List.of("YUNG's Better Witch Huts", "General", "Disable Vanilla Witch Huts"), "minecraft:swamp_hut"));
    private YungReplacementBindings() {}
}
