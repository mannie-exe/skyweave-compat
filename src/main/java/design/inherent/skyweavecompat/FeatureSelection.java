package design.inherent.skyweavecompat;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Dependency decisions use metadata only; disabled features impose no version constraints. */
public record FeatureSelection(Set<Feature> active, Map<Feature, String> inactive, Map<Feature, Reason> reasons, Map<String, String> installed) {
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
        installed = Map.copyOf(installed);
    }

    public boolean enabled(Feature feature) { return active.contains(feature); }

    /** Metadata only; this must remain usable while reporting a foreign class-loading failure. */
    public String describe(Feature feature) {
        var contracts = new java.util.TreeMap<>(dependencies(feature));
        if (feature == Feature.WAYFINDER && installed.containsKey("sable")) contracts.put("sable", "2.0.6");
        if (feature == Feature.IPN_TEXT_INPUT && installed.containsKey("emi")) contracts.put("emi", "1.1.24+1.21.1+neoforge");
        if (feature == Feature.LINEAR_BOOTSTRAP && installed.containsKey("linearbearingsfix")) contracts.put("linearbearingsfix", "1.0.3");
        if (feature == Feature.YUNG_STRUCTURES)
            for (var target : design.inherent.skyweavecompat.configuration.YungReplacementBindings.TARGETS)
                if (installed.containsKey(target.mod())) contracts.put(target.mod(), target.version());
        return feature.key + " [" + String.join("; ", contracts.entrySet().stream()
                .map(entry -> DependencyVersions.contract(entry.getKey(), entry.getValue()).describe(entry.getKey(), installed.get(entry.getKey())))
                .toList()) + "]";
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
                    throw DependencyVersions.unsupported(feature, "linearbearingsfix", DependencyVersions.exact("1.0.3"), mods.get("linearbearingsfix"));
                reasons.put(feature, new Reason(InactiveKind.EXTERNAL, List.of("Linear Bearings Fix 1.0.3")));
                continue;
            }
            if (feature == Feature.WAYFINDER && mods.containsKey("sable") && !mods.get("sable").equals("2.0.6"))
                throw DependencyVersions.unsupported(feature, "sable", DependencyVersions.exact("2.0.6"), mods.get("sable"));
            if (feature == Feature.IPN_TEXT_INPUT && mods.containsKey("emi") && !mods.get("emi").equals("1.1.24+1.21.1+neoforge"))
                throw DependencyVersions.unsupported(feature, "emi", DependencyVersions.exact("1.1.24+1.21.1+neoforge"), mods.get("emi"));
            for (var entry : new java.util.TreeMap<>(required).entrySet()) {
                String version = mods.get(entry.getKey()), expected = entry.getValue();
                var contract = DependencyVersions.contract(entry.getKey(), expected);
                if (!contract.accepts(version)) throw DependencyVersions.unsupported(feature, entry.getKey(), contract, version);
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
                        throw DependencyVersions.unsupported(feature, target.mod(), DependencyVersions.exact(target.version()), mods.get(target.mod()));
            active.add(feature);
        }
        var inactive = new LinkedHashMap<Feature, String>();
        reasons.forEach((feature, reason) -> inactive.put(feature, reason.debugMessage()));
        return new FeatureSelection(active, inactive, reasons, mods);
    }

    public static Feature prerequisite(Feature feature) {
        return switch (feature) {
            case DELIVERY_STRUCTURE_SEARCH -> Feature.DELIVERY_CHUNKS;
            case WAYFINDER_DH -> Feature.WAYFINDER;
            case MURK_WATER -> Feature.TERRAIN;
            case GROUND_SUPPORT, LITHOSTITCHED_STRUCTURES, YUNG_STRUCTURES -> Feature.STRUCTURE_DISTRIBUTION;
            case YUNG_MINESHAFTS, YUNG_WITCH_HUTS -> Feature.YUNG_STRUCTURES;
            case ORE_PLACEMENT, CARVERS, CAVE_BIOMES, ANCIENT_CITIES, SPAWN, NETHER_ACCESS, C2ME_SEARCH, STRUCTURE_LOCATION, STRUCTURE_COMMAND, STRUCTURE_DISTRIBUTION,
                    ALTITUDE_HABITATS, WATER_SPAWNS, CLIMATE, DYNAMIC_WATERS, ISLAND_CLOUDS, ISLAND_VOID, ISLAND_SHADER_ENVIRONMENT, VILLAGE_PLACEMENT, PRESSURE, DELIVERY_DISTANCE,
                    DELIVERY_CHUNKS, DELIVERY_LANDING -> Feature.TERRAIN;
            case COLORWHEEL_MATERIALS -> Feature.MATERIAL_IDS;
            case DYNAMIC_WATERS_SABLE -> Feature.DYNAMIC_WATERS;
            case SABLE_CLIMATE, SNOW_REAL_MAGIC_CLIMATE, SNOW_UNDER_TREES_CLIMATE -> Feature.CLIMATE;
            case LINEAR_PONDER, LINEAR_JADE -> Feature.LINEAR_CARRIAGE;
            case LINKAGE_JADE -> Feature.LINKAGE_INTERACTIONS;
            case LINK_GUI_SABLE -> Feature.LINK_GUI_INTERACTIONS;
            case MACAW_LANTERN_SABLE -> Feature.MACAW_LANTERN;
            default -> null;
        };
    }

    public static boolean clientOnly(Feature feature) {
        return switch (feature) {
            case C2ME_CLIENT_LIGHT, BOW_MODEL_HANDOFF -> true;
            case WAYFINDER_DH, MAIN_MENU_IDENTITY, IPN_TEXT_INPUT, MOVES_KEY_MODIFIERS, EFFORTLESS_KEY_MODIFIERS, POWER_GRID_KEY_MODIFIERS, CREATE_COPYCAT_BACKFACES, COPYCATS_BACKFACES, MOVES_TOUCH_MOTION, HAND_EFFECTS, HELD_MATERIALS, MODEL_ROTATIONS, ITEM_DEFINITIONS, CONSTRUCT_COVERAGE -> true;
            case GRASSRISE_SHADERS, HOOFPRINT_ZOOM, ISLAND_CLOUDS, ISLAND_VOID, ISLAND_SHADER_ENVIRONMENT, CONFECTIONERY_BACKFACES, BUBBLE_GUM_BACKFACES, ORNATE_GRATE_BACKFACES, INDUSTRIAL_GRATING_BACKFACES, MACAW_GLASS, CONSTRUCT_DISTANCE, CONSTRUCT_OCCLUSION, SHADER_PATCHES, TEXT_DISPLAY_LIGHTING, NAMETAG_LIGHTING, TEXT_DISPLAY_SHADOW, NAMETAG_SHADOW, CREATE_BACKFACES, FUSION_BACKFACES, CREATE_WINDOWS, RECHISELED_GLASS, AUDIO_TIMELINE, LINEAR_PONDER, COLORWHEEL_SAMPLERS, COLORWHEEL_MATERIALS, LIGHTING_LIFECYCLE, SODIUM_LIGHTING, DH_CLIENT_COMPLETION, DH_HANDOFF, FRAMEBUFFER, MATERIAL_IDS, MODEL_DATA, SINGLE_BLOCK_PASSES,
                    FTB_CREATIVE_RETURN, QUESTLOG_INTERFACE, QUESTLOG_NOTIFICATIONS -> true;
            default -> false;
        };
    }

    public static Map<String, String> dependencies(Feature feature) {
        return switch (feature) {
            case MAIN_MENU_IDENTITY -> Map.of();
            case COOKERY_MILLSTONE -> Map.of("kaleidoscope_cookery", "1.6.0-neoforge+mc1.21.1");
            case ROPE_CONNECTOR_RANGES -> Map.of("create_aeronautics_throwable_rope_connector", "0.4.3", "simulated", "1.3.3");
            case IPN_TEXT_INPUT -> Map.of("inventoryprofilesnext", "2.2.5", "libipn", "6.6.3");
            case MOVES_KEY_MODIFIERS -> Map.of("punchy_moves", "1.0.9b");
            case EFFORTLESS_KEY_MODIFIERS -> Map.of("effortlessbuilding", "4.2");
            case POWER_GRID_KEY_MODIFIERS -> Map.of("powergrid", "0.6.2");
            case SNOW_REAL_MAGIC_CLIMATE -> Map.of("snowrealmagic", "12.2.7+neoforge");
            case SNOW_UNDER_TREES_CLIMATE -> Map.of("snowundertrees", "1.7");
            case BOW_MODEL_HANDOFF -> Map.of("punchy", "3.2b", "modefite", "1.0.1+1.21.1");
            case MODEL_ROTATIONS, ITEM_DEFINITIONS -> Map.of("modefite", "1.0.1+1.21.1");
            case PATROL_CHANCE -> Map.of();
            case VARIANT_SPAWNERS -> Map.of("variantsandventures", "1.0.30");
            case VARIANT_TRIAL_ALIASES -> Map.of("variantsandventures","1.0.30","lithostitched","1.8.1");
            case MURK_WATER -> Map.of("variantsandventures", "1.0.30", "sky_archipelago", "1.4");
            case MOVES_TOUCH_MOTION -> Map.of("punchy_moves", "1.0.9b", "touch_n_motion", "1.0.5");
            case HELD_MATERIALS -> Map.of("iris", "1.8.14-beta.1+mc1.21.1", "punchy", "3.2b");
            case HAND_EFFECTS -> Map.of("iris", "1.8.14-beta.1+mc1.21.1", "hmioutline", "2.4.0+mc1.21.1");
            case BETTER_VILLAGE_CONFIG -> Map.of("bettervillage", "3.3.1", "libraryferret", "4.0.0");
            case WAYFINDER -> Map.of();
            case WAYFINDER_DH -> Map.of("distanthorizons", "3.3.3");
            case FTB_ITEM_FILTERS -> Map.of("ftbquests", "2101.1.38");
            case C2ME_SEARCH -> Map.of("c2me_rewrites_chunkio", "0.4.0-alpha.0.124+1.21.1");
            case C2ME_CLIENT_LIGHT -> Map.of("c2me", "0.4.0-alpha.0.124+1.21.1", "c2me_notickvd", "0.4.0-alpha.0.124+1.21.1");
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
            case CONSTRUCT_COVERAGE -> Map.of("ssrd", "1.8.8", "sable", "2.0.6", "distanthorizons", "3.3.3");
            case MACAW_LANTERN -> Map.of("mcwbridges", "3.1.2");
            case MACAW_LANTERN_SABLE -> Map.of("mcwbridges", "3.1.2", "sable", "2.0.6");
            case MACAW_GLASS -> Map.of("mcwwindows", "2.4.2");
            case CONFECTIONERY_BACKFACES -> Map.of("create_confectionery", "1.1.3.");
            case BUBBLE_GUM_BACKFACES -> Map.of("linearbearing", "1.3.5");
            case ORNATE_GRATE_BACKFACES -> Map.of("dndecor", "2.2b");
            case INDUSTRIAL_GRATING_BACKFACES -> Map.of("bits_n_bobs", "2.3.5");
            case SHADER_PATCHES -> Map.of("iris", "1.8.14-beta.1+mc1.21.1");
            case CREATE_BACKFACES -> Map.of("create", "6.0.10");
            case CREATE_COPYCAT_BACKFACES -> Map.of("create", "6.0.10");
            case COPYCATS_BACKFACES -> Map.of("create", "6.0.10", "copycats", "3.0.11+mc.1.21.1-neoforge");
            case FUSION_BACKFACES -> Map.of("fusion", "1.3.16");
            case CREATE_WINDOWS -> Map.of("create", "6.0.10");
            case RECHISELED_GLASS -> Map.of("rechiseled", "1.2.6");
            case QUESTLOG_INTERFACE, QUESTLOG_NOTIFICATIONS, QUESTLOG_REWARD_VALIDATION -> Map.of("questlog", "3.5.4");
            case TERRAIN, ORE_PLACEMENT, CARVERS, CAVE_BIOMES, ANCIENT_CITIES, SPAWN,
                    STRUCTURE_LOCATION, STRUCTURE_COMMAND, STRUCTURE_DISTRIBUTION, GROUND_SUPPORT, ALTITUDE_HABITATS, WATER_SPAWNS, CLIMATE, ISLAND_CLOUDS, ISLAND_VOID -> Map.of("sky_archipelago", "1.4");
            case VILLAGE_CLEARANCE, VILLAGE_PLACEMENT -> Map.of("sky_archipelago", "1.4", "skyvillages", "1.0.6", "architectury", "13.0.12");
            case PRESSURE -> Map.of("sable", "2.0.6");
            case DELIVERY_DISTANCE, DELIVERY_CHUNKS, DELIVERY_STRUCTURE_SEARCH -> Map.of("aeronautics_delivery_quests", "1.1.1");
            case DELIVERY_LANDING -> Map.of("aeronautics_delivery_quests", "1.1.1", "skyvillages", "1.0.6", "architectury", "13.0.12");
            case SABLE_GRAVES -> Map.of("sable", "2.0.6", "yigd", "2.0.13");
            case AUDIO_TIMELINE -> Map.of("analogaudio", "0.1.5-hotfix.1", "analoglib", "0.1.0");
            case ANALOG_AUDIO -> Map.of("analogaudio", "0.1.5-hotfix.1", "create", "6.0.10");
            case LINKAGE_INTERACTIONS -> Map.of("aeronautics_utility_objects", "0.2.9", "create", "6.0.10", "sable", "2.0.6", "simulated", "1.3.3");
            case LINKAGE_JOINT_BODIES -> Map.of("aeronautics_utility_objects", "0.2.9", "sable", "2.0.6");
            case LINKAGE_JADE -> Map.of("aeronautics_utility_objects", "0.2.9", "jade", "15.10.6+neoforge");
            case LINK_GUI_INTERACTIONS, LINK_GUI_RELOCATION -> Map.of("createredstonelinkgui", "1.21.1-1.9.2", "create", "6.0.10");
            case LINK_GUI_SABLE -> Map.of("createredstonelinkgui", "1.21.1-1.9.2", "create", "6.0.10", "sable", "2.0.6");
            case ACCESSORIES_CURIO_BREAK, ACCESSORIES_CURIOS_BINDINGS -> Map.of("curios", "9.6.0+1.21.1", "accessories", "1.1.0-beta.54+1.21.1",
                    "accessories_compat_layer", "0.1.13");
            case REDSTONE_LOGIC -> Map.of("create_redstone_additions", "1.0.6", "create", "6.0.10");
            case LINEAR_BOOTSTRAP -> Map.of("linearbearing", "1.3.5");
            case LINEAR_CARRIAGE, LINEAR_TRANSMISSION -> Map.of("linearbearing", "1.3.5", "create", "6.0.10", "sable", "2.0.6", "simulated", "1.3.3");
            case LINEAR_GYRO -> Map.of("linearbearing", "1.3.5", "create", "6.0.10", "sable", "2.0.6");
            case LINEAR_PONDER -> Map.of("linearbearing", "1.3.5", "create", "6.0.10", "ponder", "1.0.87+mc1.21.1");
            case LINEAR_JADE -> Map.of("linearbearing", "1.3.5", "jade", "15.10.6+neoforge");
            case DH_CLIENT_COMPLETION, DH_UPDATE_QUEUE, DH_HANDOFF -> Map.of("distanthorizons", "3.3.3");
            case DH_SHUTDOWN, DH_CACHE -> Map.of("distanthorizons", "3.3.3", "sky_archipelago", "1.4");
            case BLUEMAP -> Map.of("bluemap", "5.7", "sky_archipelago", "1.4");
            case SURVEYOR_STRUCTURES -> Map.of("surveyor", "1.2.4_1.21", "sky_archipelago", "1.4");
            case FRAMEBUFFER -> Map.of("veil", "4.3.2", "iris", "1.8.14-beta.1+mc1.21.1");
            case TEXT_DISPLAY_LIGHTING, NAMETAG_LIGHTING, TEXT_DISPLAY_SHADOW, NAMETAG_SHADOW -> Map.of("iris", "1.8.14-beta.1+mc1.21.1");
            case MATERIAL_IDS, SINGLE_BLOCK_PASSES -> Map.of("sable", "2.0.6", "iris", "1.8.14-beta.1+mc1.21.1", "sodium", "0.8.13+mc1.21.1");
            case COLORWHEEL_SAMPLERS -> Map.of("colorwheel", "1.3.2+mc1.21.1", "iris", "1.8.14-beta.1+mc1.21.1", "flywheel", "1.0.6");
            case COLORWHEEL_MATERIALS -> Map.of("colorwheel", "1.3.2+mc1.21.1");
            case LIGHTING_LIFECYCLE -> Map.of("sabledynlights", "2.0.1", "lambdynlights", "4.8.11+1.21.1", "create", "6.0.10");
            case GRASSRISE_SHADERS -> Map.of("grassrise_better_lighting", "2.0.0", "iris", "1.8.14-beta.1+mc1.21.1", "sodium", "0.8.13+mc1.21.1");
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
                case MAIN_MENU_IDENTITY -> List.of("client.MainMenuBrandingMixin", "client.MainMenuLayoutMixin");
                case MODEL_ROTATIONS -> List.of("client.MultiAxisModelMixin");
                case ITEM_DEFINITIONS -> List.of("client.definitions.DefinitionLoadingMixin", "client.definitions.DefinitionBakeryMixin",
                        "client.definitions.DefinitionBindingMixin", "client.definitions.DefinitionRegistryMixin",
                        "client.definitions.DefinitionResolverMixin", "client.definitions.DefinitionRenderingMixin");
                case MOVES_TOUCH_MOTION -> List.of("client.MovesTouchMotionApiMixin", "client.TouchMotionCapeInvoker");
                case COOKERY_MILLSTONE -> List.of("inventory.CookeryMillstoneInputMixin");
                case ROPE_CONNECTOR_RANGES -> List.of("RopeConnectorRangeMixin");
                case IPN_TEXT_INPUT -> List.of("client.input.IpnKeybindMixin", "client.input.IpnInputDispatchMixin", "client.input.IpnInventoryInputMixin", "client.input.RecipeSearchAccess", "client.input.IpnWindowMixin", "client.input.IpnContainerAccess");
                case MOVES_KEY_MODIFIERS -> List.of("client.input.MovesBindingsMixin", "client.input.MovesKeyboardScopeMixin",
                        "client.input.MovesPerspectiveDispatchMixin", "client.input.MovesNativePerspectiveMixin", "client.input.MovesSavedModifiersMixin",
                        "client.input.MovesEmoteAccess", "client.input.MovesEmoteActivityMixin", "client.input.MovesKeyReleaseAccess");
                case POWER_GRID_KEY_MODIFIERS -> List.of("client.input.PowerGridBindingsMixin", "client.input.PowerGridRegistrationMixin",
                        "client.input.PowerGridEditorMixin", "client.input.PowerGridWireAccess", "client.input.PowerGridKeyAccess",
                        "client.input.PowerGridDispatchMixin", "client.input.PowerGridWindowMixin");
                case EFFORTLESS_KEY_MODIFIERS -> List.of("client.input.EffortlessHistoryMixin", "client.input.EffortlessKeysAccess", "client.input.EffortlessHeldMixin",
                        "client.input.EffortlessKeyboardMixin", "client.input.EffortlessMouseMixin", "client.input.EffortlessRadialMixin",
                        "client.input.EffortlessWindowMixin");
                case HELD_MATERIALS -> List.of("client.materials.MaterialHandsAccess", "client.materials.MaterialIrisMixin",
                        "client.materials.MaterialPunchyMixin", "client.materials.MaterialItemMixin");
                case HAND_EFFECTS -> List.of("client.OutlineLayersAccess", "client.OutlineGlowAccess", "client.OutlineMoldAccess",
                        "client.OutlineShaderCacheAccess", "client.OutlineVanillaHandMixin", "client.OutlineIrisLifecycleMixin",
                        "client.OutlineIrisHandMixin", "client.OutlineIrisShaderMixin");
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
                case PATROL_CHANCE -> List.of("encounters.PatrolChanceMixin");
                case VARIANT_SPAWNERS -> List.of("encounters.VariantSpawnerMixin");
                case VARIANT_TRIAL_ALIASES -> List.of("encounters.VariantsTrialAliasesMixin");
                case MURK_WATER -> List.of("encounters.MurkDepthMixin", "encounters.MurkGoalMixin");
                case NETHER_ACCESS -> List.of("PortalIgnitionMixin", "PortalCreationMixin", "NetherPortalAccessMixin", "DimensionTravelMixin", "ClosedDimensionRespawnMixin", "ClosedDimensionTeleportMixin", "ClosedDimensionLoginMixin");
                case STRUCTURE_COMMAND -> List.of("StructureCommandMixin");
                case C2ME_SEARCH -> List.of("C2meSearchWorkerMixin", "C2meSearchStorageMixin");
                case STRUCTURE_LOCATION -> List.of("JigsawSearchAccess", "TemplatePoolSearchAccess");
                case LITHOSTITCHED_STRUCTURES -> List.of("LithostitchedStructureMixin", "VillageDelegateMixin");
                case YUNG_STRUCTURES -> List.of("YungStructureMixin", "YungBuriedSupportMixin", "YungReplacementMixin", "YungPolicyStateMixin");
                case YUNG_MINESHAFTS -> List.of("YungMineshaftStructureMixin", "YungMineshaftEntranceMixin", "YungMineshaftSupportMixin", "YungMineshaftFitMixin");
                case YUNG_WITCH_HUTS -> List.of("YungHutSupportMixin");
                case GROUND_SUPPORT -> List.of("NaturalStructureCaptureMixin", "StructureBeardifierAccess", "GroundVillageTerrainMixin", "GroundVillagePieceMixin", "GroundVillageBoulderMixin", "GroundVillageLakeMixin", "GroundVillageDiskMixin", "GroundVillageSimpleBlockMixin", "GroundVillageOreMixin", "GroundVillageTreeMixin", "MangroveRootPlacementAccess", "GroundVillageOutputMixin", "GroundVillageProjectionMixin", "GroundVillageTemplateMixin", "GroundVillageCarvingMixin", "GroundVillageCarverBlockMixin");
                case STRUCTURE_DISTRIBUTION -> List.of("StructurePolicyStateMixin", "GeneratorStructurePolicyMixin", "StructurePolicyCategoryMixin", "StructurePolicyJigsawMixin", "StructurePolicySizeMixin",
                        "StructurePolicyAttemptMixin", "StructurePolicyHostsMixin", "StructurePolicySitesMixin",
                        "StructurePolicyVillageMixin", "StructurePolicyUndergroundMixin", "StructurePolicyPortalHeightMixin",
                        "VillageGroundingResultAccess", "VillageTemplateAccess", "VillagePaletteAccess", "VillageGroundingMixin", "VillageClassifierMixin", "VillageTrimmerMixin", "VillageCommitMixin", "StructurePolicyOverlapMixin", "StructurePolicyThrottleMixin",
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
                        "water.WaterBiomeMixin", "water.WaterFlowMixin", "water.WaterOceanFlowMixin", "water.WaterProjectionMixin", "water.WaterStoreMixin", "water.WaterColumnQueryMixin", "water.WaterCutOwnershipMixin", "water.WaterRepairMixin");
                case DYNAMIC_WATERS_SABLE -> List.of("water.WaterSableMixin");
                case BOW_MODEL_HANDOFF -> List.of("client.bow.BowModelContextMixin", "client.bow.BowUseDurationMixin", "client.bow.PunchyBowRenderContextMixin");
                case SABLE_CLIMATE -> List.of("SableClimateMixin");
                case SNOW_REAL_MAGIC_CLIMATE -> List.of("snow.SnowExtraClimateMixin", "snow.SnowRuntimeClimateMixin");
                case SNOW_UNDER_TREES_CLIMATE -> List.of("snow.TreeSnowClimateMixin");
                case ISLAND_SHADER_ENVIRONMENT -> List.of("client.IslandShaderEnvironmentMixin", "client.ShaderClimateMixin");
                case ISLAND_CLOUDS -> List.of();
                case ISLAND_VOID -> List.of("client.IslandVoidFogMixin", "client.IslandVoidSkyMixin");
                case VILLAGE_CLEARANCE -> List.of("StructureSupportMixin");
                case VILLAGE_PLACEMENT -> List.of("VillageHeightMixin", "VillageStructureStateMixin", "GeneratorStructureStateMixin");
                case PRESSURE -> List.of("DimensionPhysicsMixin");
                case DELIVERY_DISTANCE -> List.of("DeliveryDistanceMixin");
                case DELIVERY_CHUNKS -> List.of("DeliveryChunksMixin");
                case DELIVERY_STRUCTURE_SEARCH -> List.of("DeliverySearchMixin", "DeliverySearchCacheMixin", "DeliverySearchAccess", "DeliverySearchConfigAccess");
                case DELIVERY_LANDING -> List.of("DeliveryLandingMixin");
                case SABLE_GRAVES -> List.of("recovery.GraveRecoveryMixin", "recovery.GraveBlockRecoveryMixin",
                        "recovery.GraveCompassRecoveryMixin", "recovery.CompassTrackingMixin", "recovery.GraveRespawnRecoveryMixin");
                case AUDIO_TIMELINE -> List.of("audio.RadioTimelineMixin", "audio.AudioEngineTimelineMixin", "audio.NativeTimelineMixin");
                case ANALOG_AUDIO -> List.of("AnalogAudioCreateMixin");
                case LINKAGE_INTERACTIONS -> client
                        ? List.of("linkage.LinkagePacketMixin", "linkage.LinkageBindingMixin", "linkage.LinkageJointBindingMixin", "linkage.LinkageBlockMixin", "linkage.LinkageUseMixin", "linkage.LinkageWrenchMixin", "linkage.LinkageMenuButtonMixin", "client.LinkageRodInputMixin")
                        : List.of("linkage.LinkagePacketMixin", "linkage.LinkageBindingMixin", "linkage.LinkageJointBindingMixin", "linkage.LinkageBlockMixin", "linkage.LinkageUseMixin", "linkage.LinkageWrenchMixin", "linkage.LinkageMenuButtonMixin");
                case LINKAGE_JOINT_BODIES -> List.of("linkage.HydraulicJointBodiesMixin");
                case LINKAGE_JADE -> List.of("linkage.ConversionStateAccessor");
                case LINK_GUI_INTERACTIONS -> List.of("redstone.GuiPacketMixin", "redstone.GuiOpenPacketMixin", "redstone.GuiSwapPacketMixin", "redstone.GuiMenuMixin");
                case LINK_GUI_RELOCATION -> client ? List.of("redstone.GuiRelocationMixin", "client.GuiRelocationScreenMixin") : List.of("redstone.GuiRelocationMixin");
                case LINK_GUI_SABLE -> List.of();
                case ACCESSORIES_CURIO_BREAK -> List.of();
                case ACCESSORIES_CURIOS_BINDINGS -> List.of("accessories.CuriosEarlyBindingsMixin", "accessories.AccessoriesEntityBindingsMixin");
                case REDSTONE_LOGIC -> List.of("redstone.CrossroadMixin");
                case LINEAR_BOOTSTRAP -> client ? List.of() : List.of("linear.LinearBootstrapMixin");
                case LINEAR_CARRIAGE -> List.of("linear.DockingBrakeAccessor", "linear.LinearMovingMixin", "linear.LinearCasingMixin", "linear.LinearAssemblyMixin", "linear.LinearCasingBlockMixin");
                case LINEAR_TRANSMISSION -> List.of("linear.TransmissionMixin", "linear.MagneticPortMixin");
                case LINEAR_GYRO -> List.of("linear.GyroSensorMixin");
                case DH_CLIENT_COMPLETION -> List.of("dh.ClientCompletionWaitMixin");
                case DH_UPDATE_QUEUE -> List.of("dh.UpdateQueueProgressMixin");
                case DH_HANDOFF -> List.of("dh.ClientWrapperHandoffMixin");
                case DH_SHUTDOWN -> List.of("dh.ChunkUnloadBudgetMixin");
                case DH_CACHE -> client ? List.of("dh.WorldLevelKeyMixin", "dh.LegacyCacheMixin") : List.of("dh.WorldLevelKeyMixin");
                case BLUEMAP, LINEAR_PONDER, LINEAR_JADE -> List.of();
                case FRAMEBUFFER -> List.of("client.ShaderWrapperMixin");
                case TEXT_DISPLAY_SHADOW -> List.of("client.TextDisplayShadowMixin");
                case NAMETAG_SHADOW -> List.of("client.NametagShadowMixin");
                case TEXT_DISPLAY_LIGHTING -> List.of("client.TextDisplayLightingMixin");
                case NAMETAG_LIGHTING -> List.of("client.NametagLightingMixin");
                case CONSTRUCT_DISTANCE, MACAW_GLASS, CONFECTIONERY_BACKFACES, BUBBLE_GUM_BACKFACES,
                        ORNATE_GRATE_BACKFACES, INDUSTRIAL_GRATING_BACKFACES -> List.of();
                case CONSTRUCT_OCCLUSION -> List.of("client.ConstructShaderMixin", "client.ConstructDepthLifecycleMixin", "client.ConstructSamplerBudgetAccessor");
                case CONSTRUCT_COVERAGE -> List.of("dh.CoverageSourceMixin", "dh.CoverageBuilderLifecycleMixin", "dh.CoverageBufferMixin", "dh.CoverageTransformMixin",
                        "dh.CoverageSectionMixin", "client.ConstructCoverageDrawMixin", "client.ConstructCoverageLifecycleMixin");
                case MATERIAL_IDS -> List.of("client.ConstructMaterialMixin");
                case COLORWHEEL_SAMPLERS -> List.of("client.ColorwheelSamplerMixin");
                case COLORWHEEL_MATERIALS -> List.of("client.ColorwheelMaterialMixin");
                case LIGHTING_LIFECYCLE -> List.of("client.CreateLightSourcesAccessor", "client.SableLightStateAccessor", "client.LightingLifecycleMixin",
                        "client.LightEngineOwnerAccessor", "client.VirtualEmissionSideMixin",
                        "client.DynamicLightSideMixin", "client.ScopedLightQueryMixin", "client.ServerLightViewMixin");
                case GRASSRISE_SHADERS -> List.of("client.GrassriseAdapterMixin", "client.GrassriseGateMixin", "client.GrassriseGateAccess", "client.GrassriseIrisMixin");
                case SODIUM_LIGHTING -> List.of("client.SodiumLightingMixin");
                case C2ME_CLIENT_LIGHT -> List.of("client.InitialLightPacketMixin", "client.InitialLightChunkMixin", "client.InitialLightEngineMixin", "client.InitialLightLevelMixin", "client.InitialLightWorldMixin");
                case MODEL_DATA -> List.of("client.SingleBlockModelDataMixin");
                case SINGLE_BLOCK_PASSES -> List.of("client.SingleBlockPassMixin");
                case MACAW_LANTERN -> enabled(Feature.MACAW_LANTERN_SABLE)
                        ? List.of("macaw.BridgeLanternShapeMixin", "macaw.ClipModeAccessor")
                        : List.of("macaw.BridgeLanternShapeMixin", "macaw.ClipModeAccessor", "macaw.LanternRayMixin");
                case MACAW_LANTERN_SABLE -> List.of("macaw.SableLanternRayMixin");
                case SHADER_PATCHES -> List.of("client.ShaderAssemblyMixin");
                case CREATE_BACKFACES -> List.of("client.CreateGlassBackfaceMixin");
                case CREATE_COPYCAT_BACKFACES -> List.of("client.CreateCopycatBackfaceMixin");
                case COPYCATS_BACKFACES -> List.of("client.CopycatsBackfaceMixin", "client.CopycatsRenderSessionMixin");
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
        if (client && (active.stream().anyMatch(feature -> switch (feature) {
            case CREATE_BACKFACES, FUSION_BACKFACES, MACAW_GLASS, CONFECTIONERY_BACKFACES,
                    BUBBLE_GUM_BACKFACES, ORNATE_GRATE_BACKFACES, INDUSTRIAL_GRATING_BACKFACES,
                    CREATE_COPYCAT_BACKFACES, COPYCATS_BACKFACES -> true;
            default -> false;
        }))) result.add("client.TransparencyResourcesMixin");
        return List.copyOf(result);
    }
}
