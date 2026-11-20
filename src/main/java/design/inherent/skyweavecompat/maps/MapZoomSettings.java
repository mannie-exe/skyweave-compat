package design.inherent.skyweavecompat.maps;

import design.inherent.skyweavecompat.configuration.SettingSpec;
import design.inherent.skyweavecompat.configuration.RecordSettings;
import design.inherent.skyweavecompat.configuration.ProfileJson;
import design.inherent.skyweavecompat.configuration.ReformDocuments;
import design.inherent.reform.definition.DocumentDefinition;

/** Client preference math and codec; never links Hoofprint, Surveyor or rendering classes. */
public record MapZoomSettings(
        @SettingSpec(minimum="0.015625", maximum="128", step="0.125", unit="zoom", group="zoomRange") double zoomMinimum,
        @SettingSpec(minimum="0.015625", maximum="128", step="0.125", unit="zoom", group="zoomRange") double zoomMaximum,
        @SettingSpec(minimum="0", maximum="128", step="0.125", unit="zoom", group="initialView") double zoomInitial,
        @SettingSpec(minimum="1.01", maximum="2", step="0.01", unit="factor", group="interaction") double zoomStep) {
    public static final String FILE = "skyweave_compat-hoofprint.json";
    public static final MapZoomSettings DEFAULT = new MapZoomSettings(.125, 32, .125, 1.25);
    public MapZoomSettings {
        RecordSettings.validate(MapZoomSettings.class, zoomMinimum, zoomMaximum, zoomInitial, zoomStep);
        if (zoomMaximum < zoomMinimum)
            throw new design.inherent.skyweavecompat.configuration.SettingIssue.InvalidValue(
                    new design.inherent.skyweavecompat.configuration.SettingIssue("zoom_range_order", "zoomMinimum",
                            java.util.List.of("zoomMaximum"), "Minimum zoom must not exceed maximum zoom"));
        if (zoomInitial != 0 && (zoomInitial < zoomMinimum || zoomInitial > zoomMaximum))
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
    public static DocumentDefinition<MapZoomSettings> definition() {
        return ReformDocuments.record(FILE, MapZoomSettings.class, DEFAULT);
    }
    public double clamp(double scale) { return Math.clamp(scale, zoomMinimum, zoomMaximum); }
    public double scroll(double scale, double delta) {
        if (!Double.isFinite(delta) || !Double.isFinite(scale) || scale <= 0) return scale;
        return clamp(scale * Math.pow(zoomStep, Math.clamp(delta, -32, 32)));
    }
}
