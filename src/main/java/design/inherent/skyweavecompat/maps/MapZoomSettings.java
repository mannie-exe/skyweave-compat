package design.inherent.skyweavecompat.maps;

import design.inherent.skyweavecompat.configuration.ConfigFile;
import com.google.gson.GsonBuilder;
import design.inherent.skyweavecompat.configuration.ConfigLocations;
import design.inherent.skyweavecompat.configuration.ProfileJson;
import java.nio.file.*;
import net.neoforged.fml.loading.FMLPaths;

/** Client preference math and codec; never links Hoofprint, Surveyor or rendering classes. */
public record MapZoomSettings(double zoomMinimum, double zoomMaximum, double zoomInitial, double zoomStep) {
    public static final String FILE = "skyweave_compat-hoofprint.json";
    public static final MapZoomSettings DEFAULT = new MapZoomSettings(.125, 32, .125, 1.25);
    private static volatile MapZoomSettings lastValid = DEFAULT;
    public MapZoomSettings {
        if (!Double.isFinite(zoomMinimum) || !Double.isFinite(zoomMaximum) || !Double.isFinite(zoomInitial)
                || !Double.isFinite(zoomStep) || zoomMinimum < 1.0 / 64 || zoomMaximum > 128
                || zoomMaximum < zoomMinimum || zoomInitial != 0 && (zoomInitial < zoomMinimum || zoomInitial > zoomMaximum)
                || zoomStep < 1.01 || zoomStep > 2)
            throw new IllegalArgumentException("Zoom requires 1/64 ≤ minimum ≤ maximum ≤ 128, initial zero or inside range, step1.01–2");
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
