package design.inherent.skyweavecompat;

import design.inherent.skyweavecompat.configuration.SettingsCatalog;
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
    @Test void snowConsumersRequireTheirActualProviderAndClimate() {
        for (var feature : List.of(Feature.SNOW_REAL_MAGIC_CLIMATE, Feature.SNOW_UNDER_TREES_CLIMATE)) {
            var mods = new HashMap<String,String>(FeatureSelection.dependencies(feature)); mods.put("sky_archipelago", "1.4");
            var settings = only(Feature.TERRAIN, Feature.CLIMATE, feature);
            assertTrue(FeatureSelection.select(settings, mods, false).enabled(feature));
            assertFalse(FeatureSelection.select(only(feature), mods, false).enabled(feature));
            String provider = FeatureSelection.dependencies(feature).keySet().iterator().next();
            mods.put(provider, "future");
            assertThrows(IllegalStateException.class, () -> FeatureSelection.select(settings, mods, false));
            assertFalse(FeatureSelection.select(only(Feature.TERRAIN, Feature.CLIMATE), mods, false).enabled(feature));
            mods.remove(provider);
            assertFalse(FeatureSelection.select(settings, mods, false).enabled(feature));
        }
    }
    @Test void bowHandoffIsExactlyOptionalAndClientScoped() {
        var feature = Feature.BOW_MODEL_HANDOFF;
        var mods = new HashMap<String,String>(FeatureSelection.dependencies(feature));
        assertTrue(FeatureSelection.select(only(feature), mods, true).enabled(feature));
        assertTrue(FeatureSelection.select(only(feature), mods, false).mixins(false).isEmpty());
        for (var provider : List.of("punchy", "modefite")) {
            var changed = new HashMap<>(mods); changed.put(provider, "future");
            assertThrows(IllegalStateException.class, () -> FeatureSelection.select(only(feature), changed, true));
            assertTrue(FeatureSelection.select(only(), changed, true).mixins(true).isEmpty());
            changed.remove(provider);
            assertTrue(FeatureSelection.select(only(feature), changed, true).mixins(true).isEmpty());
        }
    }

    @Test void grassriseRecipesAreExactOptionalAndClientOnly() {
        var feature=Feature.GRASSRISE_SHADERS;
        var versions=Map.of("grassrise_better_lighting","2.0.0","iris","1.8.14-beta.1+mc1.21.1","sodium","0.8.13+mc1.21.1");
        assertTrue(FeatureSelection.select(only(feature),versions,true).enabled(feature));
        assertEquals(SettingsCatalog.Group.GRASSRISE,SettingsCatalog.group(feature));
        assertTrue(FeatureSelection.select(only(feature),versions,false).mixins(false).isEmpty());
        for(var mod:versions.keySet()) {
            var partial=new HashMap<>(versions);partial.remove(mod);
            assertTrue(FeatureSelection.select(only(feature),partial,true).mixins(true).isEmpty());
            var changed=new HashMap<>(versions);changed.put(mod,"future");
            assertThrows(IllegalStateException.class,()->FeatureSelection.select(only(feature),changed,true));
            assertTrue(FeatureSelection.select(only(),changed,true).mixins(true).isEmpty());
            assertTrue(FeatureSelection.select(only(feature),changed,false).mixins(false).isEmpty());
        }
    }

    @Test void initialClientLightingIsIndependentAndExactlyClientGated() {
        var feature=Feature.C2ME_CLIENT_LIGHT;
        assertTrue(feature.defaultEnabled);assertNull(FeatureSelection.prerequisite(feature));
        assertEquals(SettingsCatalog.Group.C2ME,SettingsCatalog.group(feature));
        var versions=Map.of("c2me","0.4.0-alpha.0.124+1.21.1","c2me_notickvd","0.4.0-alpha.0.124+1.21.1");
        assertEquals(List.of("client.InitialLightPacketMixin","client.InitialLightChunkMixin","client.InitialLightEngineMixin","client.InitialLightLevelMixin","client.InitialLightWorldMixin"),
                FeatureSelection.select(only(feature),versions,true).mixins(true));
        assertFalse(FeatureSelection.select(only(feature),versions,false).enabled(feature));
        assertTrue(FeatureSelection.select(only(feature),Map.of(),true).mixins(true).isEmpty());
        for(String mod:versions.keySet()) {
            var missing=new HashMap<>(versions);missing.remove(mod);
            assertTrue(FeatureSelection.select(only(feature),missing,true).mixins(true).isEmpty());
            var future=new HashMap<>(versions);future.put(mod,"future");
            assertTrue(FeatureSelection.select(only(),future,true).mixins(true).isEmpty());
            assertTrue(FeatureSelection.select(only(feature),future,false).mixins(false).isEmpty());
            assertThrows(IllegalStateException.class,()->FeatureSelection.select(only(feature),future,true));
        }
    }

    @Test void connectorRangesRemainOptionalAndQualifiedBeforeCommonTargetsResolve() {
        var feature = Feature.ROPE_CONNECTOR_RANGES;
        var versions = Map.of("create_aeronautics_throwable_rope_connector", "0.4.3", "simulated", "1.3.3");
        for (boolean client : List.of(false, true)) {
            var admitted = FeatureSelection.select(only(feature), versions, client);
            assertTrue(admitted.enabled(feature));
            assertEquals(List.of("RopeConnectorRangeMixin"), admitted.mixins(client));
            assertTrue(FeatureSelection.select(only(feature), Map.of(), client).mixins(client).isEmpty());
            assertTrue(FeatureSelection.select(only(feature), Map.of("create_aeronautics_throwable_rope_connector", "0.4.3"), client).mixins(client).isEmpty());
            var future = Map.of("create_aeronautics_throwable_rope_connector", "future", "simulated", "future");
            assertTrue(FeatureSelection.select(only(), future, client).mixins(client).isEmpty());
            assertThrows(IllegalStateException.class, () -> FeatureSelection.select(only(feature), future, client));
        }
        assertTrue(new FeatureSettings(Map.of()).enabled(feature));
        assertEquals(SettingsCatalog.Category.SHIPS, SettingsCatalog.group(feature).category);
    }
    @Test void cookeryInsertionIsOptionalCommonAndExactVersionGated() {
        var feature = Feature.COOKERY_MILLSTONE;
        for (boolean client : List.of(false, true)) {
            var admitted = FeatureSelection.select(only(feature), Map.of("kaleidoscope_cookery", "1.6.0-neoforge+mc1.21.1"), client);
            assertTrue(admitted.enabled(feature));
            assertEquals(List.of("inventory.CookeryMillstoneInputMixin"), admitted.mixins(client));
            assertTrue(FeatureSelection.select(only(feature), Map.of(), client).mixins(client).isEmpty());
            assertTrue(FeatureSelection.select(only(), Map.of("kaleidoscope_cookery", "future"), client).mixins(client).isEmpty());
            assertThrows(IllegalStateException.class, () -> FeatureSelection.select(only(feature), Map.of("kaleidoscope_cookery", "future"), client));
        }
        assertTrue(new FeatureSettings(Map.of()).enabled(feature));
        assertEquals(SettingsCatalog.Category.MACHINES, SettingsCatalog.group(feature).category);
    }
    @Test void powerGridInputRepairKeepsOptionalClientAdmissionAndIndependentOptOut() {
        var feature = Feature.POWER_GRID_KEY_MODIFIERS;
        var selected = FeatureSelection.select(only(feature), Map.of("powergrid", "0.6.2"), true);
        assertTrue(selected.enabled(feature)); assertEquals(7, selected.mixins(true).size());
        assertEquals(SettingsCatalog.Category.INTERFACE, SettingsCatalog.group(feature).category);
        assertTrue(FeatureSelection.select(only(feature), Map.of(), true).mixins(true).isEmpty());
        assertTrue(FeatureSelection.select(only(feature), Map.of("powergrid", "future"), false).mixins(false).isEmpty());
        assertTrue(FeatureSelection.select(only(), Map.of("powergrid", "future"), true).mixins(true).isEmpty());
        assertThrows(IllegalStateException.class, () -> FeatureSelection.select(only(feature), Map.of("powergrid", "future"), true));
    }
    @Test void effortlessModifierRepairUsesActualRuntimeVersionAndNoClientTargetsOnOptOutOrServer() {
        var feature = Feature.EFFORTLESS_KEY_MODIFIERS;
        var selected = FeatureSelection.select(only(feature), Map.of("effortlessbuilding", "4.2"), true);
        assertTrue(selected.enabled(feature)); assertEquals(7, selected.mixins(true).size());
        assertEquals(SettingsCatalog.Category.INTERFACE, SettingsCatalog.group(feature).category);
        assertTrue(FeatureSelection.select(only(feature), Map.of(), true).mixins(true).isEmpty());
        assertTrue(FeatureSelection.select(only(feature), Map.of("effortlessbuilding", "future"), false).mixins(false).isEmpty());
        assertTrue(FeatureSelection.select(only(), Map.of("effortlessbuilding", "future"), true).mixins(true).isEmpty());
        assertThrows(IllegalStateException.class, () -> FeatureSelection.select(only(feature), Map.of("effortlessbuilding", "future"), true));
    }
    @Test void movesModifierRepairIsIndependentClientOnlyAndPreservesExactAdmissionAndOptOut() {
        var feature = Feature.MOVES_KEY_MODIFIERS;
        var selected = FeatureSelection.select(only(feature), Map.of("punchy_moves", "1.0.9b"), true);
        assertTrue(selected.enabled(feature));
        assertEquals(8, selected.mixins(true).size());
        assertEquals(SettingsCatalog.Category.INTERFACE, SettingsCatalog.group(feature).category);
        assertTrue(FeatureSelection.select(only(feature), Map.of(), true).mixins(true).isEmpty());
        assertTrue(FeatureSelection.select(only(feature), Map.of("punchy_moves", "future"), false).mixins(false).isEmpty());
        assertTrue(FeatureSelection.select(only(), Map.of("punchy_moves", "future"), true).mixins(true).isEmpty());
        assertThrows(IllegalStateException.class, () -> FeatureSelection.select(only(feature), Map.of("punchy_moves", "future"), true));
        assertFalse(selected.enabled(Feature.MOVES_TOUCH_MOTION));
    }
    @Test void ipnTextProtectionRequiresExactClientPairAndKeepsOptOutAndOptionalSearchProvider() {
        var feature = Feature.IPN_TEXT_INPUT;
        var versions = Map.of("inventoryprofilesnext", "2.2.5", "libipn", "6.6.3");
        assertTrue(FeatureSelection.select(only(feature), versions, true).enabled(feature));
        assertEquals(6, FeatureSelection.select(only(feature), versions, true).mixins(true).size());
        assertTrue(FeatureSelection.select(only(feature), Map.of(), true).mixins(true).isEmpty());
        assertTrue(FeatureSelection.select(only(feature), Map.of("inventoryprofilesnext", "2.2.5"), true).mixins(true).isEmpty());
        var unsupported = Map.of("inventoryprofilesnext", "future", "libipn", "future", "emi", "future");
        assertTrue(FeatureSelection.select(only(), unsupported, true).mixins(true).isEmpty());
        assertTrue(FeatureSelection.select(only(feature), unsupported, false).mixins(false).isEmpty());
        assertThrows(IllegalStateException.class, () -> FeatureSelection.select(only(feature), unsupported, true));
        assertThrows(IllegalStateException.class, () -> FeatureSelection.select(only(feature),
                Map.of("inventoryprofilesnext", "2.2.5", "libipn", "6.6.3", "emi", "future"), true));
        assertTrue(FeatureSelection.select(only(feature), Map.of("inventoryprofilesnext", "2.2.5", "libipn", "6.6.3",
                "emi", "1.1.24+1.21.1+neoforge"), true).enabled(feature));
        assertEquals(SettingsCatalog.Category.INVENTORY, SettingsCatalog.group(feature).category);
    }
    @Test void typedDefinitionsRequireOnlyThePinnedClientOwnerAndRespectOptOut() {
        var feature = Feature.ITEM_DEFINITIONS;
        var selected = FeatureSelection.select(only(feature), Map.of("modefite", "1.0.1+1.21.1"), true);
        assertTrue(selected.enabled(feature));
        assertEquals(6, selected.mixins(true).size());
        assertTrue(selected.mixins(true).contains("client.definitions.DefinitionRenderingMixin"));
        assertTrue(FeatureSelection.select(only(feature), Map.of(), true).mixins(true).isEmpty());
        assertTrue(FeatureSelection.select(only(feature), Map.of("modefite", "future"), false).mixins(false).isEmpty());
        assertTrue(FeatureSelection.select(only(), Map.of("modefite", "future"), true).mixins(true).isEmpty());
        assertThrows(IllegalStateException.class, () -> FeatureSelection.select(only(feature), Map.of("modefite", "future"), true));
    }
    @Test void modernModelRotationsAreClientOnlyAndRespectOptionalVersionAndOptOut() {
        var feature = Feature.MODEL_ROTATIONS;
        assertEquals(List.of("client.MultiAxisModelMixin"), FeatureSelection.select(only(feature), Map.of("modefite", "1.0.1+1.21.1"), true).mixins(true));
        assertTrue(FeatureSelection.select(only(feature), Map.of(), true).mixins(true).isEmpty());
        assertTrue(FeatureSelection.select(only(feature), Map.of("modefite", "future"), false).mixins(false).isEmpty());
        assertTrue(FeatureSelection.select(only(), Map.of("modefite", "future"), true).mixins(true).isEmpty());
        assertThrows(IllegalStateException.class, () -> FeatureSelection.select(only(feature), Map.of("modefite", "future"), true));
    }
    @Test void trialAliasInteropRequiresOnlyTheTwoPinnedOwners() {
        var versions=Map.of("variantsandventures","1.0.30","lithostitched","1.8.1");
        for(boolean client:List.of(false,true)) {
            var selected=FeatureSelection.select(only(Feature.VARIANT_TRIAL_ALIASES),versions,client);
            assertTrue(selected.enabled(Feature.VARIANT_TRIAL_ALIASES));
            assertEquals(List.of("encounters.VariantsTrialAliasesMixin"),selected.mixins(client));
            assertTrue(FeatureSelection.select(only(Feature.VARIANT_TRIAL_ALIASES),Map.of("variantsandventures","1.0.30"),client).mixins(client).isEmpty());
            assertDoesNotThrow(()->FeatureSelection.select(only(),Map.of("variantsandventures","future","lithostitched","future"),client));
            assertThrows(IllegalStateException.class,()->FeatureSelection.select(only(Feature.VARIANT_TRIAL_ALIASES),Map.of("variantsandventures","1.0.30","lithostitched","future"),client));
        }
    }

    @Test void encountersKeepTheirIndependentMetadataAndHostBoundaries() {
        for(boolean client:List.of(false,true)) {
            var patrol=FeatureSelection.select(only(Feature.PATROL_CHANCE),Map.of(),client);
            assertEquals(java.util.Set.of(Feature.PATROL_CHANCE),patrol.active());
            assertEquals(List.of("encounters.PatrolChanceMixin"),patrol.mixins(client));
            var variants=Map.of("variantsandventures","1.0.30");
            assertTrue(FeatureSelection.select(only(Feature.VARIANT_SPAWNERS),variants,client).enabled(Feature.VARIANT_SPAWNERS));
            assertTrue(FeatureSelection.select(only(Feature.VARIANT_SPAWNERS),Map.of(),client).mixins(client).isEmpty());
            assertTrue(FeatureSelection.select(only(Feature.MURK_WATER),variants,client).mixins(client).isEmpty());
            var host=Map.of("variantsandventures","1.0.30","sky_archipelago","1.4");
            assertTrue(FeatureSelection.select(only(Feature.TERRAIN,Feature.MURK_WATER),host,client).enabled(Feature.MURK_WATER));
            assertFalse(FeatureSelection.select(only(Feature.TERRAIN,Feature.MURK_WATER),Map.of("sky_archipelago","1.4"),client).enabled(Feature.MURK_WATER));
            assertDoesNotThrow(()->FeatureSelection.select(only(),Map.of("variantsandventures","future"),client));
            assertThrows(IllegalStateException.class,()->FeatureSelection.select(only(Feature.VARIANT_SPAWNERS),Map.of("variantsandventures","future"),client));
        }
    }

    @Test void constructCoverageIsIndependentOfIrisAndSelectsNoServerOrAbsentTargets() {
        var feature = Feature.CONSTRUCT_COVERAGE;
        var mods = Map.of("ssrd", "1.8.8", "sable", "2.0.6", "distanthorizons", "3.3.3");
        var selected = FeatureSelection.select(only(feature), mods, true);
        assertTrue(selected.enabled(feature));
        assertTrue(selected.mixins(true).contains("dh.CoverageTransformMixin"));
        assertTrue(selected.mixins(true).contains("client.ConstructCoverageDrawMixin"));
        for (String absent : mods.keySet()) {
            var missing = new HashMap<>(mods); missing.remove(absent);
            assertTrue(FeatureSelection.select(only(feature), missing, true).mixins(true).isEmpty());
        }
        var future = Map.of("ssrd", "future", "sable", "future", "distanthorizons", "future");
        assertTrue(FeatureSelection.select(only(feature), future, false).mixins(false).isEmpty());
        assertTrue(FeatureSelection.select(only(), future, true).mixins(true).isEmpty());
        assertThrows(IllegalStateException.class, () -> FeatureSelection.select(only(feature), future, true));
    }

    @Test void heldMaterialsRequireOnlyTheirPinnedClientRenderers() {
        var feature = Feature.HELD_MATERIALS;
        var versions = Map.of("iris", "1.8.14-beta.1+mc1.21.1", "punchy", "3.2b");
        assertEquals(4, FeatureSelection.select(only(feature), versions, true).mixins(true).size());
        for (String absent : versions.keySet()) {
            var missing = new HashMap<>(versions); missing.remove(absent);
            assertTrue(FeatureSelection.select(only(feature), missing, true).mixins(true).isEmpty());
        }
        var future = Map.of("iris", "future", "punchy", "future");
        assertTrue(FeatureSelection.select(only(feature), future, false).mixins(false).isEmpty());
        assertTrue(FeatureSelection.select(only(), future, true).mixins(true).isEmpty());
        assertThrows(IllegalStateException.class, () -> FeatureSelection.select(only(feature), future, true));
    }

    @Test void irisHandEffectsAreOptionalAndDoNotRequirePunchy() {
        var feature = Feature.HAND_EFFECTS;
        var mods = Map.of("iris", "1.8.14-beta.1+mc1.21.1", "hmioutline", "2.4.0+mc1.21.1");
        assertTrue(FeatureSelection.select(only(feature), mods, true).enabled(feature));
        for (String absent : mods.keySet()) {
            var missing = new HashMap<>(mods); missing.remove(absent);
            assertTrue(FeatureSelection.select(only(feature), missing, true).mixins(true).isEmpty());
        }
        var future = Map.of("iris", "future", "hmioutline", "future");
        assertTrue(FeatureSelection.select(only(feature), future, false).mixins(false).isEmpty());
        assertTrue(FeatureSelection.select(only(), future, true).mixins(true).isEmpty());
        assertThrows(IllegalStateException.class, () -> FeatureSelection.select(only(feature), future, true));
    }

    @Test void movesTouchBridgeRequiresOnlyThePinnedClientOwners() {
        var feature = Feature.MOVES_TOUCH_MOTION;
        var versions = Map.of("punchy_moves", "1.0.9b", "touch_n_motion", "1.0.5");
        var selected = FeatureSelection.select(only(feature), versions, true);
        assertEquals(java.util.Set.of(feature), selected.active());
        assertEquals(List.of("client.MovesTouchMotionApiMixin", "client.TouchMotionCapeInvoker"), selected.mixins(true));
        assertTrue(feature.defaultEnabled);
        for (var missing : List.of(Map.of("punchy_moves", "1.0.9b"), Map.of("touch_n_motion", "1.0.5"), Map.<String,String>of()))
            assertTrue(FeatureSelection.select(only(feature), missing, true).mixins(true).isEmpty());
        var future = Map.of("punchy_moves", "future", "touch_n_motion", "future");
        assertTrue(FeatureSelection.select(only(feature), future, false).mixins(false).isEmpty());
        assertTrue(FeatureSelection.select(only(), future, true).mixins(true).isEmpty());
        assertThrows(IllegalStateException.class, () -> FeatureSelection.select(only(feature), future, true));
    }

    @Test void mainMenuIdentityIsOptionalAndPhysicalClientOnlyWithoutContentMods() {
        var settings = only(Feature.MAIN_MENU_IDENTITY);
        assertTrue(Feature.MAIN_MENU_IDENTITY.defaultEnabled);
        assertEquals(List.of("client.MainMenuBrandingMixin", "client.MainMenuLayoutMixin"), FeatureSelection.select(settings, Map.of(), true).mixins(true));
        assertTrue(FeatureSelection.select(settings, Map.of(), false).active().isEmpty());
        assertTrue(FeatureSelection.select(settings, Map.of(), false).mixins(false).isEmpty());
        assertTrue(FeatureSelection.select(only(), Map.of(), true).mixins(true).isEmpty());
    }
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
                Feature.CREATE_COPYCAT_BACKFACES, Feature.COPYCATS_BACKFACES,
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
    @Test void hydraulicBodyBindingIsIndependentOfInteractionPolicyAndForeignClasses() {
        var feature = Feature.LINKAGE_JOINT_BODIES;
        var versions = Map.of("aeronautics_utility_objects", "0.2.9", "sable", "2.0.6");
        for (boolean client : List.of(false, true)) {
            assertEquals(List.of("linkage.HydraulicJointBodiesMixin"),
                    FeatureSelection.select(only(feature), versions, client).mixins(client));
            for (var dependency : versions.keySet()) {
                var partial = new HashMap<>(versions); partial.remove(dependency);
                assertTrue(FeatureSelection.select(only(feature), partial, client).mixins(client).isEmpty());
                var future = new HashMap<>(versions); future.put(dependency, "future");
                assertThrows(IllegalStateException.class, () -> FeatureSelection.select(only(feature), future, client));
                assertTrue(FeatureSelection.select(only(), future, client).mixins(client).isEmpty());
            }
        }
        assertEquals(SettingsCatalog.Group.LINKAGE, SettingsCatalog.group(feature));
    }
    @Test void linkageRequiresItsCompletePairAndKeepsJadeIndependent() {
        var settings = only(Feature.LINKAGE_INTERACTIONS, Feature.LINKAGE_JADE);
        var versions = new HashMap<>(Map.of("aeronautics_utility_objects", "0.2.9", "create", "6.0.10",
                "sable", "2.0.6", "simulated", "1.3.3", "jade", "15.10.6+neoforge"));
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
                "sable", "2.0.6", "simulated", "1.3.3", "ponder", "1.0.87+mc1.21.1", "jade", "15.10.6+neoforge"));
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
                "create", "6.0.10", "sable", "2.0.6", "simulated", "1.3.3"));
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
            Map.entry("architectury", "13.0.12"), Map.entry("sable", "2.0.6"),
            Map.entry("aeronautics_delivery_quests", "1.1.1"), Map.entry("distanthorizons", "3.3.3"),
            Map.entry("bluemap", "5.7"), Map.entry("surveyor", "1.2.4_1.21"), Map.entry("analogaudio", "0.1.5-hotfix.1"),
            Map.entry("create", "6.0.10"), Map.entry("veil", "4.3.2"),
            Map.entry("create_confectionery", "1.1.3."), Map.entry("dndecor", "2.2b"), Map.entry("bits_n_bobs", "2.3.5"),
            Map.entry("mcwwindows", "2.4.2"), Map.entry("ssrd", "1.8.8"), Map.entry("iris", "1.8.14-beta.1+mc1.21.1"), Map.entry("sodium", "0.8.13+mc1.21.1"),
            Map.entry("sabledynlights", "2.0.1"), Map.entry("lambdynlights", "4.8.11+1.21.1"),
            Map.entry("flywheel", "1.0.6"), Map.entry("colorwheel", "1.3.2+mc1.21.1"),
            Map.entry("aeronautics", "1.3.3"), Map.entry("questlog", "3.5.0"));

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
        var versions = Map.of("questlog", "3.5.4");
        assertEquals(3, FeatureSelection.select(settings, Map.of("questlog", "3.5.0"), true).active().size());
        assertEquals(3, FeatureSelection.select(settings, Map.of("questlog", "3.5.3"), true).active().size());
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

    @Test void noOptionalDependenciesSelectsOnlyNativeFeaturesForThePhysicalSide() {
        for (boolean client : new boolean[]{true, false}) {
            var selection = FeatureSelection.select(new FeatureSettings(Map.of()), Map.of(), client);
            assertEquals(client ? java.util.Set.of(Feature.WAYFINDER, Feature.MAIN_MENU_IDENTITY, Feature.PATROL_CHANCE)
                    : java.util.Set.of(Feature.WAYFINDER, Feature.PATROL_CHANCE), selection.active());
            assertEquals(client ? List.of("client.MainMenuBrandingMixin", "client.MainMenuLayoutMixin", "encounters.PatrolChanceMixin", "StructureSearchCacheAccess")
                    : List.of("encounters.PatrolChanceMixin", "StructureSearchCacheAccess"), selection.mixins(client));
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

    @Test void deliverySearchUsesItsExactOptionalProviderAndEndpointCustody() {
        var enabled=only(Feature.TERRAIN,Feature.DELIVERY_CHUNKS,Feature.DELIVERY_STRUCTURE_SEARCH);
        var selected=FeatureSelection.select(enabled,SUPPORTED,false);
        assertTrue(selected.enabled(Feature.DELIVERY_STRUCTURE_SEARCH));
        assertTrue(selected.mixins(false).containsAll(List.of("DeliverySearchMixin","DeliverySearchCacheMixin","DeliverySearchAccess","DeliverySearchConfigAccess")));
        assertFalse(FeatureSelection.select(only(Feature.TERRAIN,Feature.DELIVERY_STRUCTURE_SEARCH),SUPPORTED,false).enabled(Feature.DELIVERY_STRUCTURE_SEARCH));
        var absent=new HashMap<>(SUPPORTED);absent.remove("aeronautics_delivery_quests");
        assertFalse(FeatureSelection.select(enabled,absent,false).enabled(Feature.DELIVERY_STRUCTURE_SEARCH));
        var future=new HashMap<>(SUPPORTED);future.put("aeronautics_delivery_quests","future");
        assertTrue(FeatureSelection.select(only(),future,false).active().isEmpty());
        assertThrows(IllegalStateException.class,()->FeatureSelection.select(enabled,future,false));
    }

    @Test void settingsRejectMistakesAndKeepOperatorValues(@TempDir Path directory) throws Exception {
        assertThrows(IllegalArgumentException.class, () -> FeatureSettings.read(new StringReader("terrain.preset=maybe")));
        assertThrows(IllegalArgumentException.class, () -> FeatureSettings.read(new StringReader("terrain.presett=false")));
        assertThrows(IllegalArgumentException.class, () -> FeatureSettings.read(new StringReader("terrain.preset=true\nterrain.preset=false")));
        var defaults = FeatureSettings.load(directory);
        assertTrue(defaults.enabled(Feature.TERRAIN));
        for (Feature feature : Feature.values()) assertEquals(feature != Feature.ACCESSORIES_CURIOS_BINDINGS && feature != Feature.DELIVERY_STRUCTURE_SEARCH, defaults.enabled(feature), feature.key);
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

    @Test void curiosBindingsAreExplicitlyEnabledForTheExactOptionalSet() {
        var supported = Map.of("curios", "9.6.0+1.21.1", "accessories", "1.1.0-beta.54+1.21.1", "accessories_compat_layer", "0.1.13");
        var feature = Feature.ACCESSORIES_CURIOS_BINDINGS;
        assertFalse(new FeatureSettings(Map.of()).enabled(feature));
        for (boolean client : new boolean[]{false, true}) {
            assertEquals(java.util.Set.of(feature), FeatureSelection.select(only(feature), supported, client).active());
            assertTrue(FeatureSelection.select(only(), supported, client).mixins(client).isEmpty());
            assertFalse(FeatureSelection.select(only(feature), Map.of("curios", "9.6.0+1.21.1"), client).enabled(feature));
        }
        var changed = new java.util.HashMap<>(supported);
        changed.put("accessories_compat_layer", "0.1.14");
        assertThrows(IllegalStateException.class, () -> FeatureSelection.select(only(feature), changed, false));
    }

    @Test void curiosBreakRepairIsIndependentAndOptionalOnBothSides() {
        var feature = Feature.ACCESSORIES_CURIO_BREAK;
        var supported = Map.of("curios", "9.6.0+1.21.1", "accessories", "1.1.0-beta.54+1.21.1", "accessories_compat_layer", "0.1.13");
        assertTrue(new FeatureSettings(Map.of()).enabled(feature));
        for (boolean client : new boolean[]{false, true}) {
            assertEquals(java.util.Set.of(feature), FeatureSelection.select(only(feature), supported, client).active());
            for (String missing : supported.keySet()) {
                var partial = new java.util.HashMap<>(supported); partial.remove(missing);
                assertFalse(FeatureSelection.select(only(feature), partial, client).enabled(feature));
            }
            for (String changed : supported.keySet()) {
                var future = new java.util.HashMap<>(supported); future.put(changed, "future");
                assertThrows(IllegalStateException.class, () -> FeatureSelection.select(only(feature), future, client));
                assertTrue(FeatureSelection.select(only(), future, client).active().isEmpty());
            }
            assertFalse(FeatureSelection.select(only(feature), Map.of(), client).enabled(feature));
        }
    }

}
