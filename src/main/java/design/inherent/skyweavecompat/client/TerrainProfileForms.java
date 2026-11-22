package design.inherent.skyweavecompat.client;

import design.inherent.reform.definition.DocumentDefinition;
import design.inherent.reform.definition.DocumentId;
import design.inherent.reform.minecraft.client.ClientFileSettings;
import design.inherent.reform.minecraft.client.ClientForm;
import design.inherent.reform.minecraft.client.ClientWorkflowLayout;
import design.inherent.reform.owner.FileOwner;
import design.inherent.skyweavecompat.configuration.JsonSettings;
import design.inherent.skyweavecompat.configuration.ProfileEditPlan;
import design.inherent.skyweavecompat.configuration.ReformDocuments;
import design.inherent.skyweavecompat.configuration.SettingsCatalog;
import design.inherent.skyweavecompat.terrain.GenerationConfiguration;
import design.inherent.skyweavecompat.terrain.ShapeConfiguration;
import java.util.List;
import java.util.Set;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.loading.FMLPaths;

/** Local next-world forms share one required review of the complete generation and shape pair. */
final class TerrainProfileForms {
    private static final String KEY = "skyweave_compat.settings.";
    private final List<ClientForm> forms;
    private final ClientWorkflowLayout.ReviewScope review;

    TerrainProfileForms(IEventBus bus, ModContainer container) {
        forms = List.of(profile(bus, container, GenerationConfiguration.FILE, ProfileEditPlan.GENERATION),
                profile(bus, container, ShapeConfiguration.FILE, ProfileEditPlan.SHAPES));
        review = new ClientWorkflowLayout.ReviewScope(new DocumentId("skyweave_compat", "terrain_profiles"),
                Component.translatable(KEY + "profile_review"),
                Set.of(ProfileEditPlan.GENERATION.id(), ProfileEditPlan.SHAPES.id()), Set.of(), true, ProfileEditPlan::review);
    }

    static boolean owns(String filename) {
        return filename.equals(GenerationConfiguration.FILE) || filename.equals(ShapeConfiguration.FILE);
    }

    List<ClientForm> forms() { return forms; }

    ClientWorkflowLayout.ReviewScope review() { return review; }

    private static <T> ClientForm profile(IEventBus bus, ModContainer container, String name, DocumentDefinition<T> definition) {
        var store = ReformDocuments.json(FMLPaths.CONFIGDIR.get(), name, definition);
        var binding = ClientFileSettings.newWorldDefaultsFile(design.inherent.reform.neoforge.client.NeoForgeClientRegistration.of(bus, container), store, FileOwner.Initialization.DEFER_MISSING);
        var entry = SettingsCatalog.file(name);
        var source = JsonSettings.find(name);
        return new SettingsReferenceSources<>(binding, source).form(Component.translatable(entry.labelKey()), SettingsPresentation.json(source));
    }
}
