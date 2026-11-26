package design.inherent.skyweavecompat;

import net.neoforged.fml.loading.LoadingModList;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.fml.loading.FMLPaths;
import java.util.LinkedHashMap;

/** Uses loader metadata only; safe before game and optional mod classes load. */
public final class OptionalIntegrations {
    private OptionalIntegrations() {}

    private static final class Startup {
        static final java.util.Map<String, String> VERSIONS = versions();
        static final FeatureSettings SETTINGS = load();
        static final FeatureSelection SELECTION = FeatureSelection.select(SETTINGS, VERSIONS, FMLEnvironment.dist.isClient());
        private static FeatureSettings load() {
            try {
                return FeatureSettings.load(design.inherent.skyweavecompat.configuration.ConfigLocations.prepare(FMLPaths.CONFIGDIR.get()));
            } catch (java.io.IOException | IllegalArgumentException failure) {
                throw new IllegalStateException("Cannot load " + FeatureSettings.FILE, failure);
            }
        }
        private static java.util.Map<String, String> versions() {
            var versions = new LinkedHashMap<String, String>();
            for (var mod : LoadingModList.get().getMods())
                versions.put(mod.getModId(), mod.getVersion().toString());
            versions.put("javafml", net.neoforged.fml.loading.FMLLoader.versionInfo().fmlVersion());
            DependencyVersions.requireBase(versions);
            com.mojang.logging.LogUtils.getLogger().info("Skyweave: Compatibility Patches dependency baselines and installed versions: {}", DependencyVersions.describeBase(versions));
            return java.util.Map.copyOf(versions);
        }
    }

    public static FeatureSelection selection() { return Startup.SELECTION; }
    public static String describe(Feature feature) { return selection().describe(feature); }
    public static FeatureSettings startupSettings() { return Startup.SETTINGS; }
    public static boolean enabled(Feature feature) { return selection().enabled(feature); }
    public static boolean configured(Feature feature) { return Startup.SETTINGS.enabled(feature); }
    public static boolean defaulted(Feature feature) { return !Startup.SETTINGS.values().containsKey(feature); }
    public static boolean loaded(String id) { return LoadingModList.get().getModFileById(id) != null; }
    public static boolean skyVillages() {
        return enabled(Feature.VILLAGE_CLEARANCE) || enabled(Feature.VILLAGE_PLACEMENT);
    }
    public static boolean blueMap() { return enabled(Feature.BLUEMAP); }
    public static boolean distantHorizons() {
        return enabled(Feature.DH_EXECUTOR_LIFECYCLE) || enabled(Feature.DH_RENDER_RESOURCES) || enabled(Feature.DH_CLIENT_COMPLETION) || enabled(Feature.DH_UPDATE_QUEUE) || enabled(Feature.DH_HANDOFF) || enabled(Feature.DH_SHUTDOWN) || enabled(Feature.DH_CACHE);
    }
    public static boolean analogAudio() { return enabled(Feature.ANALOG_AUDIO); }
}
