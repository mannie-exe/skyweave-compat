package design.inherent.skyweavecompat.terrain;

import design.inherent.skyweavecompat.configuration.ConfigFile;
import com.google.gson.*;
import java.io.IOException;
import java.nio.file.*;
import org.sathrek.sky_archipelago.config.SkyIslandSettings;
import org.sathrek.sky_archipelago.config.settings.TerrainSettings;

/** Installation defaults, captured immutably by each field and checked against the saved world. */
public final class GenerationConfiguration {
    public static final String FILE = "skyweave_compat-generation.json";
    private static volatile GenerationProfile profile = GenerationProfile.INITIAL;
    private static final Gson JSON = new GsonBuilder().setPrettyPrinting().create();
    private GenerationConfiguration() {}

    public static GenerationProfile profile() { return profile; }

    /** The preset retains biome, archetype and structure rules; this profile owns geometry controls. */
    public static SkyIslandSettings resolve(SkyIslandSettings embedded) {
        var p = profile();
        var t = embedded.terrain();
        var a = t.archetypes(); var ocean = OceanConfiguration.profile();
        var archetypes = new org.sathrek.sky_archipelago.config.settings.ArchetypeSettings(
                a.classicArchetypeEnabled(), a.classicArchetypeWeight(), a.bowlCraterArchetypeEnabled(), a.bowlCraterArchetypeWeight(),
                a.crescentArchetypeEnabled(), a.crescentArchetypeWeight(), a.terraceArchetypeEnabled(), a.terraceArchetypeWeight(),
                ocean.lagoonWeight() > 0, ocean.lagoonWeight(), ocean.reefRingWeight() > 0, ocean.reefRingWeight(),
                ocean.deadOceanShelfWeight() > 0, ocean.deadOceanShelfWeight(), ocean.brokenReefWeight() > 0, ocean.brokenReefWeight());
        if (t.terrainReliefScale() < 0 || t.terrainReliefScale() > 1) {
            throw new IllegalArgumentException("Skyweave's verified terrain envelope requires terrain_relief_scale between 0 and 1");
        }
        var terrain = new TerrainSettings(p.activation(), p.anchorMinRadius(), p.anchorMaxRadius(), t.islandSize(),
                p.safeMinimumY(p.anchorMinHeight()), p.safeMaximumY(), t.maxIslandThicknessBlocks(),
                t.lowBandWeight(), t.midHighBandWeight(), t.veryHighBandWeight(), t.lowBandCenterOffset(), t.veryHighBandCenterOffset(),
                // Upstream's serialized spacing is capped at 2000. IslandFieldMixin owns the actual grid.
                t.spacing(),
                t.terrainReliefScale(), archetypes, t.ocean());
        return new SkyIslandSettings(terrain, embedded.structureSupport(), embedded.mediumGround(), embedded.smallGround(),
                embedded.defaultSurface(), embedded.advanced(), RiverConfiguration.settings());
    }

    public static void load(Path directory) {
        Path path = directory.resolve(FILE);
        try {
            if (!Files.exists(path)) {
                Files.createDirectories(directory);
                Files.writeString(path, JSON.toJson(GenerationProfile.INITIAL) + "\n", StandardOpenOption.CREATE_NEW);
            }
            profile = parse(ConfigFile.read(path));
        } catch (IOException | RuntimeException failure) {
            throw new IllegalStateException("Cannot load " + path + ": " + failure.getMessage(), failure);
        }
    }

    public static GenerationProfile parse(String text, ShapeProfile shapes) {
        return design.inherent.skyweavecompat.configuration.ProfileJson.generation(text).validateAgainst(shapes);
    }

    public static GenerationProfile parse(String text) {
        return parse(text, ShapeConfiguration.profile());
    }
}
