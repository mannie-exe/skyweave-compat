package design.inherent.skyweavecompat.configuration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Set;

/** Physical locations only; loaders and registered specs retain configuration authority. */
public final class ConfigLocations {
    public static final String DIRECTORY = "skyweave";
    private static final Set<String> OWNED = Set.of(
            "skyweave_compat-wayfinder.json",
            "skyweave_compat-hoofprint.json", "skyweave_compat-features.properties", "skyweave_compat-common.toml",
            "skyweave_compat-journal-client.toml", "skyweave_compat-generation.json",
            "skyweave_compat-shapes.json", "skyweave_compat-interiors.json", "skyweave_compat-climate.json", "skyweave_compat-rivers.json",
            "skyweave_compat-oceans.json", "skyweave_compat-structures.json",
            "skyweave_compat-ancient-cities.json", "skyweave_compat-search.json", "skyweave_compat-bluemap.json");

    private ConfigLocations() {}

    public static Path ownedDirectory(Path configRoot) { return configRoot.resolve(DIRECTORY); }
    public static Path directory(Path configRoot, String filename) {
        return OWNED.contains(filename) ? ownedDirectory(configRoot) : configRoot;
    }
    public static String registeredName(String filename) {
        return OWNED.contains(filename) ? DIRECTORY + "/" + filename : filename;
    }
    public static Path file(Path configRoot, String filename) { return directory(configRoot, filename).resolve(filename); }

    /** Run before startup feature selection, so an old file cannot silently become fresh defaults. */
    public static Path prepare(Path configRoot) throws IOException {
        if (Files.isSymbolicLink(configRoot)) throw new IOException("Configuration root must be a regular local directory");
        Files.createDirectories(configRoot);
        var owned = ownedDirectory(configRoot);
        if (Files.isSymbolicLink(owned)) throw new IOException("Skyweave configuration directory must not be a symbolic link");
        for (String filename : OWNED) {
            var legacy = configRoot.resolve(filename);
            if (!Files.exists(legacy, LinkOption.NOFOLLOW_LINKS)) continue;
            var destination = owned.resolve(filename);
            if (!Files.isRegularFile(legacy, LinkOption.NOFOLLOW_LINKS)
                    || !Files.isRegularFile(destination, LinkOption.NOFOLLOW_LINKS)
                    || Files.mismatch(legacy, destination) != -1)
                throw new IOException("Move " + legacy + " to " + destination
                        + " before starting. Preserve existing values and resolve conflicting copies explicitly.");
        }
        var oldRecipes = configRoot.resolve("skyweave-shaders/recipes");
        if (Files.exists(oldRecipes, LinkOption.NOFOLLOW_LINKS))
            throw new IOException("Move shader recipes from " + oldRecipes + " to " + owned.resolve("shaders/recipes")
                    + " before starting; cached shaders do not need to be deleted.");
        Files.createDirectories(owned);
        return owned;
    }
}
