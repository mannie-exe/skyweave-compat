package design.inherent.skyweavecompat.terrain.structure;

import java.util.Arrays;
import java.util.Collection;
import java.util.Set;

/** Explicit membership and rounded half-vanilla opportunity densities; names alone do not establish a family. */
public enum StructureFamily {
    PORTALS("portals", .32, 1, "ruined_portal", "ruined_portal_desert", "ruined_portal_jungle",
            "ruined_portal_swamp", "ruined_portal_mountain", "ruined_portal_ocean"),
    VILLAGES("villages", .443, 3, "village_plains", "village_desert",
            "village_savanna", "village_snowy", "village_taiga"),
    OUTPOSTS("outposts", .1, 1, "pillager_outpost"),
    SHIPWRECKS("shipwrecks", .889, 1, "shipwreck", "shipwreck_beached"),
    DESERT_TEMPLES("desert_temples", .5, 1, "desert_pyramid"),
    JUNGLE_TEMPLES("jungle_temples", .5, 1, "jungle_pyramid"),
    SWAMP_HUTS("swamp_huts", .5, 1, "swamp_hut"),
    IGLOOS("igloos", .5, 1, "igloo"),
    MANSIONS("mansions", .08, 1, "mansion"),
    ANCIENT_CITIES("ancient_cities", .889, 1, "ancient_city"),
    MINESHAFTS("mineshafts", 2.048, 3, "mineshaft", "mineshaft_mesa"),
    TRIAL_CHAMBERS("trial_chambers", .443, 1, "trial_chambers"),
    TRAIL_RUINS("trail_ruins", .443, 1, "trail_ruins");

    public final String key;
    public final double defaultRate;
    public final int defaultMaximum;
    private final Set<String> ids;

    StructureFamily(String key, double rate, int maximum, String... paths) {
        this.key = key;
        defaultRate = rate;
        defaultMaximum = maximum;
        ids = Arrays.stream(paths).map(path -> "minecraft:" + path).collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    public Set<String> ids() { return ids; }
    public static StructureFamily forId(String id) {
        return Arrays.stream(values()).filter(family -> family.ids.contains(id)).findFirst().orElse(null);
    }
    /** Keep mixed or foreign sets untouched; the Nether portal entry shares vanilla's registered set. */
    public static StructureFamily forSet(Collection<String> ids) {
        return forSet(ids, StructureFamily::forId);
    }
    public static StructureFamily forSet(Collection<String> ids, java.util.function.Function<String, StructureFamily> membership) {
        var known = ids.stream().map(membership).filter(java.util.Objects::nonNull).distinct().toList();
        if (known.size() != 1) return null;
        var family = known.getFirst();
        return ids.stream().allMatch(id -> membership.apply(id) == family
                || family == PORTALS && id.equals("minecraft:ruined_portal_nether")) ? family : null;
    }
}
