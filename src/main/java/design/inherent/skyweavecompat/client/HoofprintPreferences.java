package design.inherent.skyweavecompat.client;

import design.inherent.reform.definition.FieldPath;
import design.inherent.reform.neoforge.client.ClientFileSettings;
import design.inherent.reform.owner.ApplicationEffects;
import design.inherent.reform.owner.SettingsHandle;
import design.inherent.reform.presentation.FormPresentation;
import design.inherent.skyweavecompat.configuration.ReformDocuments;
import design.inherent.skyweavecompat.maps.MapZoomSettings;
import java.util.List;
import java.util.Set;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.loading.FMLPaths;

/** Binds the admitted Hoofprint settings to their actual re:form publication. */
public final class HoofprintPreferences {
    private static final FieldPath OPENING_ZOOM = FieldPath.ROOT.field("zoomInitial");
    private static final FormPresentation PRESENTATION = new FormPresentation(List.of(new FormPresentation.Range(
            "zoom_range", "skyweave_compat.settings.hoofprint_zoom.range", "Zoom range",
            "Display pixels per block. An existing view is clamped to the saved range.",
            FieldPath.ROOT.field("zoomMinimum"), FieldPath.ROOT.field("zoomMaximum"))));
    private static final ApplicationEffects EFFECTS = new ApplicationEffects(List.of(
            new ApplicationEffects.Rule(FieldPath.ROOT, Set.of(ApplicationEffects.Timing.OWNER_PUBLICATION),
                    "The map refreshes its range and scroll multiplier from the saved client preference."),
            new ApplicationEffects.Rule(OPENING_ZOOM, Set.of(ApplicationEffects.Timing.NEXT_JOIN),
                    "Opening zoom applies to a connection without a remembered map view.")));

    private static ClientFileSettings<MapZoomSettings> owner;
    private static SettingsHandle<MapZoomSettings> handle;
    private static SettingsHandle.View<MapZoomSettings> observed;
    private static MapZoomSettings effective = MapZoomSettings.DEFAULT;

    private HoofprintPreferences() {}

    static void register(IEventBus bus, ModContainer container) {
        owner = ClientFileSettings.clientFile(bus, container,
                ReformDocuments.json(FMLPaths.CONFIGDIR.get(), MapZoomSettings.FILE, MapZoomSettings.definition()), EFFECTS);
    }

    static Screen screen(Screen parent) {
        return owner.screen(parent, Component.translatable("skyweave_compat.settings.file.hoofprint_zoom.title"),
                PRESENTATION);
    }

    /** Render-thread projection. Unchanged owner observations require neither a copy nor a disk read. */
    public static MapZoomSettings current() {
        if (handle == null && owner != null) handle = owner.runtime().orElse(null);
        var view = handle == null ? null : handle.view();
        if (observed != view) {
            observed = view;
            effective = view == null ? MapZoomSettings.DEFAULT : view.value().orElse(MapZoomSettings.DEFAULT);
        }
        return effective;
    }
}
