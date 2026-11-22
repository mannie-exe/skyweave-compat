package design.inherent.skyweavecompat.placement;

import net.neoforged.fml.loading.LoadingModList;

/** The resource owner explicitly requests its island band; standalone data retains its provider. */
public final class VillageHeightPolicy {
    public static final String PROPERTY = "skyweave:owned-village-band";
    private VillageHeightPolicy() {}
    private static final class Declaration {
        static final boolean BAND = read();
        private static boolean read() {
            Object value = LoadingModList.get().getMods().stream()
                    .filter(mod -> mod.getModId().equals("skyweave_resource_patches"))
                    .findFirst().flatMap(mod -> mod.getConfig().getConfigElement(PROPERTY)).orElse(null);
            if (value == null) return false;
            if ((value instanceof Integer || value instanceof Long) && ((Number)value).longValue() == 1L) return true;
            throw new IllegalStateException("Resource Patches " + PROPERTY + " must be integer 1");
        }
    }
    public static boolean packBand() { return Declaration.BAND; }
}
