package design.inherent.skyweavecompat.maps;

import design.inherent.skyweavecompat.configuration.SettingSpec;
import design.inherent.skyweavecompat.configuration.RecordSettings;
import design.inherent.skyweavecompat.configuration.ConfigFile;
import com.google.gson.GsonBuilder;
import design.inherent.skyweavecompat.configuration.ConfigLocations;
import design.inherent.skyweavecompat.configuration.ProfileJson;
import java.nio.file.*;
import net.neoforged.fml.loading.FMLPaths;

/** Client preference math and codec; never links Hoofprint, Surveyor or rendering classes. */
public record MapZoomSettings(
        @SettingSpec(minimum="0.015625", maximum="128", step="0.125", unit="zoom", group="zoomRange") double zoomMinimum,
        @SettingSpec(minimum="0.015625", maximum="128", step="0.125", unit="zoom", group="zoomRange") double zoomMaximum,
        @SettingSpec(minimum="0", maximum="128", step="0.125", unit="zoom", group="initialView") double zoomInitial,
        @SettingSpec(minimum="1.01", maximum="2", step="0.01", unit="factor", group="interaction") double zoomStep) {
    public static final String FILE = "skyweave_compat-hoofprint.json";
    public static final MapZoomSettings DEFAULT = new MapZoomSettings(.125, 32, .125, 1.25);
    private static volatile MapZoomSettings lastValid = DEFAULT;
    public MapZoomSettings {
        RecordSettings.validate(MapZoomSettings.class, zoomMinimum, zoomMaximum, zoomInitial, zoomStep);
        if (zoomMaximum < zoomMinimum || zoomInitial != 0 && (zoomInitial < zoomMinimum || zoomInitial > zoomMaximum))
            throw new design.inherent.skyweavecompat.configuration.SettingIssue.InvalidValue(
                    new design.inherent.skyweavecompat.configuration.SettingIssue("zoom_range", "zoomInitial",
                            java.util.List.of("zoomMinimum", "zoomMaximum"), "Initial zoom must be zero or within the minimum/maximum range"));
    }
    public static MapZoomSettings parse(String text) {
        try { return ProfileJson.record(text, MapZoomSettings.class); }
        catch (RuntimeException invalid) {
            Throwable reason = invalid;
            while (reason.getCause() != null) reason = reason.getCause();
            throw new IllegalArgumentException(reason.getMessage(), invalid);
        }
    }
    public double clamp(double scale) { return Math.clamp(scale, zoomMinimum, zoomMaximum); }
    public double scroll(double scale, double delta) {
        if (!Double.isFinite(delta) || !Double.isFinite(scale) || scale <= 0) return scale;
        return clamp(scale * Math.pow(zoomStep, Math.clamp(delta, -32, 32)));
    }
    public static MapZoomSettings current() { return lastValid; }
    /** Publish a complete validated saved document; readers never poll the file during rendering. */
    public static void acceptSaved(String text) { lastValid = parse(text); }
    public static MapZoomSettings load() {
        var path = ConfigLocations.file(FMLPaths.CONFIGDIR.get(), FILE);
        try {
            Files.createDirectories(path.getParent());
            if (!Files.exists(path)) Files.writeString(path, new GsonBuilder().setPrettyPrinting().create().toJson(DEFAULT) + "\n",
                    StandardOpenOption.CREATE_NEW);
            lastValid = parse(ConfigFile.read(path));
        } catch (java.io.IOException | RuntimeException failure) {
            // Keep the invalid file for the configuration editor rather than losing it or crashing the map.
            com.mojang.logging.LogUtils.getLogger().warn("Cannot read map zoom preferences {}; retaining last valid values", path, failure);
        }
        return lastValid;
    }
}
