package design.inherent.skyweavecompat;

import net.neoforged.fml.loading.LoadingModList;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.fml.loading.FMLPaths;
import java.util.LinkedHashMap;

/** Uses loader metadata only; safe before game and optional mod classes load. */
public final class OptionalIntegrations {
    private OptionalIntegrations() {}

    private static final class Startup {
        static final FeatureSettings SETTINGS = load();
        static final FeatureSelection SELECTION = select();
        private static FeatureSettings load() {
            try {
                return FeatureSettings.load(design.inherent.skyweavecompat.configuration.ConfigLocations.prepare(FMLPaths.CONFIGDIR.get()));
            } catch (java.io.IOException | IllegalArgumentException failure) {
                throw new IllegalStateException("Cannot load " + FeatureSettings.FILE, failure);
            }
        }
        private static FeatureSelection select() {
            var versions = new LinkedHashMap<String, String>();
            for (var mod : LoadingModList.get().getMods())
                versions.put(mod.getModId(), mod.getVersion().toString());
            return FeatureSelection.select(SETTINGS, versions, FMLEnvironment.dist.isClient());
        }
    }

    public static FeatureSelection selection() { return Startup.SELECTION; }
    public static boolean enabled(Feature feature) { return selection().enabled(feature); }
    public static boolean configured(Feature feature) { return Startup.SETTINGS.enabled(feature); }
    public static boolean defaulted(Feature feature) { return !Startup.SETTINGS.values().containsKey(feature); }
    public static boolean loaded(String id) { return LoadingModList.get().getModFileById(id) != null; }
    public static boolean skyVillages() {
        return enabled(Feature.VILLAGE_CLEARANCE) || enabled(Feature.VILLAGE_PLACEMENT);
    }
    public static boolean blueMap() { return enabled(Feature.BLUEMAP); }
    public static boolean distantHorizons() {
        return enabled(Feature.DH_HANDOFF) || enabled(Feature.DH_SHUTDOWN) || enabled(Feature.DH_CACHE);
    }
    public static boolean analogAudio() { return enabled(Feature.ANALOG_AUDIO); }
}
