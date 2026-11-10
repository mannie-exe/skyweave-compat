package design.inherent.skyweavecompat.terrain.structure;

import com.google.gson.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class StructurePolicyTest {
    private final Gson json = new Gson();

    @Test void lookupPrefixIncludesEveryFitAttemptButNotUnselectedLand() {
        var sites = java.util.stream.IntStream.range(0, 20)
                .mapToObj(i -> new StructureSelection.Site(i, -i)).toList();
        for (var family : StructureFamily.values()) {
            long limit = StructureSelection.attemptLimit(family, 1);
            for (int i = 0; i < sites.size(); i++)
                assertEquals(i < limit, StructureSelection.withinAttempts(family, 1, sites, sites.get(i)));
            assertFalse(StructureSelection.withinAttempts(family, 0, sites, sites.getFirst()));
            assertFalse(StructureSelection.withinAttempts(family, 1, sites, new StructureSelection.Site(100, 100)));
        }
    }

    @Test void variantsShareFamiliesAndForeignNamespacesRemainForeign() {
        assertSame(StructureFamily.PORTALS, StructureFamily.forId("minecraft:ruined_portal_desert"));
        assertSame(StructureFamily.PORTALS, StructureFamily.forId("minecraft:ruined_portal_ocean"));
        assertSame(StructureFamily.VILLAGES, StructureFamily.forId("minecraft:village_snowy"));
        assertSame(StructureFamily.SHIPWRECKS, StructureFamily.forId("minecraft:shipwreck_beached"));
        for (String id : List.of("minecraft:ruined_portal_nether", "skyvillages:skyvillage", "example:village_plains", "example:shipwreck"))
            assertNull(StructureFamily.forId(id));
        var all = new HashSet<String>();
        for (var family : StructureFamily.values()) for (String id : family.ids()) assertTrue(all.add(id), id);
        assertSame(StructureFamily.PORTALS, StructureFamily.forSet(List.of("minecraft:ruined_portal", "minecraft:ruined_portal_nether")));
        assertSame(StructureFamily.VILLAGES, StructureFamily.forSet(List.of("minecraft:village_plains", "minecraft:village_taiga")));
        assertNull(StructureFamily.forSet(List.of("minecraft:village_plains", "example:custom_village")));
        assertNull(StructureFamily.forSet(List.of("minecraft:village_plains", "minecraft:ruined_portal")));
    }

    @Test void unsupportedNativeCategoryOverridesKeepTheirOriginalPlanner() {
        var village = org.sathrek.sky_archipelago.worldgen.structure.StructurePlacementCategory.GROUND_VILLAGE;
        var sky = org.sathrek.sky_archipelago.worldgen.structure.StructurePlacementCategory.SKY;
        var ground = org.sathrek.sky_archipelago.worldgen.structure.StructurePlacementCategory.DEFAULT;
        var underground = org.sathrek.sky_archipelago.worldgen.structure.StructurePlacementCategory.UNDERGROUND;
        assertTrue(StructureDistribution.supportsRoute(StructureFamily.VILLAGES, village));
        assertFalse(StructureDistribution.supportsRoute(StructureFamily.VILLAGES, sky));
        assertFalse(StructureDistribution.supportsRoute(StructureFamily.MINESHAFTS, ground));
        assertTrue(StructureDistribution.supportsRoute(StructureFamily.MINESHAFTS, underground));
        assertTrue(StructureDistribution.supportsRoute(StructureFamily.TRAIL_RUINS, ground));
        assertTrue(StructureDistribution.supportsRoute(StructureFamily.TRAIL_RUINS, underground));
        assertFalse(StructureDistribution.supportsRoute(StructureFamily.DESERT_TEMPLES, underground));
    }

    @Test void parserRejectsAmbiguousAndUnboundedRules() {
        assertEquals(StructurePolicy.DEFAULT, StructureConfiguration.parse(json.toJson(StructurePolicy.DEFAULT)));
        var original = JsonParser.parseString(json.toJson(StructurePolicy.DEFAULT)).getAsJsonObject();
        for (String invalid : List.of("NaN", "-1", "9", "\"1\"", "null")) {
            var edited = original.deepCopy();
            edited.add("frequencyMultiplier", JsonParser.parseString(invalid));
            assertThrows(RuntimeException.class, () -> StructureConfiguration.parse(edited.toString()), invalid);
        }
        var missing = original.deepCopy(); missing.getAsJsonObject("families").remove("villages");
        assertThrows(RuntimeException.class, () -> StructureConfiguration.parse(missing.toString()));
        var unknown = original.deepCopy(); unknown.getAsJsonObject("families").add("example", original.getAsJsonObject("families").get("portals"));
        assertThrows(RuntimeException.class, () -> StructureConfiguration.parse(unknown.toString()));
        var fractional = original.deepCopy(); fractional.getAsJsonObject("families").getAsJsonObject("portals").addProperty("maximumPerCluster", 1.5);
        assertThrows(RuntimeException.class, () -> StructureConfiguration.parse(fractional.toString()));
        assertThrows(RuntimeException.class, () -> StructureConfiguration.parse(original.toString().replace("\"frequencyMultiplier\":1.0", "\"frequencyMultiplier\":1.0,\"frequencyMultiplier\":2")));
    }

    @Test void fittingSettingsPreserveExistingPolicyAndHaveMatchingRuntimeEditorValidation() {
        var authored=JsonParser.parseString(json.toJson(StructurePolicy.DEFAULT)).getAsJsonObject();
        authored.addProperty("frequencyMultiplier",.75);
        authored.getAsJsonObject("families").getAsJsonObject("villages").addProperty("maximumPerCluster",2);
        authored.remove("villageFitting");
        var loaded=StructureConfiguration.parse(authored.toString());
        assertEquals(.75,loaded.frequencyMultiplier());assertEquals(2,loaded.rule(StructureFamily.VILLAGES).maximumPerCluster());
        assertEquals(VillageFitSettings.DEFAULT,loaded.villageFitting());
        var normalized=StructureConfiguration.normalize(authored.toString());
        assertEquals(loaded,StructureConfiguration.parse(normalized));
        for(var invalid:List.of("0.49","1.01","null","\"0.9\"")) {
            var edited=JsonParser.parseString(normalized).getAsJsonObject();
            edited.getAsJsonObject("villageFitting").add("minimumSupportFraction",JsonParser.parseString(invalid));
            assertThrows(RuntimeException.class,()->StructureConfiguration.parse(edited.toString()));
        }
        var edited=JsonParser.parseString(normalized).getAsJsonObject();
        edited.getAsJsonObject("villageFitting").addProperty("siteAttemptsPerVillage",3);
        var custom=StructureConfiguration.parse(edited.toString());
        assertEquals(6,StructureSelection.attemptLimit(StructureFamily.VILLAGES,2,custom.villageFitting()));
        assertEquals(16,StructureSelection.attemptLimit(StructureFamily.SHIPWRECKS,2,custom.villageFitting()));
        edited.getAsJsonObject("villageFitting").addProperty("maximumColumnProbes",1000000);
        assertThrows(IllegalArgumentException.class,()->StructureConfiguration.parse(edited.toString()));
    }

    @Test void fractionalAllowancesRetainTheirMeanRatherThanRoundingToZero() {
        int count = 0;
        for (int x = -5000; x < 5000; x++) {
            int n = StructureSelection.allowance(42, new StructureSelection.Owner(x, -17), StructureFamily.PORTALS, 800, StructurePolicy.DEFAULT);
            assertTrue(n == 0 || n == 1);
            count += n;
        }
        assertEquals(.25, count / 10000.0, .015);
        assertEquals(0, StructureSelection.allowance(42, new StructureSelection.Owner(0, 0), StructureFamily.PORTALS, 0, StructurePolicy.DEFAULT));
    }

    @Test void frequencyAndCapsApplyOnceWithoutGivingVariantsExtraSlots() {
        var original = StructurePolicy.DEFAULT;
        var owner = new StructureSelection.Owner(-6, 13);
        assertEquals(1, StructureSelection.allowance(8, owner, StructureFamily.PORTALS, 1_000_000, original));
        var doubled = new StructurePolicy(2, 10, original.families());
        var rules = new TreeMap<>(original.families());
        rules.put("mineshafts", new StructurePolicy.Rule(1, 10));
        var one = new StructurePolicy(1, 10, rules);
        var two = new StructurePolicy(2, 10, rules);
        assertEquals(2, StructureSelection.allowance(8, owner, StructureFamily.MINESHAFTS, 2048, one));
        assertEquals(4, StructureSelection.allowance(8, owner, StructureFamily.MINESHAFTS, 2048, two));
        assertEquals(1, StructureSelection.allowance(8, owner, StructureFamily.PORTALS, 1_000_000, doubled));
        var disabled = new StructurePolicy(0, 10, original.families());
        assertEquals(0, StructureSelection.allowance(8, owner, StructureFamily.MINESHAFTS, 1_000_000, disabled));
        assertThrows(IllegalArgumentException.class, () -> StructureSelection.allowance(8, owner, StructureFamily.PORTALS, Double.NaN, original));
    }

    @Test void rankingIsStableAcrossEnumerationOrderDuplicatesAndUnrelatedCalls() {
        var sites = new ArrayList<StructureSelection.Site>();
        for (int x = -4; x <= 4; x++) for (int z = -4; z <= 4; z++) sites.add(new StructureSelection.Site(x, z));
        var owner = new StructureSelection.Owner(-1, 0);
        var expected = StructureSelection.ranked(42, owner, StructureFamily.VILLAGES, sites);
        Collections.shuffle(sites, new Random(9));
        sites.add(sites.getFirst());
        StructureSelection.ranked(-42, owner, StructureFamily.PORTALS, sites);
        assertEquals(expected, StructureSelection.ranked(42, owner, StructureFamily.VILLAGES, sites));
        assertNotEquals(expected, StructureSelection.ranked(-42, owner, StructureFamily.VILLAGES, sites));
        assertNotEquals(expected, StructureSelection.ranked(42, new StructureSelection.Owner(0, -1), StructureFamily.VILLAGES, sites));
    }

    @Test void outpostExclusionUsesTheNativeSquareAtFinalAnchors() {
        var villages = List.of(new StructureSelection.Site(100, -100));
        assertTrue(StructureSelection.outpostExcluded(new StructureSelection.Site(110, -90), villages, 10));
        assertFalse(StructureSelection.outpostExcluded(new StructureSelection.Site(111, -100), villages, 10));
        assertFalse(StructureSelection.outpostExcluded(new StructureSelection.Site(100, -100), villages, 0));
        assertFalse(StructureSelection.outpostExcluded(new StructureSelection.Site(Integer.MIN_VALUE, 0),
                List.of(new StructureSelection.Site(Integer.MAX_VALUE, 0)), 10));
    }
}
