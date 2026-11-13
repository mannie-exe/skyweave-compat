package design.inherent.skyweavecompat.configuration;

import design.inherent.skyweavecompat.Feature;
import design.inherent.skyweavecompat.FeatureSettings;
import java.util.Arrays;
import java.util.List;

/** Navigation metadata only: activation and dependency checks belong to FeatureSelection. */
public final class SettingsCatalog {
    public enum Category { INTERFACE, RENDERING, MAPS, SHIPS, MACHINES, INVENTORY, AUDIO, WORLD }
    public enum Group {
        NAVIGATION(Category.MAPS, "skyweave_compat"),
        FTB_QUESTS(Category.INTERFACE, "ftbquests"),
        FTB_LIBRARY(Category.INVENTORY, "ftblibrary"), JOURNAL(Category.INTERFACE, "questlog"), AUDIO_CLIENT(Category.AUDIO, "analog_audio"),
        DH_CLIENT(Category.MAPS, "distant_horizons"), SABLE_CLIENT(Category.RENDERING, "sable"),
        VEIL(Category.RENDERING, "veil"), IRIS(Category.RENDERING, "iris"), AERONAUTICS(Category.RENDERING, "aeronautics"),
        SSRD(Category.MAPS, "ssrd"), COLORWHEEL(Category.RENDERING, "colorwheel"), LIGHTS(Category.RENDERING, "lighting"),
        CREATE_CLIENT(Category.RENDERING, "create"), FUSION(Category.RENDERING, "fusion"),
        RECHISELED(Category.RENDERING, "rechiseled"), MACAW_GLASS(Category.RENDERING, "mcwwindows"),
        CONFECTIONERY(Category.RENDERING, "confectionery"), DNDECOR(Category.RENDERING, "dndecor"),
        BITS_N_BOBS(Category.RENDERING, "bits_n_bobs"), GUI_CLIENT(Category.MACHINES, "redstone_link_gui"),
        LINEAR_GLASS(Category.RENDERING, "linear_bearing"), LINEAR_CLIENT(Category.MACHINES, "linear_bearing"), REDSTONE_CLIENT(Category.MACHINES, "redstone_additions"),
        LINK_GUI(Category.MACHINES, "redstone_link_gui"), REDSTONE(Category.MACHINES, "redstone_additions"),
        LINKAGE(Category.MACHINES, "transmission_linkage"), LINEAR(Category.MACHINES, "linear_bearing"),
        MACAW(Category.SHIPS, "mcwbridges"),
        GRAVES(Category.INVENTORY, "yigd"), AUDIO(Category.AUDIO, "analog_audio"),
        DELIVERY(Category.SHIPS, "delivery"), QUESTLOG_SERVER(Category.INTERFACE, "questlog"),
        HOOFPRINT(Category.MAPS, "hoofprint"), DH(Category.MAPS, "distant_horizons"), BLUEMAP(Category.MAPS, "bluemap"), SURVEYOR(Category.MAPS, "surveyor"),
        BETTER_VILLAGE(Category.WORLD, "bettervillage"), C2ME(Category.WORLD, "c2me"), TERRAIN(Category.WORLD, "sky_archipelago"), LITHOSTITCHED(Category.WORLD, "lithostitched"), YUNG(Category.WORLD, "yungsapi"), YUNG_MINESHAFTS(Category.WORLD, "bettermineshafts"), YUNG_WITCH_HUTS(Category.WORLD, "betterwitchhuts"), WATERSHEDS(Category.WORLD, "dynamic_waters"), ISLAND_SKY(Category.RENDERING, "sky_archipelago"), VILLAGES(Category.WORLD, "sky_villages"),
        PRESSURE(Category.SHIPS, "sable"), WATER_SHIPS(Category.SHIPS, "dynamic_waters");
        public final Category category;
        public final String label;
        Group(Category category, String label) { this.category = category; this.label = label; }
    }
    public record Page(Category category, String label, List<Group> groups) {
        public Page { groups = List.copyOf(groups); }
        public String id() { return category.name().toLowerCase(java.util.Locale.ROOT) + "/" + label; }
        public List<Feature> features() { return Arrays.stream(Feature.values()).filter(feature -> groups.contains(group(feature))).toList(); }
        public List<Section> sections() {
            var sections = new java.util.ArrayList<Section>();
            var preferences = new java.util.ArrayList<Item>();
            if (groups.contains(Group.JOURNAL)) preferences.addAll(List.of(new Preference("images"), new Preference("symbols"), new Preference("notifications")));
            files().stream().filter(file -> file.section() == SectionKind.PREFERENCES && !file.help().equals("journal"))
                    .map(file -> (Item) new File(file)).forEach(preferences::add);
            if (!preferences.isEmpty()) sections.add(new Section(this, SectionKind.PREFERENCES, preferences));
            if (!features().isEmpty()) sections.add(new Section(this, SectionKind.FIXES,
                    features().stream().map(feature -> (Item) new Fix(feature)).toList()));
            var tuning = files().stream().filter(file -> file.section() == SectionKind.TUNING).map(file -> (Item) new File(file)).toList();
            if (!tuning.isEmpty()) sections.add(new Section(this, SectionKind.TUNING, tuning));
            if (groups.contains(Group.IRIS)) sections.add(new Section(this, SectionKind.SHADERS, List.of(new ShaderWorkspace())));
            return List.copyOf(sections);
        }
        public List<FileEntry> files() { return FILES.stream().filter(file -> file.group() != null && file.presentOn(groups)).toList(); }
    }
    public enum SectionKind {
        PREFERENCES("preferences"), FIXES("fixes"), TUNING("tuning"), SHADERS("shaders.title");
        public final String label;
        SectionKind(String label) { this.label = label; }
    }
    public record Section(Page page, SectionKind kind, List<Item> items) {
        public Section { items = List.copyOf(items); }
        public String id() { return "section/" + page.id() + "/" + kind.name().toLowerCase(java.util.Locale.ROOT); }
    }
    public sealed interface Item permits Fix, Preference, File, ShaderWorkspace { String id(); }
    public record Fix(Feature feature) implements Item { public String id() { return "feature/" + feature.key; } }
    public record Preference(String key) implements Item { public String id() { return "preference/" + key; } }
    public record File(FileEntry entry) implements Item { public String id() { return "file/" + entry.id(); } }
    public record ShaderWorkspace() implements Item { public String id() { return "shaders/rebuild"; } }
    public static List<Page> pages(Category category) {
        return groups(category).stream().map(group -> group.label).distinct()
                .map(label -> new Page(category, label, groups(category).stream().filter(group -> group.label.equals(label)).toList())).toList();
    }
    public static List<Page> pages() { return Arrays.stream(Category.values()).flatMap(category -> pages(category).stream()).toList(); }
    public static Page page(Group group) { return pages(group.category).stream().filter(page -> page.groups.contains(group)).findFirst().orElseThrow(); }
    public enum Timing { IMMEDIATE, SERVER_START, GAME_RESTART, NEW_WORLD, RELOAD, SCREEN_OPEN, NEW_VIEW, PLAYER_JOIN, CONFIG_LOAD, MIXED, UNVERIFIED }
    public record FeatureEntry(Feature feature, Group group, String labelKey, String summaryKey, String filename, Timing timing) {}
    public static FeatureEntry entry(Feature feature) {
        return new FeatureEntry(feature, group(feature), "skyweave_compat.configuration.feature." + feature.key,
                "skyweave_compat.settings.summary." + feature.key, FeatureSettings.FILE, Timing.GAME_RESTART);
    }
    public record FileEntry(String filename, String id, Group group, String help, List<Feature> owners, SectionKind section) {
        public FileEntry {
            owners = List.copyOf(owners);
            SettingId.validateDocument(id);
        }
        public boolean presentOn(List<Group> groups) {
            return groups.contains(group) || help.equals("rendering_client") && groups.contains(Group.IRIS);
        }
        public boolean presents(Page page, String key) {
            return !help.equals("rendering_client") || (key.startsWith("worldText.")
                    ? page.groups().contains(Group.IRIS) : page.groups().contains(Group.ISLAND_SKY));
        }
        public Feature fieldOwner(String key) {
            if (!help.equals("rendering_client")) return null;
            if (key.startsWith("clouds.")) return Feature.ISLAND_CLOUDS;
            if (key.startsWith("voidAtmosphere.")) return Feature.ISLAND_VOID;
            return switch (key) {
                case "worldText.unlitTextDisplays" -> Feature.TEXT_DISPLAY_LIGHTING;
                case "worldText.unlitNametags" -> Feature.NAMETAG_LIGHTING;
                case "worldText.excludeTextDisplayShadows" -> Feature.TEXT_DISPLAY_SHADOW;
                case "worldText.excludeNametagShadows" -> Feature.NAMETAG_SHADOW;
                default -> throw new IllegalArgumentException("Unowned rendering preference: " + key);
            };
        }
        public SettingId field(String key) { return new SettingId(id, key); }
        public String labelKey() { return "skyweave_compat.settings.file." + help + ".title"; }
        public String summaryKey() { return "skyweave_compat.settings.file." + help; }
        public Timing timing() {
            return switch (help) {
                case "rendering_client", "journal", "wayfinder_client", "link_gui_client", "link_gui_common" -> Timing.IMMEDIATE;
                case "hoofprint_zoom" -> Timing.IMMEDIATE;
                case "wayfinder", "structure_search" -> Timing.SERVER_START;
                case "villages" -> Timing.NEW_WORLD;
                case "generation", "shapes", "interiors", "climate", "rivers", "oceans", "ancient_cities", "structures" -> Timing.NEW_WORLD;
                case "watersheds" -> Timing.MIXED;
                case "bluemap" -> Timing.RELOAD;
                default -> Timing.GAME_RESTART;
            };
        }
        public Timing timing(String field) {
            if (help.equals("hoofprint_zoom") && field.equals("zoomInitial")) return Timing.NEW_VIEW;
            if (!help.equals("watersheds")) return timing();
            if (field.startsWith("Generation.")) return Timing.NEW_WORLD;
            return switch (field) {
                case "Visuals.showMainMenuButton" -> Timing.SCREEN_OPEN;
                case "General.showWelcomeMessage" -> Timing.PLAYER_JOIN;
                case "General.disableTectonicUndergroundRivers" -> Timing.CONFIG_LOAD;
                case "River Flow Speeds.playerSpeed", "River Flow Speeds.animalSpeed", "River Flow Speeds.waterMobSpeed",
                        "River Flow Speeds.itemSpeed", "River Flow Speeds.defaultSpeed", "River Flow Speeds.sableShipSpeed",
                        "River Flow Speeds.rainFlowBoost", "River Flow Speeds.thunderstormFlowBoost", "River Flow Speeds.vanillaRiverFlow",
                        "River Flow Speeds.customEntitySpeeds", "Visuals.enableRiverParticles", "Visuals.riverParticleDensity",
                        "Visuals.enableRiverVisualFlow", "Visuals.customMainMenuPanorama", "Sounds.enableRiverSound" -> Timing.IMMEDIATE;
                default -> Timing.UNVERIFIED;
            };
        }
        public List<String> notices(String field) {
            if (help.equals("villages")) return List.of("villages.saved_identity");
            return help.equals("watersheds") && field.equals("General.disableTectonicUndergroundRivers")
                    ? List.of("watersheds.tectonic_effect") : List.of();
        }
    }
    /** Authored relationship; numeric bounds and coupled validation remain with the document owner. */
    public record RangePair(String minimum, String maximum, String labelKey) {}
    public static List<RangePair> ranges(String filename) {
        return filename.equals("skyweave_compat-hoofprint.json")
                ? List.of(new RangePair("zoomMinimum", "zoomMaximum", "skyweave_compat.settings.zoom_range")) : List.of();
    }
    public static Timing timing(String filename, String field) {
        return FILES.stream().filter(entry -> entry.filename().equals(filename))
                .findFirst().map(entry -> entry.timing(field)).orElse(Timing.GAME_RESTART);
    }
    public static boolean changesGeneration(String filename, java.util.Collection<String> fields) {
        return fields.stream().anyMatch(field -> timing(filename, field) == Timing.NEW_WORLD);
    }
    public static Group group(Feature feature) {
        return switch (feature) {
            case BETTER_VILLAGE_CONFIG -> Group.BETTER_VILLAGE;
            case WAYFINDER, WAYFINDER_DH -> Group.NAVIGATION;
            case FTB_ITEM_FILTERS -> Group.FTB_QUESTS;
            case ISLAND_CLOUDS, ISLAND_VOID -> Group.ISLAND_SKY;
            case ISLAND_SHADER_ENVIRONMENT -> Group.IRIS;
            case DYNAMIC_WATERS -> Group.WATERSHEDS;
            case DYNAMIC_WATERS_SABLE -> Group.WATER_SHIPS;
            case MACAW_LANTERN, MACAW_LANTERN_SABLE -> Group.MACAW;
            case MACAW_GLASS -> Group.MACAW_GLASS;
            case CONFECTIONERY_BACKFACES -> Group.CONFECTIONERY;
            case BUBBLE_GUM_BACKFACES -> Group.LINEAR_GLASS;
            case ORNATE_GRATE_BACKFACES -> Group.DNDECOR;
            case INDUSTRIAL_GRATING_BACKFACES -> Group.BITS_N_BOBS;
            case CREATE_WINDOWS, CREATE_BACKFACES -> Group.CREATE_CLIENT;
            case FUSION_BACKFACES -> Group.FUSION;
            case RECHISELED_GLASS -> Group.RECHISELED;
            case QUESTLOG_INTERFACE, QUESTLOG_NOTIFICATIONS -> Group.JOURNAL;
            case AUDIO_TIMELINE -> Group.AUDIO_CLIENT;
            case DH_HANDOFF -> Group.DH_CLIENT;
            case CONSTRUCT_DISTANCE, CONSTRUCT_OCCLUSION -> Group.SSRD;
            case MODEL_DATA, MATERIAL_IDS, SINGLE_BLOCK_PASSES -> Group.SABLE_CLIENT;
            case FRAMEBUFFER -> Group.VEIL;
            case SHADER_PATCHES, TEXT_DISPLAY_LIGHTING, NAMETAG_LIGHTING, TEXT_DISPLAY_SHADOW, NAMETAG_SHADOW -> Group.IRIS;
            case BALLOON -> Group.AERONAUTICS;
            case COLORWHEEL_SAMPLERS, COLORWHEEL_MATERIALS -> Group.COLORWHEEL;
            case LIGHTING_LIFECYCLE, SODIUM_LIGHTING -> Group.LIGHTS;
            case LINEAR_PONDER -> Group.LINEAR_CLIENT;
            case LINKAGE_INTERACTIONS, LINKAGE_JADE -> Group.LINKAGE;
            case LINK_GUI_INTERACTIONS, LINK_GUI_RELOCATION, LINK_GUI_SABLE -> Group.LINK_GUI;
            case REDSTONE_LOGIC, REDSTONE_SCRAPING, REDSTONE_WRENCH -> Group.REDSTONE;
            case REDSTONE_RENDERING -> Group.REDSTONE_CLIENT;
            case LINEAR_BOOTSTRAP, LINEAR_CARRIAGE, LINEAR_TRANSMISSION, LINEAR_GYRO, LINEAR_JADE -> Group.LINEAR;
            case FTB_CREATIVE_RETURN -> Group.FTB_LIBRARY;
            case SABLE_GRAVES -> Group.GRAVES;
            case ANALOG_AUDIO -> Group.AUDIO;
            case DELIVERY_DISTANCE, DELIVERY_CHUNKS, DELIVERY_LANDING -> Group.DELIVERY;
            case QUESTLOG_REWARD_VALIDATION -> Group.QUESTLOG_SERVER;
            case DH_SHUTDOWN, DH_CACHE -> Group.DH;
            case BLUEMAP -> Group.BLUEMAP;
            case SURVEYOR_STRUCTURES -> Group.SURVEYOR;
            case NETHER_ACCESS -> Group.TERRAIN;
            case C2ME_SEARCH -> Group.C2ME;
            case HOOFPRINT_ZOOM -> Group.HOOFPRINT;
            case TERRAIN, ORE_PLACEMENT, CARVERS, CAVE_BIOMES, ANCIENT_CITIES, SPAWN,
                    STRUCTURE_LOCATION, STRUCTURE_COMMAND, STRUCTURE_DISTRIBUTION, GROUND_SUPPORT, ALTITUDE_HABITATS, WATER_SPAWNS, CLIMATE, SABLE_CLIMATE -> Group.TERRAIN;
            case VILLAGE_CLEARANCE, VILLAGE_PLACEMENT -> Group.VILLAGES;
            case LITHOSTITCHED_STRUCTURES -> Group.LITHOSTITCHED;
            case YUNG_STRUCTURES -> Group.YUNG;
            case YUNG_MINESHAFTS -> Group.YUNG_MINESHAFTS;
            case YUNG_WITCH_HUTS -> Group.YUNG_WITCH_HUTS;
            case PRESSURE -> Group.PRESSURE;
        };
    }
    public static List<Feature> features(Group group) {
        return Arrays.stream(Feature.values()).filter(feature -> group(feature) == group).toList();
    }
    public static List<Group> groups(Category category) {
        return Arrays.stream(Group.values()).filter(group -> group.category == category).toList();
    }
    public static final List<FileEntry> FILES = List.of(
            new FileEntry("skyweave_compat-rendering-client.toml", "skyweave_compat:rendering_client", Group.ISLAND_SKY, "rendering_client",
                    List.of(Feature.ISLAND_CLOUDS, Feature.ISLAND_VOID, Feature.ISLAND_SHADER_ENVIRONMENT,
                            Feature.TEXT_DISPLAY_LIGHTING, Feature.NAMETAG_LIGHTING, Feature.TEXT_DISPLAY_SHADOW, Feature.NAMETAG_SHADOW), SectionKind.PREFERENCES),
            new FileEntry("skyweave_compat-wayfinder.json", "skyweave_compat:wayfinder", Group.NAVIGATION, "wayfinder", List.of(Feature.WAYFINDER), SectionKind.TUNING),
            new FileEntry("skyweave_compat-wayfinder-client.toml", "skyweave_compat:wayfinder_client", Group.NAVIGATION, "wayfinder_client", List.of(Feature.WAYFINDER), SectionKind.PREFERENCES),
            new FileEntry("skyweave_compat-hoofprint.json", "skyweave_compat:hoofprint", Group.HOOFPRINT, "hoofprint_zoom", List.of(Feature.HOOFPRINT_ZOOM), SectionKind.PREFERENCES),
            new FileEntry(FeatureSettings.FILE, "skyweave_compat:features", null, "startup", List.of(), SectionKind.FIXES),
            new FileEntry("createredstonelinkgui-client.toml", "skyweave_compat:link_gui_client", Group.GUI_CLIENT, "link_gui_client", List.of(Feature.LINK_GUI_INTERACTIONS), SectionKind.PREFERENCES),
            new FileEntry("createredstonelinkgui-common.toml", "skyweave_compat:link_gui_common", Group.LINK_GUI, "link_gui_common", List.of(Feature.LINK_GUI_RELOCATION), SectionKind.TUNING),
            new FileEntry("skyweave_compat-journal-client.toml", "skyweave_compat:journal", Group.JOURNAL, "journal", List.of(Feature.QUESTLOG_INTERFACE, Feature.QUESTLOG_NOTIFICATIONS), SectionKind.PREFERENCES),
            new FileEntry("skyweave_compat-common.toml", "skyweave_compat:sky_villages", Group.VILLAGES, "villages", List.of(Feature.VILLAGE_CLEARANCE, Feature.VILLAGE_PLACEMENT), SectionKind.TUNING),
            new FileEntry("skyweave_compat-generation.json", "skyweave_compat:generation", Group.TERRAIN, "generation", List.of(Feature.TERRAIN), SectionKind.TUNING),
            new FileEntry("skyweave_compat-shapes.json", "skyweave_compat:shapes", Group.TERRAIN, "shapes", List.of(Feature.TERRAIN), SectionKind.TUNING),
            new FileEntry("skyweave_compat-search.json", "skyweave_compat:search", Group.TERRAIN, "structure_search", List.of(Feature.TERRAIN), SectionKind.TUNING),
            new FileEntry("skyweave_compat-interiors.json", "skyweave_compat:interiors", Group.TERRAIN, "interiors", List.of(Feature.TERRAIN), SectionKind.TUNING),
            new FileEntry("skyweave_compat-climate.json", "skyweave_compat:climate", Group.TERRAIN, "climate", List.of(Feature.CLIMATE, Feature.CAVE_BIOMES), SectionKind.TUNING),
            new FileEntry("skyweave_compat-rivers.json", "skyweave_compat:rivers", Group.TERRAIN, "rivers", List.of(Feature.TERRAIN), SectionKind.TUNING),
            new FileEntry("dynamicwaters-common.toml", "skyweave_compat:dynamic_waters", Group.WATERSHEDS, "watersheds", List.of(Feature.DYNAMIC_WATERS), SectionKind.TUNING),
            new FileEntry("skyweave_compat-oceans.json", "skyweave_compat:oceans", Group.TERRAIN, "oceans", List.of(Feature.TERRAIN), SectionKind.TUNING),
            new FileEntry("skyweave_compat-structures.json", "skyweave_compat:structures", Group.TERRAIN, "structures", List.of(Feature.STRUCTURE_DISTRIBUTION), SectionKind.TUNING),
            new FileEntry("skyweave_compat-ancient-cities.json", "skyweave_compat:ancient_cities", Group.TERRAIN, "ancient_cities", List.of(Feature.ANCIENT_CITIES), SectionKind.TUNING),
            new FileEntry("skyweave_compat-bluemap.json", "skyweave_compat:bluemap", Group.BLUEMAP, "bluemap", List.of(Feature.BLUEMAP), SectionKind.TUNING));
    public static FileEntry file(String filename) {
        return FILES.stream().filter(file -> file.filename().equals(filename)).findFirst().orElse(null);
    }
    public static FileEntry document(String id) {
        return FILES.stream().filter(file -> file.id().equals(id)).findFirst().orElse(null);
    }
    public static SettingId field(String filename, String key) {
        var file = file(filename);
        if (file == null) throw new IllegalArgumentException("Unknown configuration owner");
        return file.field(key);
    }
    private SettingsCatalog() {}
}
