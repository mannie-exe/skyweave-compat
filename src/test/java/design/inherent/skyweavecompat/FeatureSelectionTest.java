package design.inherent.skyweavecompat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class FeatureSelectionTest {
    @Test void floeWaterLayersRequireBothOptionalOwnersWithoutRequiringTemperature() {
        var mods=Map.of("sky_archipelago","1.4","dynamicwaters","13.1.0");
        var settings=only(Feature.TERRAIN,Feature.DYNAMIC_WATERS,Feature.WATER_SPAWNS);
        for(boolean client:List.of(false,true)) {
            assertTrue(FeatureSelection.select(settings,mods,client).mixins(client).contains("water.FloeWaterLevelMixin"));
            assertFalse(FeatureSelection.select(settings,Map.of("sky_archipelago","1.4"),client).mixins(client).contains("water.FloeWaterLevelMixin"));
            assertFalse(FeatureSelection.select(only(Feature.TERRAIN,Feature.DYNAMIC_WATERS),mods,client).mixins(client).contains("water.FloeWaterLevelMixin"));
            assertFalse(FeatureSelection.select(only(Feature.TERRAIN,Feature.WATER_SPAWNS),mods,client).mixins(client).contains("water.FloeWaterLevelMixin"));
        }
    }
    @Test void sableTemperatureCoordinatesAreIndependentOfDynamicWaters() {
        var mods=Map.of("sky_archipelago","1.4","sable","2.0.6");
        for(boolean client:List.of(false,true)) {
            var settings=only(Feature.TERRAIN,Feature.CLIMATE,Feature.SABLE_CLIMATE);
            var selected=FeatureSelection.select(settings,mods,client);
            assertTrue(selected.enabled(Feature.SABLE_CLIMATE));
            assertTrue(selected.mixins(client).contains("SableClimateMixin"));
            assertFalse(selected.enabled(Feature.DYNAMIC_WATERS));
            assertFalse(FeatureSelection.select(settings,Map.of("sky_archipelago","1.4"),client).mixins(client).contains("SableClimateMixin"));
            assertFalse(FeatureSelection.select(only(Feature.TERRAIN,Feature.SABLE_CLIMATE),mods,client).enabled(Feature.SABLE_CLIMATE));
            assertFalse(FeatureSelection.select(only(Feature.TERRAIN,Feature.CLIMATE),mods,client).mixins(client).contains("SableClimateMixin"));
            assertThrows(IllegalStateException.class,()->FeatureSelection.select(settings,Map.of("sky_archipelago","1.4","sable","future"),client));
        }
    }

    @Test void wayfinderAdmitsOnlyTheInspectedOptionalSableFrameApi() {
        for (boolean client : List.of(false,true)) {
            assertTrue(FeatureSelection.select(only(Feature.WAYFINDER),Map.of(),client).enabled(Feature.WAYFINDER));
            assertTrue(FeatureSelection.select(only(Feature.WAYFINDER),Map.of("sable","2.0.6"),client).enabled(Feature.WAYFINDER));
            assertThrows(IllegalStateException.class,() -> FeatureSelection.select(only(Feature.WAYFINDER),Map.of("sable","future"),client));
            assertDoesNotThrow(() -> FeatureSelection.select(only(),Map.of("sable","future"),client));
        }
    }
    @Test void ftbAlternativesUseOnlyTheOptionalCommonApi() {
        var feature = Feature.FTB_ITEM_FILTERS;
        for (boolean client : List.of(false, true)) {
            var selected = FeatureSelection.select(only(feature), Map.of("ftbquests", "2101.1.38"), client);
            assertEquals(java.util.Set.of(feature), selected.active());
            assertTrue(selected.mixins(client).isEmpty());
            assertFalse(FeatureSelection.select(only(feature), Map.of(), client).enabled(feature));
            assertFalse(FeatureSelection.select(only(), Map.of("ftbquests", "future"), client).enabled(feature));
            assertThrows(IllegalStateException.class, () -> FeatureSelection.select(only(feature), Map.of("ftbquests", "future"), client));
        }
    }
    @Test void c2meStorageHooksRequireTheExactOptionalBackendAndTerrain() {
        var feature = Feature.C2ME_SEARCH;
        var mods = Map.of("sky_archipelago", "1.4", "c2me_rewrites_chunkio", "0.4.0-alpha.0.124+1.21.1");
        for (boolean client : List.of(false, true)) {
            var selected = FeatureSelection.select(only(Feature.TERRAIN, feature), mods, client);
            assertTrue(selected.enabled(feature));
            assertTrue(selected.mixins(client).containsAll(List.of("C2meSearchWorkerMixin", "C2meSearchStorageMixin")));
            assertFalse(FeatureSelection.select(only(Feature.TERRAIN), mods, client).mixins(client).contains("C2meSearchWorkerMixin"));
            assertFalse(FeatureSelection.select(only(feature), mods, client).enabled(feature));
            assertFalse(FeatureSelection.select(only(Feature.TERRAIN, feature), Map.of("sky_archipelago", "1.4"), client).enabled(feature));
        }
        assertThrows(IllegalStateException.class, () -> FeatureSelection.select(only(Feature.TERRAIN, feature),
                Map.of("sky_archipelago", "1.4", "c2me_rewrites_chunkio", "future"), false));
        assertDoesNotThrow(() -> FeatureSelection.select(only(Feature.TERRAIN),
                Map.of("sky_archipelago", "1.4", "c2me_rewrites_chunkio", "future"), false));
    }
    @Test void hoofprintZoomIsPhysicalClientOnlyAndIndependentOfTerrain() {
        var feature = Feature.HOOFPRINT_ZOOM;
        var versions = Map.of("hoofprint", "1.3.0_1.21", "surveyor", "1.2.4_1.21", "connector", "2.0.0-beta.17+1.21.1");
        assertEquals(java.util.Set.of(feature), FeatureSelection.select(only(feature), versions, true).active());
        assertEquals(List.of("client.HoofprintZoomMixin"), FeatureSelection.select(only(feature), versions, true).mixins(true));
        assertTrue(FeatureSelection.select(only(feature), versions, false).active().isEmpty());
        assertTrue(FeatureSelection.select(only(feature), Map.of(), true).active().isEmpty());
        assertTrue(FeatureSelection.select(only(), Map.of("hoofprint", "future"), true).active().isEmpty());
        assertThrows(IllegalStateException.class, () -> FeatureSelection.select(only(feature),
                Map.of("hoofprint", "future", "surveyor", "1.2.4_1.21", "connector", "2.0.0-beta.17+1.21.1"), true));
    }
    @Test void accessAndCommandPolicyRequireTerrainOnBothLogicalServerHosts() {
        var settings = only(Feature.TERRAIN, Feature.NETHER_ACCESS, Feature.STRUCTURE_COMMAND);
        for (boolean client : new boolean[]{false,true}) {
            var selected = FeatureSelection.select(settings, Map.of("sky_archipelago","1.4"), client);
            assertTrue(selected.enabled(Feature.NETHER_ACCESS));
            assertTrue(selected.enabled(Feature.STRUCTURE_COMMAND));
            assertTrue(selected.mixins(client).contains("ClosedDimensionLoginMixin"));
            assertFalse(FeatureSelection.select(only(Feature.NETHER_ACCESS, Feature.STRUCTURE_COMMAND),
                    Map.of("sky_archipelago","1.4"), client).enabled(Feature.NETHER_ACCESS));
        }
    }

    @Test void ftbCreativeReturnIsClientOnlyAndDoesNotRequireQuestOrInventoryMods() {
        var feature = Feature.FTB_CREATIVE_RETURN;
        var versions = Map.of("ftblibrary", "2101.1.39");
        assertEquals(java.util.Set.of(feature), FeatureSelection.select(only(feature), versions, true).active());
        assertEquals(java.util.List.of("client.FtbCreativeReturnMixin"), FeatureSelection.select(only(feature), versions, true).mixins(true));
        assertTrue(FeatureSelection.select(only(feature), Map.of(), true).active().isEmpty());
        assertTrue(FeatureSelection.select(only(feature), Map.of("ftblibrary", "future"), false).active().isEmpty());
        assertTrue(FeatureSelection.select(only(), Map.of("ftblibrary", "future"), true).active().isEmpty());
        assertThrows(IllegalStateException.class, () -> FeatureSelection.select(only(feature), Map.of("ftblibrary", "future"), true));
    }

    @Test void hutSupportHookRequiresTheQualifiedOptionalMod() {
        var settings = only(Feature.TERRAIN, Feature.STRUCTURE_DISTRIBUTION, Feature.YUNG_STRUCTURES, Feature.YUNG_WITCH_HUTS);
        var base = Map.of("sky_archipelago", "1.4", "yungsapi", "1.21.1-NeoForge-5.1.9");
        assertFalse(FeatureSelection.select(settings, base, false).mixins(false).contains("YungHutSupportMixin"));
        var mods = new HashMap<>(base); mods.put("betterwitchhuts", "1.21.1-NeoForge-4.1.1");
        assertTrue(FeatureSelection.select(settings, mods, false).mixins(false).contains("YungHutSupportMixin"));
        assertTrue(FeatureSelection.select(settings, mods, true).mixins(true).contains("YungHutSupportMixin"));
        assertFalse(FeatureSelection.select(only(Feature.TERRAIN, Feature.STRUCTURE_DISTRIBUTION, Feature.YUNG_STRUCTURES), mods, false)
                .mixins(false).contains("YungHutSupportMixin"));
    }
    @Test void mineshaftBridgeRequiresItsNativeOwnerAndGeneralYungPolicy() {
        var mods = Map.of("sky_archipelago", "1.4", "yungsapi", "1.21.1-NeoForge-5.1.9", "bettermineshafts", "1.21.1-NeoForge-5.1.1");
        var enabled = only(Feature.TERRAIN, Feature.STRUCTURE_DISTRIBUTION, Feature.YUNG_STRUCTURES, Feature.YUNG_MINESHAFTS);
        assertTrue(FeatureSelection.select(enabled, mods, false).mixins(false).contains("YungMineshaftEntranceMixin"));
        assertFalse(FeatureSelection.select(only(Feature.TERRAIN, Feature.STRUCTURE_DISTRIBUTION, Feature.YUNG_MINESHAFTS), mods, false)
                .enabled(Feature.YUNG_MINESHAFTS));
        assertFalse(FeatureSelection.select(enabled, Map.of("sky_archipelago", "1.4", "yungsapi", "1.21.1-NeoForge-5.1.9"), false)
                .mixins(false).contains("YungMineshaftEntranceMixin"));
    }
    @Test void yungBridgeUsesOnlyQualifiedOptionalTargets() {
        var enabled = only(Feature.TERRAIN, Feature.STRUCTURE_DISTRIBUTION, Feature.YUNG_STRUCTURES);
        assertFalse(FeatureSelection.select(enabled, Map.of("sky_archipelago", "1.4"), false).enabled(Feature.YUNG_STRUCTURES));
        var mods = Map.of("sky_archipelago", "1.4", "yungsapi", "1.21.1-NeoForge-5.1.9");
        assertTrue(FeatureSelection.select(enabled, mods, false).mixins(false).contains("YungStructureMixin"));
        assertTrue(FeatureSelection.select(enabled, mods, false).mixins(false).contains("StructureLocateCheckMixin"));
        assertEquals(1, FeatureSelection.select(only(Feature.TERRAIN, Feature.STRUCTURE_DISTRIBUTION, Feature.YUNG_STRUCTURES,
                Feature.STRUCTURE_LOCATION), mods, false).mixins(false).stream().filter("StructureLocateCheckMixin"::equals).count());
        assertFalse(FeatureSelection.select(only(Feature.TERRAIN, Feature.YUNG_STRUCTURES), mods, false).enabled(Feature.YUNG_STRUCTURES));
        var future = Map.of("sky_archipelago", "1.4", "yungsapi", "1.21.1-NeoForge-5.2.0");
        assertThrows(IllegalStateException.class, () -> FeatureSelection.select(enabled, future, false));
        assertFalse(FeatureSelection.select(only(Feature.TERRAIN, Feature.STRUCTURE_DISTRIBUTION), future, false)
                .mixins(false).contains("YungStructureMixin"));
        var optionalFuture = new HashMap<>(mods); optionalFuture.put("betterdeserttemples", "1.21.1-NeoForge-4.2.0");
        assertThrows(IllegalStateException.class, () -> FeatureSelection.select(enabled, optionalFuture, false));
        assertFalse(FeatureSelection.select(only(Feature.TERRAIN, Feature.STRUCTURE_DISTRIBUTION), optionalFuture, false)
                .mixins(false).contains("YungReplacementMixin"));
    }
    @Test void lithostitchedBridgeRequiresTheOwnedPolicyAndExactOptionalLibrary() {
        var settings = only(Feature.TERRAIN, Feature.STRUCTURE_DISTRIBUTION, Feature.LITHOSTITCHED_STRUCTURES);
        assertFalse(FeatureSelection.select(settings, Map.of("sky_archipelago", "1.4"), false).enabled(Feature.LITHOSTITCHED_STRUCTURES));
        var selected = FeatureSelection.select(settings, Map.of("sky_archipelago", "1.4", "lithostitched", "1.8.1"), false);
        assertTrue(selected.enabled(Feature.LITHOSTITCHED_STRUCTURES));
        assertTrue(selected.mixins(false).contains("LithostitchedStructureMixin"));
        assertThrows(IllegalStateException.class, () -> FeatureSelection.select(settings,
                Map.of("sky_archipelago", "1.4", "lithostitched", "1.8.2"), false));
        var disabled = only(Feature.TERRAIN, Feature.STRUCTURE_DISTRIBUTION);
        assertFalse(FeatureSelection.select(disabled, Map.of("sky_archipelago", "1.4", "lithostitched", "1.8.2"), false)
                .mixins(false).contains("LithostitchedStructureMixin"));
        assertFalse(FeatureSelection.select(only(Feature.TERRAIN, Feature.LITHOSTITCHED_STRUCTURES),
                Map.of("sky_archipelago", "1.4", "lithostitched", "1.8.1"), false).enabled(Feature.LITHOSTITCHED_STRUCTURES));
    }
    @Test void structureDistributionNeedsOnlyTheOwnedTerrainAndSkyArchipelago() {
        var feature = Feature.STRUCTURE_DISTRIBUTION;
        assertTrue(feature.defaultEnabled);
        var enabled = FeatureSelection.select(only(Feature.TERRAIN, feature), Map.of("sky_archipelago", "1.4"), false);
        assertTrue(enabled.enabled(feature));
        assertTrue(enabled.mixins(false).contains("StructurePolicyPortalHeightMixin"));
        assertTrue(FeatureSelection.select(only(Feature.TERRAIN, feature), Map.of(), false).active().isEmpty());
        assertFalse(FeatureSelection.select(only(feature), Map.of("sky_archipelago", "1.4"), false).enabled(feature));
        assertTrue(FeatureSelection.select(only(), Map.of("sky_archipelago", "future"), false).mixins(false).isEmpty());
    }

    @Test void uiShadowPoliciesNeedOnlyIrisAndCanBeSelectedIndependently() {
        for (Feature feature : java.util.List.of(Feature.TEXT_DISPLAY_SHADOW, Feature.NAMETAG_SHADOW)) {
            assertTrue(feature.defaultEnabled);
            var versions = FeatureSelection.dependencies(feature);
            assertEquals(java.util.Set.of("iris"), versions.keySet());
            var selected = FeatureSelection.select(only(feature), versions, true);
            assertEquals(java.util.Set.of(feature), selected.active());
            assertEquals(1, selected.mixins(true).size());
            assertTrue(FeatureSelection.select(only(feature), Map.of(), true).mixins(true).isEmpty());
            assertTrue(FeatureSelection.select(only(feature), Map.of("iris", "future"), false).mixins(false).isEmpty());
            assertTrue(FeatureSelection.select(only(), Map.of("iris", "future"), true).mixins(true).isEmpty());
            assertThrows(IllegalStateException.class, () -> FeatureSelection.select(only(feature), Map.of("iris", "future"), true));
        }
    }
    @Test void textLightingHasIndependentEmittersAndOneSharedClientBackend() {
        var iris = Map.of("iris", "1.8.14-beta.1+mc1.21.1");
        for (var feature : java.util.List.of(Feature.TEXT_DISPLAY_LIGHTING, Feature.NAMETAG_LIGHTING)) {
            var selected = FeatureSelection.select(only(feature), iris, true);
            assertEquals(java.util.Set.of(feature), selected.active());
            assertEquals(3, selected.mixins(true).size());
            assertTrue(FeatureSelection.select(only(feature), Map.of(), true).mixins(true).isEmpty());
            assertTrue(FeatureSelection.select(only(feature), iris, false).mixins(false).isEmpty());
            assertTrue(FeatureSelection.select(only(), Map.of("iris", "future"), true).mixins(true).isEmpty());
            assertThrows(IllegalStateException.class, () -> FeatureSelection.select(only(feature), Map.of("iris", "future"), true));
        }
        var both = FeatureSelection.select(only(Feature.TEXT_DISPLAY_LIGHTING, Feature.NAMETAG_LIGHTING), iris, true);
        assertEquals(4, both.mixins(true).size());
        assertEquals(1, both.mixins(true).stream().filter(m -> m.equals("client.WorldTextPipelineMixin")).count());
        assertEquals(1, both.mixins(true).stream().filter(m -> m.equals("client.WorldTextShaderMixin")).count());
        assertFalse(both.enabled(Feature.TEXT_DISPLAY_SHADOW));
        assertFalse(both.enabled(Feature.NAMETAG_SHADOW));
    }

    @Test void glassBackfacesAreIndependentDefaultOnClientAdapters() {
        for (var feature : java.util.List.of(Feature.CREATE_BACKFACES, Feature.FUSION_BACKFACES,
                Feature.CONFECTIONERY_BACKFACES, Feature.BUBBLE_GUM_BACKFACES,
                Feature.ORNATE_GRATE_BACKFACES, Feature.INDUSTRIAL_GRATING_BACKFACES)) {
            assertTrue(feature.defaultEnabled);
            var supported = FeatureSelection.dependencies(feature);
            assertTrue(FeatureSelection.select(only(feature), supported, true).enabled(feature));
            assertTrue(FeatureSelection.select(only(feature), supported, false).active().isEmpty());
            assertTrue(FeatureSelection.select(only(feature), Map.of(), true).active().isEmpty());
            var future = new HashMap<>(supported);
            future.replaceAll((key, value) -> "future");
            assertTrue(FeatureSelection.select(only(), future, true).active().isEmpty());
            assertThrows(IllegalStateException.class, () -> FeatureSelection.select(only(feature), future, true));
        }
    }

    @Test void hangingLanternDefaultsOnAndNeedsOnlyMacawOnEitherSide() {
        assertTrue(Feature.MACAW_LANTERN.defaultEnabled);
        for (boolean client : new boolean[]{false, true}) {
            var selected = FeatureSelection.select(only(Feature.MACAW_LANTERN), Map.of("mcwbridges", "3.1.2"), client);
            assertTrue(selected.enabled(Feature.MACAW_LANTERN));
            assertEquals(java.util.List.of("macaw.BridgeLanternShapeMixin", "macaw.ClipModeAccessor", "macaw.LanternRayMixin"), selected.mixins(client));
            assertTrue(FeatureSelection.select(only(Feature.MACAW_LANTERN), Map.of(), client).mixins(client).isEmpty());
            assertTrue(FeatureSelection.select(only(), Map.of("mcwbridges", "future"), client).active().isEmpty());
            assertThrows(IllegalStateException.class, () -> FeatureSelection.select(only(Feature.MACAW_LANTERN),
                    Map.of("mcwbridges", "future"), client));
        }
    }

    @Test void constructLanternPickingReplacesOnlyTheQualifiedGlobalHook() {
        var settings = only(Feature.MACAW_LANTERN, Feature.MACAW_LANTERN_SABLE);
        var supported = Map.of("mcwbridges", "3.1.2", "sable", "2.0.6");
        for (boolean client : new boolean[]{false, true}) {
            var selected = FeatureSelection.select(settings, supported, client);
            assertTrue(selected.enabled(Feature.MACAW_LANTERN_SABLE));
            assertTrue(selected.mixins(client).contains("macaw.SableLanternRayMixin"));
            assertFalse(selected.mixins(client).contains("macaw.LanternRayMixin"));
            var absent = FeatureSelection.select(settings, Map.of("mcwbridges", "3.1.2"), client);
            assertFalse(absent.enabled(Feature.MACAW_LANTERN_SABLE));
            assertTrue(absent.mixins(client).contains("macaw.LanternRayMixin"));
            assertFalse(FeatureSelection.select(only(Feature.MACAW_LANTERN_SABLE), supported, client)
                    .enabled(Feature.MACAW_LANTERN_SABLE));
            var future = Map.of("mcwbridges", "3.1.2", "sable", "future");
            assertThrows(IllegalStateException.class, () -> FeatureSelection.select(settings, future, client));
            assertFalse(FeatureSelection.select(only(Feature.MACAW_LANTERN), future, client)
                    .mixins(client).contains("macaw.SableLanternRayMixin"));
        }
    }

    @Test void propertyWriterRequiresBothExactOptionalDependenciesOnEitherSide() {
        var policy = only(Feature.BETTER_VILLAGE_CONFIG);
        for (boolean client : new boolean[]{false, true}) {
            assertTrue(FeatureSelection.select(policy, Map.of(), client).mixins(client).isEmpty());
            assertTrue(FeatureSelection.select(policy, Map.of("libraryferret", "future"), client).mixins(client).isEmpty());
            assertEquals(java.util.List.of("BetterVillageConfigMixin"), FeatureSelection.select(policy,
                    Map.of("libraryferret", "4.0.0", "bettervillage", "3.3.1"), client).mixins(client));
            var future = Map.of("libraryferret", "future", "bettervillage", "3.3.1");
            assertTrue(FeatureSelection.select(only(), future, client).mixins(client).isEmpty());
            assertThrows(IllegalStateException.class, () -> FeatureSelection.select(policy, future, client));
        }
    }

    @Test void prerequisitesPrecedeTheirDependents() {
        for (var feature : Feature.values()) {
            var prerequisite = FeatureSelection.prerequisite(feature);
            if (prerequisite != null) assertTrue(prerequisite.ordinal() < feature.ordinal(), feature.key);
        }
    }

    @Test void rotatedGlassNeedsOnlyRechiseledAndOnlyOnClients() {
        var settings = only(Feature.RECHISELED_GLASS);
        var selected = FeatureSelection.select(settings, Map.of("rechiseled", "1.2.6"), true);
        assertEquals(java.util.List.of("client.RechiseledGlassCullingMixin"), selected.mixins(true));
        assertTrue(FeatureSelection.select(settings, Map.of("create", "6.0.10", "fusion", "1.3.16"), true).active().isEmpty());
        assertTrue(FeatureSelection.select(settings, Map.of("rechiseled", "future"), false).mixins(false).isEmpty());
        assertTrue(FeatureSelection.select(only(), Map.of("rechiseled", "future"), true).mixins(true).isEmpty());
        assertThrows(IllegalStateException.class, () -> FeatureSelection.select(settings, Map.of("rechiseled", "future"), true));
    }
    @Test void windowCullingIsCreateOnlyAndPhysicalClientOnly() {
        var settings = only(Feature.CREATE_WINDOWS);
        var selected = FeatureSelection.select(settings, Map.of("create", "6.0.10"), true);
        assertTrue(selected.enabled(Feature.CREATE_WINDOWS));
        assertEquals(java.util.List.of("client.CreateWindowCullingMixin"), selected.mixins(true));
        assertTrue(FeatureSelection.select(settings, Map.of("create", "6.0.10", "fusion", "future",
                "rechiseled", "future", "rechiseledcreate", "future"), true).enabled(Feature.CREATE_WINDOWS));
        for (var absent : java.util.List.of(Map.<String, String>of(),
                Map.of("fusion", "1.3.16", "rechiseled", "1.2.6", "rechiseledcreate", "1.1.1")))
            assertTrue(FeatureSelection.select(settings, absent, true).active().isEmpty());
        assertTrue(FeatureSelection.select(settings, Map.of("create", "future"), false).mixins(false).isEmpty());
        assertTrue(FeatureSelection.select(only(), Map.of("create", "future"), true).mixins(true).isEmpty());
        assertThrows(IllegalStateException.class, () -> FeatureSelection.select(settings, Map.of("create", "future"), true));
    }
    @Test void overviewNamesEveryConfigurableFeature() throws Exception {
        try (var stream = getClass().getResourceAsStream("/assets/skyweave_compat/lang/en_us.json")) {
            assertNotNull(stream);
            var labels = com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(stream,
                    java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
            for (var feature : Feature.values()) {
                var key = "skyweave_compat.configuration.feature." + feature.key;
                assertTrue(labels.has(key) && !labels.get(key).getAsString().isBlank(), key);
            }
        }
    }
    @Test void constructRecoveryRequiresBothTargetsAndSupportsIntegratedServers() {
        var settings = only(Feature.SABLE_GRAVES);
        for (boolean client : new boolean[]{true, false}) {
            assertTrue(FeatureSelection.select(settings, Map.of("sable", "2.0.6", "yigd", "2.0.13"), client)
                    .enabled(Feature.SABLE_GRAVES));
            assertTrue(FeatureSelection.select(settings, Map.of("sable", "2.0.6"), client).active().isEmpty());
            assertTrue(FeatureSelection.select(settings, Map.of("yigd", "2.0.13"), client).active().isEmpty());
            assertTrue(FeatureSelection.select(only(), Map.of("sable", "future", "yigd", "future"), client).active().isEmpty());
        }
    }
    @Test void radioTimelineNeedsOnlyTheAudioPairOnPhysicalClients() {
        var settings = only(Feature.AUDIO_TIMELINE);
        var versions = Map.of("analogaudio", "0.1.5-hotfix.1", "analoglib", "0.1.0");
        assertTrue(FeatureSelection.select(settings, versions, true).enabled(Feature.AUDIO_TIMELINE));
        assertTrue(FeatureSelection.select(settings, versions, false).active().isEmpty());
        assertTrue(FeatureSelection.select(settings, Map.of("analogaudio", "0.1.5-hotfix.1"), true).active().isEmpty());
        assertTrue(FeatureSelection.select(settings, Map.of("analoglib", "0.1.0"), true).active().isEmpty());
        assertTrue(FeatureSelection.select(only(), Map.of("analoglib", "future", "analogaudio", "future"), true).active().isEmpty());
        assertThrows(IllegalStateException.class, () -> FeatureSelection.select(settings,
                Map.of("analoglib", "future", "analogaudio", "0.1.5-hotfix.1"), true));
    }
    @Test void linkageRequiresItsCompletePairAndKeepsJadeIndependent() {
        var settings = only(Feature.LINKAGE_INTERACTIONS, Feature.LINKAGE_JADE);
        var versions = new HashMap<>(Map.of("aeronautics_utility_objects", "0.2.9", "create", "6.0.10",
                "sable", "2.0.6", "simulated", "1.3.2", "jade", "15.10.6+neoforge"));
        for (boolean client : new boolean[]{false, true}) {
            var selection = FeatureSelection.select(settings, versions, client);
            assertTrue(selection.enabled(Feature.LINKAGE_INTERACTIONS));
            assertTrue(selection.enabled(Feature.LINKAGE_JADE));
            assertEquals(client, selection.mixins(client).contains("client.LinkageRodInputMixin"));
        }
        versions.remove("jade");
        assertFalse(FeatureSelection.select(settings, versions, false).enabled(Feature.LINKAGE_JADE));
        assertTrue(FeatureSelection.select(settings, versions, false).enabled(Feature.LINKAGE_INTERACTIONS));
        versions.remove("simulated");
        assertTrue(FeatureSelection.select(settings, versions, false).active().isEmpty());
        versions.put("simulated", "future");
        assertThrows(IllegalStateException.class, () -> FeatureSelection.select(settings, versions, false));
        assertTrue(FeatureSelection.select(only(), versions, false).active().isEmpty());
        versions.remove("aeronautics_utility_objects");
        assertTrue(FeatureSelection.select(settings, versions, false).active().isEmpty());
        assertTrue(FeatureSelection.select(settings, Map.of(), true).active().isEmpty());
    }
    @Test void linearGuidanceRequiresItsApisAndAnActiveCarriageAdapter() {
        var versions = new HashMap<>(Map.of("linearbearing", "1.3.5", "create", "6.0.10",
                "sable", "2.0.6", "simulated", "1.3.2", "ponder", "1.0.87+mc1.21.1", "jade", "15.10.6+neoforge"));
        var settings = only(Feature.LINEAR_CARRIAGE, Feature.LINEAR_PONDER, Feature.LINEAR_JADE);
        assertTrue(FeatureSelection.select(settings, versions, true).enabled(Feature.LINEAR_PONDER));
        assertTrue(FeatureSelection.select(settings, versions, false).enabled(Feature.LINEAR_JADE));
        assertFalse(FeatureSelection.select(settings, versions, false).enabled(Feature.LINEAR_PONDER));
        assertTrue(FeatureSelection.select(only(Feature.LINEAR_PONDER, Feature.LINEAR_JADE), versions, true).active().isEmpty());
        versions.remove("jade");
        assertFalse(FeatureSelection.select(settings, versions, true).enabled(Feature.LINEAR_JADE));
        versions.put("jade", "future");
        assertFalse(FeatureSelection.select(only(Feature.LINEAR_CARRIAGE, Feature.LINEAR_PONDER), versions, true).enabled(Feature.LINEAR_JADE));
        assertThrows(IllegalStateException.class, () -> FeatureSelection.select(settings, versions, true));
        versions.remove("ponder");
        assertFalse(FeatureSelection.select(only(Feature.LINEAR_CARRIAGE, Feature.LINEAR_PONDER), versions, true).enabled(Feature.LINEAR_PONDER));
    }
    @Test void linearMachineryNeedsItsOwnApisAndSurvivesStandaloneFixCoexistence() {
        var settings = only(Feature.LINEAR_BOOTSTRAP, Feature.LINEAR_CARRIAGE, Feature.LINEAR_TRANSMISSION, Feature.LINEAR_GYRO);
        var versions = new HashMap<>(Map.of("linearbearing", "1.3.5", "linearbearingsfix", "1.0.3",
                "create", "6.0.10", "sable", "2.0.6", "simulated", "1.3.2"));
        for (boolean client : new boolean[]{false, true}) {
            var selection = FeatureSelection.select(settings, versions, client);
            assertEquals(java.util.Set.of(Feature.LINEAR_CARRIAGE, Feature.LINEAR_TRANSMISSION, Feature.LINEAR_GYRO), selection.active());
            assertEquals(8, selection.mixins(client).size());
        }
        versions.remove("simulated");
        var noSpringApi = FeatureSelection.select(settings, versions, false);
        assertFalse(noSpringApi.enabled(Feature.LINEAR_TRANSMISSION));
        assertFalse(noSpringApi.enabled(Feature.LINEAR_CARRIAGE));
        assertTrue(noSpringApi.enabled(Feature.LINEAR_GYRO));
        versions.remove("sable");
        assertTrue(FeatureSelection.select(settings, versions, false).active().isEmpty());
        versions.replaceAll((id, version) -> "future");
        assertTrue(FeatureSelection.select(only(), versions, false).active().isEmpty());
    }
    @Test void linearBootstrapIsOptionalAndDefersOnlyItsOverlappingRepairs() {
        var settings = only(Feature.LINEAR_BOOTSTRAP);
        var versions = new HashMap<>(Map.of("linearbearing", "1.3.5"));
        assertEquals(java.util.List.of("linear.LinearBootstrapMixin"),
                FeatureSelection.select(settings, versions, false).mixins(false));
        var client = FeatureSelection.select(settings, versions, true);
        assertTrue(client.enabled(Feature.LINEAR_BOOTSTRAP), "client still needs the gated loot pack");
        assertTrue(client.mixins(true).isEmpty(), "client initialization must remain unchanged");
        assertTrue(FeatureSelection.select(settings, Map.of(), false).active().isEmpty());
        versions.put("linearbearingsfix", "1.0.3");
        var coexist = FeatureSelection.select(settings, versions, false);
        assertTrue(coexist.active().isEmpty());
        assertEquals("provided by Linear Bearings Fix 1.0.3", coexist.inactive().get(Feature.LINEAR_BOOTSTRAP));
        versions.put("linearbearingsfix", "future");
        assertThrows(IllegalStateException.class, () -> FeatureSelection.select(settings, versions, false));
        versions.put("linearbearing", "future");
        assertTrue(FeatureSelection.select(only(), versions, false).active().isEmpty());
        versions.remove("linearbearingsfix");
        assertThrows(IllegalStateException.class, () -> FeatureSelection.select(settings, versions, false));
    }
    @Test void constructAdaptersRespectIndependentDependenciesDisableAndPhysicalSide() {
        var both = only(Feature.CONSTRUCT_DISTANCE, Feature.CONSTRUCT_OCCLUSION);
        var versions = new HashMap<>(SUPPORTED);
        assertEquals(2, FeatureSelection.select(both, versions, true).active().size());
        versions.remove("distanthorizons");
        var noDh = FeatureSelection.select(both, versions, true);
        assertTrue(noDh.enabled(Feature.CONSTRUCT_DISTANCE));
        assertFalse(noDh.enabled(Feature.CONSTRUCT_OCCLUSION));
        assertFalse(noDh.mixins(true).contains("client.ConstructShaderMixin"));
        for (String id : java.util.List.of("ssrd", "sable", "iris")) {
            var absent = new HashMap<>(SUPPORTED); absent.remove(id);
            assertTrue(FeatureSelection.select(both, absent, true).active().isEmpty());
        }
        versions.put("ssrd", "future");
        assertTrue(FeatureSelection.select(only(), versions, true).active().isEmpty());
        assertTrue(FeatureSelection.select(both, versions, false).mixins(false).isEmpty());
        assertThrows(IllegalStateException.class, () -> FeatureSelection.select(both, versions, true));
        assertTrue(FeatureSelection.select(only(Feature.CONSTRUCT_OCCLUSION), SUPPORTED, true)
                .enabled(Feature.CONSTRUCT_OCCLUSION));
    }

    @Test void mosaicGlassNeedsOnlyItsOptionalClientTarget() {
        var selected = only(Feature.MACAW_GLASS);
        assertTrue(FeatureSelection.select(selected, Map.of("mcwwindows", "2.4.2"), true).enabled(Feature.MACAW_GLASS));
        assertTrue(FeatureSelection.select(selected, Map.of(), true).active().isEmpty());
        assertTrue(FeatureSelection.select(selected, Map.of("mcwwindows", "future"), false).active().isEmpty());
        assertTrue(FeatureSelection.select(only(), Map.of("mcwwindows", "future"), true).active().isEmpty());
        assertThrows(IllegalStateException.class, () -> FeatureSelection.select(selected, Map.of("mcwwindows", "future"), true));
    }

    private static final Map<String, String> SUPPORTED = Map.ofEntries(
            Map.entry("sky_archipelago", "1.4"), Map.entry("skyvillages", "1.0.6"),
            Map.entry("architectury", "13.0.11"), Map.entry("sable", "2.0.6"),
            Map.entry("aeronautics_delivery_quests", "1.1.1"), Map.entry("distanthorizons", "3.3.3"),
            Map.entry("bluemap", "5.7"), Map.entry("surveyor", "1.2.4_1.21"), Map.entry("analogaudio", "0.1.5-hotfix.1"),
            Map.entry("create", "6.0.10"), Map.entry("veil", "4.3.2"),
            Map.entry("create_confectionery", "1.1.3."), Map.entry("dndecor", "2.2b"), Map.entry("bits_n_bobs", "2.3.5"),
            Map.entry("mcwwindows", "2.4.2"), Map.entry("ssrd", "1.8.8"), Map.entry("iris", "1.8.14-beta.1+mc1.21.1"), Map.entry("sodium", "0.8.13+mc1.21.1"),
            Map.entry("sabledynlights", "2.0.1"), Map.entry("lambdynlights", "4.8.11+1.21.1"),
            Map.entry("flywheel", "1.0.6"), Map.entry("colorwheel", "1.3.2+mc1.21.1"),
            Map.entry("aeronautics", "1.3.2"), Map.entry("questlog", "3.5.0"));

    @Test void colorwheelBridgeRequiresItsOwnerAndRespectsAbsenceDisableAndSide() {
        var settings = only(Feature.MATERIAL_IDS, Feature.COLORWHEEL_MATERIALS);
        assertTrue(FeatureSelection.select(settings, SUPPORTED, true).enabled(Feature.COLORWHEEL_MATERIALS));
        assertFalse(FeatureSelection.select(only(Feature.COLORWHEEL_MATERIALS), SUPPORTED, true)
                .enabled(Feature.COLORWHEEL_MATERIALS));
        var versions = new HashMap<>(SUPPORTED);
        versions.remove("colorwheel");
        var missing = FeatureSelection.select(settings, versions, true);
        assertTrue(missing.enabled(Feature.MATERIAL_IDS));
        assertFalse(missing.enabled(Feature.COLORWHEEL_MATERIALS));
        versions.put("colorwheel", "future");
        assertFalse(FeatureSelection.select(only(Feature.MATERIAL_IDS), versions, true)
                .enabled(Feature.COLORWHEEL_MATERIALS));
        assertFalse(FeatureSelection.select(settings, versions, false).enabled(Feature.COLORWHEEL_MATERIALS));
        assertThrows(IllegalStateException.class, () -> FeatureSelection.select(settings, versions, true));
    }

    @Test void journalFeaturesNeedOnlyQuestlogAndRespectPhysicalSides() {
        var settings = only(Feature.QUESTLOG_INTERFACE, Feature.QUESTLOG_NOTIFICATIONS, Feature.QUESTLOG_REWARD_VALIDATION);
        var versions = Map.of("questlog", "3.5.3");
        assertEquals(3, FeatureSelection.select(settings, Map.of("questlog", "3.5.0"), true).active().size());
        assertEquals(3, FeatureSelection.select(settings, versions, true).active().size());
        var server = FeatureSelection.select(settings, versions, false);
        assertEquals(java.util.Set.of(Feature.QUESTLOG_REWARD_VALIDATION), server.active());
        assertEquals(java.util.List.of("questlog.RewardClaimMixin"), server.mixins(false));
        assertTrue(FeatureSelection.select(settings, Map.of(), true).active().isEmpty());
        assertTrue(FeatureSelection.select(only(), Map.of("questlog", "future"), true).active().isEmpty());
        assertThrows(IllegalStateException.class,
                () -> FeatureSelection.select(settings, Map.of("questlog", "future"), true));
        assertTrue(FeatureSelection.select(only(Feature.QUESTLOG_INTERFACE, Feature.QUESTLOG_NOTIFICATIONS),
                Map.of("questlog", "future"), false).active().isEmpty());
    }

    @Test void lightCleanupNeedsNeitherSableNorSodium() {
        var versions = Map.of("sabledynlights", "2.0.1", "lambdynlights", "4.8.11+1.21.1", "create", "6.0.10");
        assertEquals(8, FeatureSelection.select(only(Feature.LIGHTING_LIFECYCLE), versions, true).mixins(true).size());
        assertTrue(FeatureSelection.select(only(Feature.LIGHTING_LIFECYCLE), versions, false).active().isEmpty());
    }

    @Test void colorwheelSamplerRepairNeedsNeitherSableNorTerrain() {
        var settings = only(Feature.COLORWHEEL_SAMPLERS);
        var versions = new HashMap<>(Map.of("colorwheel", "1.3.2+mc1.21.1",
                "iris", "1.8.14-beta.1+mc1.21.1", "flywheel", "1.0.6"));
        assertEquals(java.util.List.of("client.ColorwheelSamplerMixin"),
                FeatureSelection.select(settings, versions, true).mixins(true));
        for (var missing : java.util.List.copyOf(versions.keySet())) {
            var incomplete = new HashMap<>(versions); incomplete.remove(missing);
            incomplete.replaceAll((id, version) -> "unsupported");
            assertTrue(FeatureSelection.select(settings, incomplete, true).active().isEmpty());
        }
        versions.replaceAll((id, version) -> "unsupported");
        assertTrue(FeatureSelection.select(only(), versions, true).active().isEmpty());
        assertTrue(FeatureSelection.select(settings, versions, false).active().isEmpty());
        assertThrows(IllegalStateException.class, () -> FeatureSelection.select(settings, versions, true));
    }

    @Test void sodiumLightingNeedsEveryBridgeButNotSableOrShaders() {
        var versions = new HashMap<>(Map.of("sabledynlights", "2.0.1", "lambdynlights", "4.8.11+1.21.1",
                "sodium", "0.8.13+mc1.21.1", "create", "6.0.10"));
        var settings = only(Feature.SODIUM_LIGHTING);
        assertEquals(java.util.List.of("client.SodiumLightingMixin"),
                FeatureSelection.select(settings, versions, true).mixins(true));
        for (var missing : java.util.List.copyOf(versions.keySet())) {
            var incomplete = new HashMap<>(versions); incomplete.remove(missing);
            incomplete.replaceAll((id, version) -> "unsupported");
            assertTrue(FeatureSelection.select(settings, incomplete, true).active().isEmpty());
        }
        versions.replaceAll((id, version) -> "unsupported");
        assertTrue(FeatureSelection.select(only(), versions, true).active().isEmpty());
        assertTrue(FeatureSelection.select(settings, versions, false).active().isEmpty());
        assertThrows(IllegalStateException.class, () -> FeatureSelection.select(settings, versions, true));
    }

    private static FeatureSettings only(Feature... features) {
        var values = new EnumMap<Feature, Boolean>(Feature.class);
        for (var feature : Feature.values()) values.put(feature, false);
        for (var feature : features) values.put(feature, true);
        return new FeatureSettings(values);
    }

    @Test void surveyorRejectsNoOptionalVersionsUntilItsWholePairIsEnabled() {
        var feature = Feature.SURVEYOR_STRUCTURES;
        var pair = FeatureSelection.dependencies(feature);
        for (boolean client : new boolean[]{false,true}) {
            assertEquals(java.util.Set.of(feature),FeatureSelection.select(only(feature),pair,client).active());
            assertEquals(List.of("SurveyorStructureMixin", "SurveyorLayoutMixin", "SurveyorPieceAccess"),
                    FeatureSelection.select(only(feature),pair,client).mixins(client));
            for (String missing : pair.keySet()) {
                var incomplete = new HashMap<>(pair);
                incomplete.remove(missing); incomplete.replaceAll((id,version)->"unsupported");
                assertTrue(FeatureSelection.select(only(feature),incomplete,client).mixins(client).isEmpty());
            }
            var future = new HashMap<>(pair); future.put("surveyor","future");
            assertTrue(FeatureSelection.select(only(),future,client).mixins(client).isEmpty());
            assertThrows(IllegalStateException.class,()->FeatureSelection.select(only(feature),future,client));
        }
    }

    @Test void pinnedFabricVersionsAcceptOnlyTheTwoObservedLoaderSpellings() {
        for(var feature:List.of(Feature.SURVEYOR_STRUCTURES,Feature.HOOFPRINT_ZOOM)) {
            var versions=new HashMap<>(FeatureSelection.dependencies(feature));
            versions.computeIfPresent("surveyor",(id,version)->"1.2.4+1.21");
            versions.computeIfPresent("hoofprint",(id,version)->"1.3.0+1.21");
            assertTrue(FeatureSelection.select(only(feature),versions,true).enabled(feature));
            if(feature==Feature.SURVEYOR_STRUCTURES)
                assertTrue(FeatureSelection.select(only(feature),versions,false).enabled(feature));
            for(String unsupported:List.of("1.2.5+1.21","1.2.4+1.21.1","1.2.4_extra")) {
                var changed=new HashMap<>(versions);changed.put("surveyor",unsupported);
                assertThrows(IllegalStateException.class,()->FeatureSelection.select(only(feature),changed,true));
            }
            if(feature==Feature.HOOFPRINT_ZOOM) {
                versions.put("hoofprint","1.3.1+1.21");
                assertThrows(IllegalStateException.class,()->FeatureSelection.select(only(feature),versions,true));
            }
        }
    }

    @Test void noOptionalDependenciesSelectsOnlyNativeWayfinderOnEitherSide() {
        for (boolean client : new boolean[]{true, false}) {
            var selection = FeatureSelection.select(new FeatureSettings(Map.of()), Map.of(), client);
            assertEquals(java.util.Set.of(Feature.WAYFINDER),selection.active());
            assertEquals(List.of("StructureSearchCacheAccess"),selection.mixins(client));
        }
    }

    @Test void wayfinderDhRequiresClientAndExplicitlyEnabledNativeFeature() {
        var bad = Map.of("distanthorizons","unsupported");
        assertEquals(java.util.Set.of(Feature.WAYFINDER),
                FeatureSelection.select(only(Feature.WAYFINDER,Feature.WAYFINDER_DH),bad,false).active());
        assertEquals(java.util.Set.of(Feature.WAYFINDER),
                FeatureSelection.select(only(Feature.WAYFINDER),bad,true).active());
        assertThrows(IllegalStateException.class,()->FeatureSelection.select(only(Feature.WAYFINDER,Feature.WAYFINDER_DH),bad,true));
        assertEquals(java.util.Set.of(Feature.WAYFINDER,Feature.WAYFINDER_DH),FeatureSelection.select(
                only(Feature.WAYFINDER,Feature.WAYFINDER_DH),Map.of("distanthorizons","3.3.3"),true).active());
    }

    @Test void allDisabledIgnoresInstalledVersionsAndLegacyPatches() {
        var versions = new HashMap<String, String>();
        SUPPORTED.keySet().forEach(id -> versions.put(id, "unsupported"));
        versions.put("veil_iris_fix", "0.1.0");
        versions.put("sky_village_neighbors", "1.0");
        var selection = FeatureSelection.select(only(), versions, true);
        assertTrue(selection.active().isEmpty());
        assertTrue(selection.mixins(true).isEmpty());
    }

    @Test void independentFixesDoNotNeedTerrainOrItsClasses() {
        var selection = FeatureSelection.select(only(Feature.ANALOG_AUDIO, Feature.DH_HANDOFF, Feature.FRAMEBUFFER),
                Map.of("analogaudio", "0.1.5-hotfix.1", "create", "6.0.10", "distanthorizons", "3.3.3",
                        "veil", "4.3.2", "iris", "1.8.14-beta.1+mc1.21.1"), true);
        assertEquals(3, selection.active().size());
        assertEquals(3, selection.mixins(true).size());
        assertFalse(selection.enabled(Feature.TERRAIN));
    }

    @Test void serverNeverSelectsClientAdaptersOrValidatesTheirVersions() {
        var versions = new HashMap<>(SUPPORTED);
        versions.put("iris", "unsupported");
        var selection = FeatureSelection.select(new FeatureSettings(Map.of()), versions, false);
        assertFalse(selection.enabled(Feature.FRAMEBUFFER));
        assertFalse(selection.enabled(Feature.BALLOON));
        assertFalse(selection.enabled(Feature.MATERIAL_IDS));
        assertFalse(selection.enabled(Feature.MODEL_DATA));
        assertFalse(selection.enabled(Feature.SINGLE_BLOCK_PASSES));
        assertTrue(selection.mixins(false).stream().noneMatch(name -> name.startsWith("client.")));
        assertTrue(selection.enabled(Feature.BLUEMAP));
    }

    @Test void singleBlockPassesAreIndependentAndNeedTheirRendererStack() {
        var selection = FeatureSelection.select(only(Feature.SINGLE_BLOCK_PASSES), SUPPORTED, true);
        assertEquals(java.util.List.of("client.SingleBlockPassMixin"), selection.mixins(true));
        var missing = new HashMap<>(SUPPORTED);
        missing.remove("iris");
        assertTrue(FeatureSelection.select(only(Feature.SINGLE_BLOCK_PASSES), missing, true).active().isEmpty());
        missing.put("iris", "unsupported");
        assertTrue(FeatureSelection.select(only(), missing, true).active().isEmpty());
    }

    @Test void activeAdaptersRejectUnsupportedVersionsWithConfigurationRemedy() {
        var versions = new HashMap<>(SUPPORTED);
        versions.put("iris", "unsupported");
        var failure = assertThrows(IllegalStateException.class,
                () -> FeatureSelection.select(only(Feature.FRAMEBUFFER), versions, true));
        assertTrue(failure.getMessage().contains(Feature.FRAMEBUFFER.key));
        assertTrue(failure.getMessage().contains(FeatureSettings.FILE));
    }

    @Test void disabledPrerequisitesAreNotSilentlyEnabled() {
        var selection = FeatureSelection.select(only(Feature.PRESSURE, Feature.DELIVERY_CHUNKS), SUPPORTED, false);
        assertTrue(selection.active().isEmpty());
        assertTrue(selection.inactive().get(Feature.PRESSURE).contains(Feature.TERRAIN.key));
    }

    @Test void missingCounterpartDoesNotValidateAnInactiveAdapter() {
        var selection = FeatureSelection.select(only(Feature.ANALOG_AUDIO), Map.of("analogaudio", "future"), false);
        assertTrue(selection.active().isEmpty());
        assertEquals("missing create", selection.inactive().get(Feature.ANALOG_AUDIO));
    }

    @Test void oldFramebufferPatchConflictsOnlyWhenReplacementActive() {
        var versions = new HashMap<>(SUPPORTED);
        versions.put("veil_iris_fix", "0.1.0");
        assertThrows(IllegalStateException.class,
                () -> FeatureSelection.select(only(Feature.FRAMEBUFFER), versions, true));
        assertTrue(FeatureSelection.select(only(), versions, true).active().isEmpty());
    }

    @Test void deliverySwitchesSelectSeparateHooks() {
        for (var feature : new Feature[]{Feature.DELIVERY_DISTANCE, Feature.DELIVERY_CHUNKS, Feature.DELIVERY_LANDING}) {
            var selection = FeatureSelection.select(only(Feature.TERRAIN, feature), SUPPORTED, false);
            assertEquals(1, selection.mixins(false).stream().filter(name -> name.startsWith("Delivery")).count());
        }
    }

    @Test void settingsRejectMistakesAndKeepOperatorValues(@TempDir Path directory) throws Exception {
        assertThrows(IllegalArgumentException.class, () -> FeatureSettings.read(new StringReader("terrain.preset=maybe")));
        assertThrows(IllegalArgumentException.class, () -> FeatureSettings.read(new StringReader("terrain.presett=false")));
        assertThrows(IllegalArgumentException.class, () -> FeatureSettings.read(new StringReader("terrain.preset=true\nterrain.preset=false")));
        var defaults = FeatureSettings.load(directory);
        assertTrue(defaults.enabled(Feature.TERRAIN));
        for (Feature feature : Feature.values()) assertTrue(defaults.enabled(feature), feature.key);
        Files.writeString(directory.resolve(FeatureSettings.FILE), "terrain.preset=false\n");
        assertFalse(FeatureSettings.load(directory).enabled(Feature.TERRAIN));
        assertEquals("terrain.preset=false\n", Files.readString(directory.resolve(FeatureSettings.FILE)));
    }

    @Test void firstStartupRetainsVillageOptOutWithoutKeepingTwoAuthorities(@TempDir Path directory) throws Exception {
        var legacy = directory.resolve("skyweave_compat-common.toml");
        Files.writeString(legacy, "enabled = false # operator choice\n");
        assertFalse(FeatureSettings.load(directory).enabled(Feature.VILLAGE_CLEARANCE));
        Files.writeString(legacy, "enabled = true\n");
        assertFalse(FeatureSettings.load(directory).enabled(Feature.VILLAGE_CLEARANCE));
    }
}
