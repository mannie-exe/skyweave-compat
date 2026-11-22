package design.inherent.skyweavecompat;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;
import org.apache.maven.artifact.versioning.DefaultArtifactVersion;
import org.apache.maven.artifact.versioning.InvalidVersionSpecificationException;
import org.apache.maven.artifact.versioning.VersionRange;

/** Runtime admission is separate from the exact artifacts used to build and test the mod. */
public final class DependencyVersions {
    private DependencyVersions() {}

    public record Contract(String developedAgainst, Set<String> exactVersions, String stableRange) {
        public Contract { exactVersions = Set.copyOf(exactVersions); }
        public boolean accepts(String installed) {
            if (installed == null) return false;
            if (stableRange == null) return exactVersions.contains(installed);
            // Maven places the next family's prereleases below its final release. They are
            // not part of our stable API-family admission, nor are snapshots or build aliases.
            if (!installed.matches("[0-9]+(?:\\.[0-9]+)*")) return false;
            try {
                return VersionRange.createFromVersionSpec(stableRange).containsVersion(new DefaultArtifactVersion(installed));
            } catch (InvalidVersionSpecificationException invalid) {
                throw new IllegalStateException("Invalid Skyweave: Compatibility Patches dependency range: " + stableRange, invalid);
            }
        }
        public String accepted() {
            return stableRange == null ? String.join(", ", exactVersions.stream().sorted().toList()) : "stable " + stableRange;
        }
        public String describe(String id, String installed) {
            return id + ": developed against " + developedAgainst + "; accepts " + accepted()
                    + "; installed " + (installed == null ? "missing" : installed);
        }
    }

    public static Contract exact(String baseline, String... aliases) {
        var versions = new java.util.HashSet<>(List.of(aliases));
        versions.add(baseline);
        return new Contract(baseline, versions, null);
    }

    public static Contract contract(String id, String baseline) {
        return switch (id) {
            // Architectury is a presence/API prerequisite, not a private injection target.
            case "architectury" -> new Contract(baseline, Set.of(), "[13.0.12,14)");
            case "questlog" -> exact(baseline, "3.5.0", "3.5.3", "3.5.4");
            case "surveyor" -> exact(baseline, "1.2.4+1.21");
            case "hoofprint" -> exact(baseline, "1.3.0+1.21");
            default -> exact(baseline);
        };
    }

    private static final class Baseline {
        static final Map<String, Contract> CONTRACTS = load();
        private static Map<String, Contract> load() {
            var properties = new Properties();
            try (var input = DependencyVersions.class.getResourceAsStream("/META-INF/skyweave-compat-development.properties")) {
                if (input == null) throw new IOException("Development dependency metadata is missing");
                properties.load(input);
                return Map.of(
                        "minecraft", exact(required(properties, "minecraft")),
                        "javafml", new Contract(required(properties, "javafml"), Set.of(), "[4,5)"),
                        "neoforge", new Contract(required(properties, "neoforge"), Set.of(), "[" + required(properties, "neoforge") + ",21.2)"),
                        "reform", new Contract(required(properties, "reform"), Set.of(), "[" + required(properties, "reform") + ",0.2)"));
            } catch (IOException failure) {
                throw new IllegalStateException("Cannot read Skyweave: Compatibility Patches development dependency metadata", failure);
            }
        }
        private static String required(Properties properties, String id) throws IOException {
            var version = properties.getProperty(id);
            if (version == null || version.contains("${")) throw new IOException("Missing development version for " + id);
            return version;
        }
    }

    public static Map<String, Contract> base() { return Baseline.CONTRACTS; }

    public static void requireBase(Map<String, String> installed) {
        for (var entry : new TreeMap<>(base()).entrySet())
            if (!entry.getValue().accepts(installed.get(entry.getKey())))
                throw new IllegalStateException("Skyweave: Compatibility Patches cannot start. "
                        + entry.getValue().describe(entry.getKey(), installed.get(entry.getKey()))
                        + ". Install a supported stable version for Minecraft 1.21.1.");
    }

    public static Map<String, String> describeBase(Map<String, String> installed) {
        var result = new TreeMap<String, String>();
        base().forEach((id, contract) -> result.put(id, contract.describe(id, installed.get(id))));
        return java.util.Collections.unmodifiableMap(result);
    }

    public static IllegalStateException unsupported(Feature feature, String id, Contract contract, String installed) {
        return new IllegalStateException("Skyweave: Compatibility Patches cannot enable " + feature.key + ". "
                + contract.describe(id, installed) + ". Disable " + feature.key + " in " + FeatureSettings.FILE
                + " or install an accepted version. This adapter has not been qualified for the installed version.");
    }
}
