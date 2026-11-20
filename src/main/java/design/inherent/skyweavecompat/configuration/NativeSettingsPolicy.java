package design.inherent.skyweavecompat.configuration;

import design.inherent.reform.definition.DocumentId;
import design.inherent.reform.definition.FieldPath;
import design.inherent.reform.neoforge.configuration.RegisteredConfig;
import design.inherent.reform.owner.ApplicationEffects;
import design.inherent.reform.validation.ValidationReport;
import design.inherent.reform.value.ValueType;
import design.inherent.reform.value.ValueTypes;
import design.inherent.skyweavecompat.SkyweaveCompat;
import design.inherent.skyweavecompat.placement.NeighborRule;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import net.neoforged.fml.config.ModConfig;

/** Exact foreign projections and domain rules remain with the Compat consumer. */
public final class NativeSettingsPolicy {
    public static final RegisteredConfig.Limits LIMITS = new RegisteredConfig.Limits(ConfigFile.LIMIT, 1024, 64, 16);
    private static final Set<String> WATER_FIELDS = Set.of(
            "Generation.normalRiverMode", "Generation.normalRiverDensity", "Generation.normalRiverCanyonHeight",
            "Generation.normalRiverTunnels", "Generation.normalTunnelHeight", "Generation.normalTunnelLedge",
            "Generation.normalTunnelPillars", "Generation.normalTunnelFormations", "Generation.deltaInlandReach",
            "Generation.riverSplits", "Generation.riverFallenLogs", "Generation.enableWatersheds", "Generation.mountainRiverDensity",
            "Generation.mountainRiverTunnels", "Generation.mountainTunnelFrequency", "Generation.mountainTunnelLength",
            "Generation.mountainTunnelDepth", "Generation.mountainTunnelHeight", "Generation.mountainTunnelLedge",
            "Generation.mountainRiverWaterfalls", "Generation.mountainRiverIce", "Generation.riverIceFloes",
            "Generation.mountainRiverBiome", "Generation.enableMountainLakes", "Generation.biomeBlacklist",
            "Generation.riverBoulders", "Generation.riverbankPalettes", "Generation.waterPlants",
            "River Flow Speeds.playerSpeed", "River Flow Speeds.animalSpeed", "River Flow Speeds.waterMobSpeed",
            "River Flow Speeds.itemSpeed", "River Flow Speeds.defaultSpeed", "River Flow Speeds.sableShipSpeed",
            "River Flow Speeds.rainFlowBoost", "River Flow Speeds.thunderstormFlowBoost", "River Flow Speeds.vanillaRiverFlow",
            "River Flow Speeds.customEntitySpeeds", "Visuals.enableRiverParticles", "Visuals.riverParticleDensity",
            "Visuals.enableRiverVisualFlow", "Visuals.customMainMenuPanorama", "Visuals.showMainMenuButton",
            "Sounds.enableRiverSound", "General.showWelcomeMessage", "General.disableTectonicUndergroundRivers");

    private NativeSettingsPolicy() {}

    public static RegisteredConfig.Delegation delegation(SettingsCatalog.FileEntry entry, ModConfig config, List<NativeFields.Field> fields) {
        String mod;
        String version;
        Set<String> expected;
        switch (entry.help()) {
            case "watersheds" -> { mod = "dynamicwaters"; version = "13.1.0"; expected = WATER_FIELDS; }
            case "link_gui_client" -> { mod = "createredstonelinkgui"; version = "1.21.1-1.9.2"; expected = Set.of("clickMode"); }
            case "link_gui_common" -> { mod = "createredstonelinkgui"; version = "1.21.1-1.9.2"; expected = Set.of("moveRange"); }
            default -> throw new IllegalArgumentException("Uncatalogued foreign native owner " + entry.id());
        }
        var actual = fields.stream().map(NativeFields.Field::key).collect(Collectors.toSet());
        if (!actual.containsAll(expected)) throw new IllegalArgumentException("The pinned native field set is incomplete for " + entry.id());
        var selected = fields.stream().filter(field -> expected.contains(field.key())).map(NativeSettingsPolicy::path).collect(Collectors.toSet());
        return new RegisteredConfig.Delegation(mod, version, config.getType(), entry.filename(), selected);
    }

    public static DocumentId nativeId(SettingsCatalog.FileEntry entry, ModConfig config) {
        return config.getModId().equals(SkyweaveCompat.ID) ? DocumentId.parse(entry.id())
                : new DocumentId(config.getModId(), config.getType().name().toLowerCase(java.util.Locale.ROOT));
    }

    public static FieldPath path(NativeFields.Field field) {
        var path = FieldPath.ROOT;
        for (String segment : field.binding().getPath()) path = path.field(segment);
        return path;
    }

    public static Map<FieldPath, ValueType<?>> explicitTypes(List<NativeFields.Field> fields) {
        var types = new LinkedHashMap<FieldPath, ValueType<?>>();
        for (var field : fields) if (field.defaultValue() instanceof List<?> list) {
            if (!list.stream().allMatch(String.class::isInstance)) throw new IllegalArgumentException("Unsupported native collection " + field.key());
            types.put(path(field), ValueTypes.list(ValueTypes.text(ConfigFile.LIMIT), ConfigFile.LIMIT));
        }
        return Map.copyOf(types);
    }

    public static ValidationReport validate(SettingsCatalog.FileEntry entry, Map<String, Object> values) {
        if (!entry.help().equals("villages")) return ValidationReport.VALID;
        try {
            new NeighborRule.Settings(true, ((Number) values.get("minimumGap")).intValue(),
                    ((Number) values.get("maximumGap")).intValue(), ((Number) values.get("minimumIslandRadius")).intValue());
            return ValidationReport.VALID;
        } catch (RuntimeException invalid) { return ReformDocuments.invalid(invalid); }
    }

    public static ApplicationEffects effects(SettingsCatalog.FileEntry entry, List<NativeFields.Field> fields) {
        return new ApplicationEffects(fields.stream().map(field -> new ApplicationEffects.Rule(path(field),
                Set.of(timing(field.timing(entry.filename()))), "After save, " + entry.filename() + " uses "
                        + field.timing(entry.filename()).name().toLowerCase(java.util.Locale.ROOT).replace('_', ' ') + ".")).toList());
    }

    public static ApplicationEffects.Timing timing(SettingsCatalog.Timing timing) {
        return switch (timing) {
            case IMMEDIATE, MIXED, UNVERIFIED -> ApplicationEffects.Timing.OWNER_MANAGED;
            case SERVER_START -> ApplicationEffects.Timing.WORLD_RESTART;
            case GAME_RESTART -> ApplicationEffects.Timing.GAME_RESTART;
            case NEW_WORLD -> ApplicationEffects.Timing.NEW_WORLD;
            case RELOAD, CONFIG_LOAD -> ApplicationEffects.Timing.EXPLICIT_RELOAD;
            case SCREEN_OPEN, NEW_VIEW -> ApplicationEffects.Timing.REOPEN_VIEW;
            case PLAYER_JOIN -> ApplicationEffects.Timing.NEXT_JOIN;
        };
    }
}
