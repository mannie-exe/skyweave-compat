package design.inherent.skyweavecompat;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Dependency decisions use metadata only; disabled features impose no version constraints. */
public record FeatureSelection(Set<Feature> active, Map<Feature, String> inactive, Map<Feature, Reason> reasons) {
    public enum InactiveKind { DISABLED, WRONG_SIDE, PREREQUISITE, MISSING, EXTERNAL }
    public record Reason(InactiveKind kind, List<String> details) {
        public Reason { details = List.copyOf(details); }
        String debugMessage() {
            return switch (kind) {
                case DISABLED -> "disabled in configuration";
                case WRONG_SIDE -> "not applicable on this physical side";
                case PREREQUISITE -> "requires active " + details.getFirst();
                case MISSING -> "missing " + String.join(", ", details);
                case EXTERNAL -> "provided by " + details.getFirst();
            };
        }
    }
    public FeatureSelection {
        active = Set.copyOf(active);
        inactive = Map.copyOf(inactive);
        reasons = Map.copyOf(reasons);
    }

    public boolean enabled(Feature feature) { return active.contains(feature); }

    /** Connector can report the same pinned Fabric artifact with '+' or normalized '_'. */
    private static boolean fabricVersionSpelling(String id,String version,String expected) {
        return id.equals("surveyor") && expected.equals("1.2.4_1.21") && version.equals("1.2.4+1.21")
                || id.equals("hoofprint") && expected.equals("1.3.0_1.21") && version.equals("1.3.0+1.21");
    }

    public static FeatureSelection select(FeatureSettings settings, Map<String, String> mods, boolean client) {
        var active = EnumSet.noneOf(Feature.class);
        var reasons = new LinkedHashMap<Feature, Reason>();
        for (var feature : Feature.values()) {
            if (!settings.enabled(feature)) { reasons.put(feature, new Reason(InactiveKind.DISABLED, List.of())); continue; }
            if (clientOnly(feature) && !client || feature == Feature.BLUEMAP && client) {
                reasons.put(feature, new Reason(InactiveKind.WRONG_SIDE, List.of())); continue;
            }
            Feature prerequisite = prerequisite(feature);
            if (prerequisite != null && !active.contains(prerequisite)) {
                reasons.put(feature, new Reason(InactiveKind.PREREQUISITE, List.of(prerequisite.key))); continue;
            }
            var required = dependencies(feature);
            var missing = required.keySet().stream().filter(id -> !mods.containsKey(id)).sorted().toList();
            if (!missing.isEmpty()) { reasons.put(feature, new Reason(InactiveKind.MISSING, missing)); continue; }
            if (feature == Feature.LINEAR_BOOTSTRAP && mods.containsKey("linearbearingsfix")) {
                if (!mods.get("linearbearingsfix").equals("1.0.3"))
                    throw new IllegalStateException("Review the installed Linear Bearings Fix before enabling "
                            + feature.key + "; supported companion version is 1.0.3. Disable this adaptation in " + FeatureSettings.FILE + ".");
                reasons.put(feature, new Reason(InactiveKind.EXTERNAL, List.of("Linear Bearings Fix 1.0.3")));
                continue;
            }
            if (feature == Feature.WAYFINDER && mods.containsKey("sable") && !mods.get("sable").equals("2.0.6"))
                throw new IllegalStateException("Review Wayfinder's coordinate-frame guard for Sable " + mods.get("sable")
                        + "; supported optional version is 2.0.6. Disable " + feature.key + " in " + FeatureSettings.FILE + ".");
            for (var entry : required.entrySet()) {
                String version = mods.get(entry.getKey()), expected = entry.getValue();
                if (!(expected.endsWith(".") ? version.startsWith(expected) : version.equals(expected))
                        && !fabricVersionSpelling(entry.getKey(), version, expected)
                        && !(entry.getKey().equals("questlog") && expected.equals("3.5.0") && version.equals("3.5.3")))
                    throw new IllegalStateException("Skyweave: Compatibility Patches " + feature.key + " requires "
                            + entry.getKey() + " " + expected + "; found " + version
                            + ". Disable this adaptation in " + FeatureSettings.FILE + " or install the supported version.");
            }
            if (feature == Feature.FRAMEBUFFER && mods.containsKey("veil_iris_fix"))
                throw new IllegalStateException("Remove Veil Iris Fix: its framebuffer correction is included in Skyweave: Compatibility Patches. "
                        + "Alternatively disable " + feature.key + " in " + FeatureSettings.FILE + ".");
            if ((feature == Feature.VILLAGE_CLEARANCE || feature == Feature.VILLAGE_PLACEMENT)
                    && mods.containsKey("sky_village_neighbors"))
                throw new IllegalStateException("Remove Sky Village Neighbors or disable village adaptations in Skyweave: Compatibility Patches in " + FeatureSettings.FILE);
            if (feature == Feature.YUNG_STRUCTURES)
                for (var target : design.inherent.skyweavecompat.configuration.YungReplacementBindings.TARGETS)
                    if (mods.containsKey(target.mod()) && !mods.get(target.mod()).equals(target.version()))
                        throw new IllegalStateException("Review " + feature.key + " for " + target.mod() + " " + mods.get(target.mod())
                                + "; supported version is " + target.version() + ". Disable the adaptation or install the supported version.");
            active.add(feature);
        }
        var inactive = new LinkedHashMap<Feature, String>();
        reasons.forEach((feature, reason) -> inactive.put(feature, reason.debugMessage()));
        return new FeatureSelection(active, inactive, reasons);
    }

    public static Feature prerequisite(Feature feature) {
        return switch (feature) {
            case WAYFINDER_DH -> Feature.WAYFINDER;
            case GROUND_SUPPORT, LITHOSTITCHED_STRUCTURES, YUNG_STRUCTURES -> Feature.STRUCTURE_DISTRIBUTION;
            case YUNG_MINESHAFTS, YUNG_WITCH_HUTS -> Feature.YUNG_STRUCTURES;
            case ORE_PLACEMENT, CARVERS, CAVE_BIOMES, ANCIENT_CITIES, SPAWN, NETHER_ACCESS, C2ME_SEARCH, STRUCTURE_LOCATION, STRUCTURE_COMMAND, STRUCTURE_DISTRIBUTION,
                    ALTITUDE_HABITATS, WATER_SPAWNS, CLIMATE, DYNAMIC_WATERS, ISLAND_CLOUDS, ISLAND_VOID, ISLAND_SHADER_ENVIRONMENT, VILLAGE_PLACEMENT, PRESSURE, DELIVERY_DISTANCE,
                    DELIVERY_CHUNKS, DELIVERY_LANDING -> Feature.TERRAIN;
            case COLORWHEEL_MATERIALS -> Feature.MATERIAL_IDS;
            case DYNAMIC_WATERS_SABLE -> Feature.DYNAMIC_WATERS;
            case SABLE_CLIMATE -> Feature.CLIMATE;
            case LINEAR_PONDER, LINEAR_JADE -> Feature.LINEAR_CARRIAGE;
            case LINKAGE_JADE -> Feature.LINKAGE_INTERACTIONS;
            case LINK_GUI_SABLE -> Feature.LINK_GUI_INTERACTIONS;
            case MACAW_LANTERN_SABLE -> Feature.MACAW_LANTERN;
            default -> null;
        };
    }

    public static boolean clientOnly(Feature feature) {
        return switch (feature) {
            case WAYFINDER_DH -> true;
            case HOOFPRINT_ZOOM, ISLAND_CLOUDS, ISLAND_VOID, ISLAND_SHADER_ENVIRONMENT, CONFECTIONERY_BACKFACES, BUBBLE_GUM_BACKFACES, ORNATE_GRATE_BACKFACES, INDUSTRIAL_GRATING_BACKFACES, MACAW_GLASS, CONSTRUCT_DISTANCE, CONSTRUCT_OCCLUSION, SHADER_PATCHES, TEXT_DISPLAY_LIGHTING, NAMETAG_LIGHTING, TEXT_DISPLAY_SHADOW, NAMETAG_SHADOW, CREATE_BACKFACES, FUSION_BACKFACES, CREATE_WINDOWS, RECHISELED_GLASS, REDSTONE_RENDERING, AUDIO_TIMELINE, LINEAR_PONDER, COLORWHEEL_SAMPLERS, COLORWHEEL_MATERIALS, LIGHTING_LIFECYCLE, SODIUM_LIGHTING, DH_HANDOFF, FRAMEBUFFER, BALLOON, MATERIAL_IDS, MODEL_DATA, SINGLE_BLOCK_PASSES,
                    FTB_CREATIVE_RETURN, QUESTLOG_INTERFACE, QUESTLOG_NOTIFICATIONS -> true;
            default -> false;
        };
    }

    public static Map<String, String> dependencies(Feature feature) {
        return switch (feature) {
            case BETTER_VILLAGE_CONFIG -> Map.of("bettervillage", "3.3.1", "libraryferret", "4.0.0");
            case WAYFINDER -> Map.of();
            case WAYFINDER_DH -> Map.of("distanthorizons", "3.3.3");
            case FTB_ITEM_FILTERS -> Map.of("ftbquests", "2101.1.38");
            case C2ME_SEARCH -> Map.of("c2me_rewrites_chunkio", "0.4.0-alpha.0.124+1.21.1");
            case NETHER_ACCESS -> Map.of();
            case HOOFPRINT_ZOOM -> Map.of("hoofprint", "1.3.0_1.21", "surveyor", "1.2.4_1.21", "connector", "2.0.0-beta.17+1.21.1");
            case FTB_CREATIVE_RETURN -> Map.of("ftblibrary", "2101.1.39");
            case LITHOSTITCHED_STRUCTURES -> Map.of("sky_archipelago", "1.4", "lithostitched", "1.8.1");
            case YUNG_STRUCTURES -> Map.of("sky_archipelago", "1.4", "yungsapi", "1.21.1-NeoForge-5.1.9");
            case YUNG_MINESHAFTS -> Map.of("sky_archipelago", "1.4", "yungsapi", "1.21.1-NeoForge-5.1.9", "bettermineshafts", "1.21.1-NeoForge-5.1.1");
            case YUNG_WITCH_HUTS -> Map.of("sky_archipelago", "1.4", "yungsapi", "1.21.1-NeoForge-5.1.9", "betterwitchhuts", "1.21.1-NeoForge-4.1.1");
            case ISLAND_SHADER_ENVIRONMENT -> Map.of("sky_archipelago", "1.4", "iris", "1.8.14-beta.1+mc1.21.1");
            case DYNAMIC_WATERS -> Map.of("dynamicwaters", "13.1.0", "sky_archipelago", "1.4");
            case DYNAMIC_WATERS_SABLE -> Map.of("dynamicwaters", "13.1.0", "sky_archipelago", "1.4", "sable", "2.0.6");
            case SABLE_CLIMATE -> Map.of("sky_archipelago", "1.4", "sable", "2.0.6");
            case CONSTRUCT_DISTANCE -> Map.of("ssrd", "1.8.8", "sable", "2.0.6", "iris", "1.8.14-beta.1+mc1.21.1");
            case CONSTRUCT_OCCLUSION -> Map.of("ssrd", "1.8.8", "sable", "2.0.6", "iris", "1.8.14-beta.1+mc1.21.1", "distanthorizons", "3.3.3");
            case MACAW_LANTERN -> Map.of("mcwbridges", "3.1.2");
            case MACAW_LANTERN_SABLE -> Map.of("mcwbridges", "3.1.2", "sable", "2.0.6");
            case MACAW_GLASS -> Map.of("mcwwindows", "2.4.2");
            case CONFECTIONERY_BACKFACES -> Map.of("create_confectionery", "1.1.3.");
            case BUBBLE_GUM_BACKFACES -> Map.of("linearbearing", "1.3.5");
            case ORNATE_GRATE_BACKFACES -> Map.of("dndecor", "2.2b");
            case INDUSTRIAL_GRATING_BACKFACES -> Map.of("bits_n_bobs", "2.3.5");
            case SHADER_PATCHES -> Map.of("iris", "1.8.14-beta.1+mc1.21.1");
            case CREATE_BACKFACES -> Map.of("create", "6.0.10");
            case FUSION_BACKFACES -> Map.of("fusion", "1.3.16");
            case CREATE_WINDOWS -> Map.of("create", "6.0.10");
            case RECHISELED_GLASS -> Map.of("rechiseled", "1.2.6");
            case QUESTLOG_INTERFACE, QUESTLOG_NOTIFICATIONS, QUESTLOG_REWARD_VALIDATION -> Map.of("questlog", "3.5.0");
            case TERRAIN, ORE_PLACEMENT, CARVERS, CAVE_BIOMES, ANCIENT_CITIES, SPAWN,
                    STRUCTURE_LOCATION, STRUCTURE_COMMAND, STRUCTURE_DISTRIBUTION, GROUND_SUPPORT, ALTITUDE_HABITATS, WATER_SPAWNS, CLIMATE, ISLAND_CLOUDS, ISLAND_VOID -> Map.of("sky_archipelago", "1.4");
            case VILLAGE_CLEARANCE, VILLAGE_PLACEMENT -> Map.of("sky_archipelago", "1.4", "skyvillages", "1.0.6", "architectury", "13.");
            case PRESSURE -> Map.of("sable", "2.0.6");
            case DELIVERY_DISTANCE, DELIVERY_CHUNKS -> Map.of("aeronautics_delivery_quests", "1.1.1");
            case DELIVERY_LANDING -> Map.of("aeronautics_delivery_quests", "1.1.1", "skyvillages", "1.0.6", "architectury", "13.");
            case SABLE_GRAVES -> Map.of("sable", "2.0.6", "yigd", "2.0.13");
            case AUDIO_TIMELINE -> Map.of("analogaudio", "0.1.5-hotfix.1", "analoglib", "0.1.0");
            case ANALOG_AUDIO -> Map.of("analogaudio", "0.1.5-hotfix.1", "create", "6.0.10");
            case LINKAGE_INTERACTIONS -> Map.of("aeronautics_utility_objects", "0.2.9", "create", "6.0.10", "sable", "2.0.6", "simulated", "1.3.2");
            case LINKAGE_JADE -> Map.of("aeronautics_utility_objects", "0.2.9", "jade", "15.10.6+neoforge");
            case LINK_GUI_INTERACTIONS, LINK_GUI_RELOCATION -> Map.of("createredstonelinkgui", "1.21.1-1.9.2", "create", "6.0.10");
            case LINK_GUI_SABLE -> Map.of("createredstonelinkgui", "1.21.1-1.9.2", "create", "6.0.10", "sable", "2.0.6");
            case REDSTONE_LOGIC, REDSTONE_SCRAPING, REDSTONE_WRENCH, REDSTONE_RENDERING -> Map.of("create_redstone_additions", "1.0.5", "create", "6.0.10");
            case LINEAR_BOOTSTRAP -> Map.of("linearbearing", "1.3.5");
            case LINEAR_CARRIAGE, LINEAR_TRANSMISSION -> Map.of("linearbearing", "1.3.5", "create", "6.0.10", "sable", "2.0.6", "simulated", "1.3.2");
            case LINEAR_GYRO -> Map.of("linearbearing", "1.3.5", "create", "6.0.10", "sable", "2.0.6");
            case LINEAR_PONDER -> Map.of("linearbearing", "1.3.5", "create", "6.0.10", "ponder", "1.0.87+mc1.21.1");
            case LINEAR_JADE -> Map.of("linearbearing", "1.3.5", "jade", "15.10.6+neoforge");
            case DH_HANDOFF -> Map.of("distanthorizons", "3.3.3");
            case DH_SHUTDOWN, DH_CACHE -> Map.of("distanthorizons", "3.3.3", "sky_archipelago", "1.4");
            case BLUEMAP -> Map.of("bluemap", "5.7", "sky_archipelago", "1.4");
            case SURVEYOR_STRUCTURES -> Map.of("surveyor", "1.2.4_1.21", "sky_archipelago", "1.4");
            case FRAMEBUFFER -> Map.of("veil", "4.3.2", "iris", "1.8.14-beta.1+mc1.21.1");
            case BALLOON -> Map.of("aeronautics", "1.3.2", "iris", "1.8.14-beta.1+mc1.21.1");
            case TEXT_DISPLAY_LIGHTING, NAMETAG_LIGHTING, TEXT_DISPLAY_SHADOW, NAMETAG_SHADOW -> Map.of("iris", "1.8.14-beta.1+mc1.21.1");
            case MATERIAL_IDS, SINGLE_BLOCK_PASSES -> Map.of("sable", "2.0.6", "iris", "1.8.14-beta.1+mc1.21.1", "sodium", "0.8.13+mc1.21.1");
            case COLORWHEEL_SAMPLERS -> Map.of("colorwheel", "1.3.2+mc1.21.1", "iris", "1.8.14-beta.1+mc1.21.1", "flywheel", "1.0.6");
            case COLORWHEEL_MATERIALS -> Map.of("colorwheel", "1.3.2+mc1.21.1");
            case LIGHTING_LIFECYCLE -> Map.of("sabledynlights", "2.0.1", "lambdynlights", "4.8.11+1.21.1", "create", "6.0.10");
            case SODIUM_LIGHTING -> Map.of("sabledynlights", "2.0.1", "lambdynlights", "4.8.11+1.21.1",
                    "sodium", "0.8.13+mc1.21.1", "create", "6.0.10");
            case MODEL_DATA -> Map.of("sable", "2.0.6");
        };
    }

    public List<String> mixins(boolean client) {
        var result = new ArrayList<String>();
        for (var feature : Feature.values()) {
            if (!enabled(feature)) continue;
            result.addAll(switch (feature) {
                case BETTER_VILLAGE_CONFIG -> List.of("BetterVillageConfigMixin");
                case FTB_CREATIVE_RETURN -> List.of("client.FtbCreativeReturnMixin");
                case WAYFINDER, WAYFINDER_DH -> List.of();
                case TERRAIN -> List.of("StructureSearchChunksAccess", "StructureSearchStorageMixin", "StructureRegionStorageAccess", "GeneratorProfileMixin", "IslandFieldMixin", "ColumnResolverMixin",
                        "ColumnDataMixin", "InteriorSurfaceMixin", "SurfaceRulesMixin", "SurfacePaletteAccessor", "ClusterPlacementMixin", "ClusterSamplerMixin",
                        "DescriptorFactoryMixin", "OceanShapeMixin", "FringePlanMixin", "FringeColumnMixin", "ShapeDensityMixin", "PreparedDensityMixin", "ShapeDecorationMixin", "TerrainMaterialMixin", "InteriorInterpolationMixin",
                        "GeologicalMaterialMixin", "OreVeinifierAccessor");
                case ORE_PLACEMENT -> List.of("OreHeightMixin");
                case CARVERS -> List.of("TerrainCarverPolicyMixin", "CarvingContextMixin", "CarverHeightMixin", "CarverLavaMixin", "WaterCarverMixin");
                case CAVE_BIOMES -> List.of("DeepDarkFeatureMixin", "DeepDarkPlantMixin", "DeepDarkPieceMixin", "DeepDarkSpawnMixin", "DeepDarkSpreadMixin", "CaveBiomeMixin", "ProjectedCaveBiomeMixin", "DeepDarkContextAccessor", "DeepDarkProjectorAccess", "NativeClimateSourceMixin", "StableClimateTreeMixin", "RawHostClimateMixin", "RawHostCacheMixin");
                case ANCIENT_CITIES -> List.of("AncientCityRoutingMixin", "AncientCityPlacementMixin", "AncientCityHeightMixin", "AncientCityTerrainMixin");
                case SPAWN -> List.of("SpawnFallbackMixin");
                case NETHER_ACCESS -> List.of("PortalIgnitionMixin", "PortalCreationMixin", "NetherPortalAccessMixin", "DimensionTravelMixin", "ClosedDimensionRespawnMixin", "ClosedDimensionTeleportMixin", "ClosedDimensionLoginMixin");
                case STRUCTURE_COMMAND -> List.of("StructureCommandMixin");
                case C2ME_SEARCH -> List.of("C2meSearchWorkerMixin", "C2meSearchStorageMixin");
                case STRUCTURE_LOCATION -> List.of("JigsawSearchAccess", "TemplatePoolSearchAccess");
                case LITHOSTITCHED_STRUCTURES -> List.of("LithostitchedStructureMixin", "VillageDelegateMixin");
                case YUNG_STRUCTURES -> List.of("YungStructureMixin", "YungBuriedSupportMixin", "YungReplacementMixin", "YungPolicyStateMixin");
                case YUNG_MINESHAFTS -> List.of("YungMineshaftStructureMixin", "YungMineshaftEntranceMixin", "YungMineshaftSupportMixin", "YungMineshaftFitMixin");
                case YUNG_WITCH_HUTS -> List.of("YungHutSupportMixin");
                case GROUND_SUPPORT -> List.of("NaturalStructureCaptureMixin", "StructureBeardifierAccess", "GroundVillageTerrainMixin", "GroundVillagePieceMixin", "GroundVillageBoulderMixin", "GroundVillageLakeMixin", "GroundVillageDiskMixin", "GroundVillageSimpleBlockMixin", "GroundVillageOreMixin", "GroundVillageTreeMixin", "GroundVillageOutputMixin", "GroundVillageProjectionMixin", "GroundVillageTemplateMixin", "GroundVillageCarvingMixin", "GroundVillageCarverBlockMixin");
                case STRUCTURE_DISTRIBUTION -> List.of("StructurePolicyStateMixin", "GeneratorStructurePolicyMixin", "StructurePolicyCategoryMixin", "StructurePolicyJigsawMixin", "StructurePolicySizeMixin",
                        "StructurePolicyAttemptMixin", "StructurePolicyHostsMixin", "StructurePolicySitesMixin",
                        "StructurePolicyVillageMixin", "StructurePolicyUndergroundMixin", "StructurePolicyPortalHeightMixin",
                        "VillageGroundingResultAccess", "VillageTemplateAccess", "VillagePaletteAccess", "VillageGroundingMixin", "VillageTrimmerMixin", "VillageCommitMixin", "StructurePolicyOverlapMixin", "StructurePolicyThrottleMixin",
                        "StructurePolicyDiversityMixin", "StructurePolicySiteOverlapMixin", "StructurePolicyNoHostMixin",
                        "StructurePolicyUndergroundPlanningMixin", "StructurePolicyUndergroundClaimsMixin", "StructurePolicyAnchorCacheMixin", "StructurePolicyWaterPlanningMixin", "StructurePolicyIglooHeightMixin",
                        "StructurePolicyIglooPieceMixin", "StructurePolicyIglooSupportMixin", "StructurePolicyPhysicalClaimsMixin",
                        "StructurePolicyReservationMixin", "StructurePolicyPendingOwnerMixin", "StructurePolicyPyramidPieceMixin");
                case HOOFPRINT_ZOOM -> List.of("client.HoofprintZoomMixin");
                case SURVEYOR_STRUCTURES -> List.of("SurveyorStructureMixin", "SurveyorLayoutMixin", "SurveyorPieceAccess");
                case ALTITUDE_HABITATS -> List.of("ecology.BatHabitatMixin", "ecology.SlimeHabitatMixin");
                case WATER_SPAWNS -> List.of("WaterAnimalMixin", "ecology.TurtleHabitatMixin", "ecology.GlowSquidHabitatMixin",
                        "ecology.DrownedHabitatMixin", "ecology.TurtleTravelHabitatMixin", "ecology.DrownedBeachHabitatMixin",
                        "ecology.DrownedSwimHabitatMixin");
                case CLIMATE -> List.of("GenerationClimateMixin", "SurfaceClimateMixin", "SurfaceTemperatureMixin", "FreezeClimateMixin", "SnowFreezeClimateMixin",
                        "LevelClimateMixin", "ServerClimateMixin", "PortalClimateMixin", "ProjectionClimateContext",
                        "ShoreProjectionClimateMixin", "RiverProjectionClimateMixin", "ShoreLocatorClimateMixin");
                case DYNAMIC_WATERS -> List.of("water.WaterTerrainMixin", "water.WaterSinksMixin", "water.WaterSchedulerMixin",
                        "water.WaterGeneratorMixin", "water.WaterFieldMixin", "water.WaterEnvelopeMixin",
                        "water.WaterBiomeMixin", "water.WaterFlowMixin", "water.WaterOceanFlowMixin", "water.WaterProjectionMixin", "water.WaterStoreMixin", "water.WaterColumnQueryMixin", "water.WaterCutOwnershipMixin");
                case DYNAMIC_WATERS_SABLE -> List.of("water.WaterSableMixin");
                case SABLE_CLIMATE -> List.of("SableClimateMixin");
                case ISLAND_SHADER_ENVIRONMENT -> List.of("client.IslandShaderEnvironmentMixin", "client.ShaderClimateMixin");
                case ISLAND_CLOUDS -> List.of();
                case ISLAND_VOID -> List.of("client.IslandVoidFogMixin", "client.IslandVoidSkyMixin");
                case VILLAGE_CLEARANCE -> List.of("StructureSupportMixin");
                case VILLAGE_PLACEMENT -> List.of("VillageHeightMixin", "VillageStructureStateMixin", "GeneratorStructureStateMixin");
                case PRESSURE -> List.of("DimensionPhysicsMixin");
                case DELIVERY_DISTANCE -> List.of("DeliveryDistanceMixin");
                case DELIVERY_CHUNKS -> List.of("DeliveryChunksMixin");
                case DELIVERY_LANDING -> List.of("DeliveryLandingMixin");
                case SABLE_GRAVES -> List.of("recovery.GraveRecoveryMixin", "recovery.GraveBlockRecoveryMixin",
                        "recovery.GraveCompassRecoveryMixin", "recovery.CompassTrackingMixin", "recovery.GraveRespawnRecoveryMixin");
                case AUDIO_TIMELINE -> List.of("audio.RadioTimelineMixin", "audio.AudioEngineTimelineMixin", "audio.NativeTimelineMixin");
                case ANALOG_AUDIO -> List.of("AnalogAudioCreateMixin");
                case LINKAGE_INTERACTIONS -> client
                        ? List.of("linkage.LinkagePacketMixin", "linkage.LinkageBindingMixin", "linkage.LinkageJointBindingMixin", "linkage.LinkageBlockMixin", "linkage.LinkageUseMixin", "linkage.LinkageWrenchMixin", "linkage.LinkageMenuButtonMixin", "client.LinkageRodInputMixin")
                        : List.of("linkage.LinkagePacketMixin", "linkage.LinkageBindingMixin", "linkage.LinkageJointBindingMixin", "linkage.LinkageBlockMixin", "linkage.LinkageUseMixin", "linkage.LinkageWrenchMixin", "linkage.LinkageMenuButtonMixin");
                case LINKAGE_JADE -> List.of("linkage.ConversionStateAccessor");
                case LINK_GUI_INTERACTIONS -> List.of("redstone.GuiPacketMixin", "redstone.GuiOpenPacketMixin", "redstone.GuiSwapPacketMixin", "redstone.GuiMenuMixin");
                case LINK_GUI_RELOCATION -> client ? List.of("redstone.GuiRelocationMixin", "client.GuiRelocationScreenMixin") : List.of("redstone.GuiRelocationMixin");
                case LINK_GUI_SABLE, REDSTONE_RENDERING -> List.of();
                case REDSTONE_LOGIC -> List.of("redstone.ConjunctorMixin", "redstone.CrossroadMixin", "redstone.GateSupportMixin", "redstone.DisjunctorMixin", "redstone.GateLightMixin");
                case REDSTONE_SCRAPING -> List.of("redstone.VerdigrisMixin");
                case REDSTONE_WRENCH -> List.of("redstone.GateWrenchMixin");
                case LINEAR_BOOTSTRAP -> client ? List.of() : List.of("linear.LinearBootstrapMixin");
                case LINEAR_CARRIAGE -> List.of("linear.DockingBrakeAccessor", "linear.LinearMovingMixin", "linear.LinearCasingMixin", "linear.LinearAssemblyMixin", "linear.LinearCasingBlockMixin");
                case LINEAR_TRANSMISSION -> List.of("linear.TransmissionMixin", "linear.MagneticPortMixin");
                case LINEAR_GYRO -> List.of("linear.GyroSensorMixin");
                case DH_HANDOFF -> List.of("dh.ClientWrapperHandoffMixin");
                case DH_SHUTDOWN -> List.of("dh.ChunkUnloadBudgetMixin");
                case DH_CACHE -> client ? List.of("dh.WorldLevelKeyMixin", "dh.LegacyCacheMixin") : List.of("dh.WorldLevelKeyMixin");
                case BLUEMAP, LINEAR_PONDER, LINEAR_JADE -> List.of();
                case FRAMEBUFFER -> List.of("client.ShaderWrapperMixin");
                case BALLOON -> List.of("client.BalloonShadowMixin");
                case TEXT_DISPLAY_SHADOW -> List.of("client.TextDisplayShadowMixin");
                case NAMETAG_SHADOW -> List.of("client.NametagShadowMixin");
                case TEXT_DISPLAY_LIGHTING -> List.of("client.TextDisplayLightingMixin");
                case NAMETAG_LIGHTING -> List.of("client.NametagLightingMixin");
                case CONSTRUCT_DISTANCE, MACAW_GLASS, CONFECTIONERY_BACKFACES, BUBBLE_GUM_BACKFACES,
                        ORNATE_GRATE_BACKFACES, INDUSTRIAL_GRATING_BACKFACES -> List.of();
                case CONSTRUCT_OCCLUSION -> List.of("client.ConstructShaderMixin", "client.ConstructDepthLifecycleMixin", "client.ConstructSamplerBudgetAccessor");
                case MATERIAL_IDS -> List.of("client.ConstructMaterialMixin");
                case COLORWHEEL_SAMPLERS -> List.of("client.ColorwheelSamplerMixin");
                case COLORWHEEL_MATERIALS -> List.of("client.ColorwheelMaterialMixin");
                case LIGHTING_LIFECYCLE -> List.of("client.CreateLightSourcesAccessor", "client.SableLightStateAccessor", "client.LightingLifecycleMixin",
                        "client.LightEngineOwnerAccessor", "client.VirtualEmissionSideMixin",
                        "client.DynamicLightSideMixin", "client.ScopedLightQueryMixin", "client.ServerLightViewMixin");
                case SODIUM_LIGHTING -> List.of("client.SodiumLightingMixin");
                case MODEL_DATA -> List.of("client.SingleBlockModelDataMixin");
                case SINGLE_BLOCK_PASSES -> List.of("client.SingleBlockPassMixin");
                case MACAW_LANTERN -> enabled(Feature.MACAW_LANTERN_SABLE)
                        ? List.of("macaw.BridgeLanternShapeMixin", "macaw.ClipModeAccessor")
                        : List.of("macaw.BridgeLanternShapeMixin", "macaw.ClipModeAccessor", "macaw.LanternRayMixin");
                case MACAW_LANTERN_SABLE -> List.of("macaw.SableLanternRayMixin");
                case SHADER_PATCHES -> List.of("client.ShaderAssemblyMixin");
                case CREATE_BACKFACES -> List.of("client.CreateGlassBackfaceMixin");
                case FUSION_BACKFACES -> List.of("client.FusionGlassBackfaceMixin", "client.FusionPaneBackfaceMixin");
                case CREATE_WINDOWS -> List.of("client.CreateWindowCullingMixin");
                case RECHISELED_GLASS -> List.of("client.RechiseledGlassCullingMixin");
                case FTB_ITEM_FILTERS -> List.of();
                case QUESTLOG_INTERFACE -> List.of("client.QuestDetailsMixin", "client.QuestlogBookmarkMixin");
                case QUESTLOG_NOTIFICATIONS -> List.of("client.QuestNotificationMixin", "client.QuestSyncMixin");
                case QUESTLOG_REWARD_VALIDATION -> List.of("questlog.RewardClaimMixin");
            });
        }
        if (enabled(Feature.TERRAIN) || enabled(Feature.WAYFINDER)) result.add("StructureSearchCacheAccess");
        if (enabled(Feature.TERRAIN) || enabled(Feature.VILLAGE_PLACEMENT)) result.add("PlacementSaltAccessor");
        if (enabled(Feature.STRUCTURE_LOCATION) || enabled(Feature.YUNG_STRUCTURES)) result.add("StructureLocateCheckMixin");
        if (client && (enabled(Feature.CONSTRUCT_DISTANCE) || enabled(Feature.CONSTRUCT_OCCLUSION)))
            result.addAll(List.of("client.ConstructDrawMixin", "client.ConstructBufferMixin", "client.ConstructProgramMixin"));
        if (client && enabled(Feature.CLIMATE)) result.addAll(List.of("client.WeatherClimateMixin", "client.ChunkClimateMixin"));
        if (enabled(Feature.CLIMATE) && enabled(Feature.DYNAMIC_WATERS))
            result.addAll(List.of("water.FloeClimateMixin", "water.FloeLifetimeClimateMixin", "water.RiverLogClimateMixin",
                    "water.BoulderClimateMixin", "water.WaterPlantClimateMixin"));
        if (enabled(Feature.WATER_SPAWNS) && enabled(Feature.DYNAMIC_WATERS)) result.add("water.FloeWaterLevelMixin");
        if (client && (enabled(Feature.TEXT_DISPLAY_LIGHTING) || enabled(Feature.NAMETAG_LIGHTING)))
            result.addAll(List.of("client.WorldTextPipelineMixin", "client.WorldTextShaderMixin"));
        return List.copyOf(result);
    }
}
