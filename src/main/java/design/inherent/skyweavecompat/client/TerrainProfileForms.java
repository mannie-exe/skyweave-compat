package design.inherent.skyweavecompat.client;

import design.inherent.reform.definition.DocumentDefinition;
import design.inherent.reform.definition.DocumentId;
import design.inherent.reform.definition.FieldPath;
import design.inherent.reform.neoforge.client.ClientEditingWorkflow;
import design.inherent.reform.neoforge.client.ClientFileSettings;
import design.inherent.reform.neoforge.client.ClientForm;
import design.inherent.reform.neoforge.client.ClientWorkflowLayout;
import design.inherent.reform.owner.FileOwner;
import design.inherent.reform.presentation.FormCatalog;
import design.inherent.reform.presentation.FormPresentation;
import design.inherent.skyweavecompat.configuration.JsonSettings;
import design.inherent.skyweavecompat.configuration.ProfileEditPlan;
import design.inherent.skyweavecompat.configuration.ReformDocuments;
import design.inherent.skyweavecompat.configuration.SettingsCatalog;
import design.inherent.skyweavecompat.terrain.GenerationConfiguration;
import design.inherent.skyweavecompat.terrain.ShapeConfiguration;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.loading.FMLPaths;

/** Local next-world forms share one required review of the complete generation and shape pair. */
final class TerrainProfileForms {
    private static final String KEY = "skyweave_compat.settings.";
    private final List<ClientForm> forms;
    private final ClientWorkflowLayout layout;

    TerrainProfileForms(IEventBus bus, ModContainer container) {
        forms = List.of(profile(bus, container, GenerationConfiguration.FILE, ProfileEditPlan.GENERATION),
                profile(bus, container, ShapeConfiguration.FILE, ProfileEditPlan.SHAPES));
        var placements = forms.stream().map(form -> new ClientWorkflowLayout.Placement(form.document(), form.route(),
                form.title(), Optional.empty())).toList();
        var review = new ClientWorkflowLayout.ReviewScope(new DocumentId("skyweave_compat", "terrain_profiles"),
                Component.translatable(KEY + "profile_review"),
                Set.of(ProfileEditPlan.GENERATION.id(), ProfileEditPlan.SHAPES.id()), Set.of(), true, ProfileEditPlan::review);
        layout = new ClientWorkflowLayout(List.of(new ClientWorkflowLayout.Group(
                Component.translatable(KEY + "tuning"), placements, List.of())), List.of(review));
    }

    static boolean owns(String filename) {
        return filename.equals(GenerationConfiguration.FILE) || filename.equals(ShapeConfiguration.FILE);
    }

    Screen screen(Screen parent) {
        return ClientEditingWorkflow.open(parent, Component.translatable(KEY + "profile_review"), forms, layout).screen();
    }

    private static <T> ClientForm profile(IEventBus bus, ModContainer container, String name, DocumentDefinition<T> definition) {
        var store = ReformDocuments.json(FMLPaths.CONFIGDIR.get(), name, definition);
        var binding = ClientFileSettings.newWorldDefaultsFile(bus, container, store, FileOwner.Initialization.DEFER_MISSING);
        var entry = SettingsCatalog.file(name);
        var sections = JsonSettings.find(name).fields().stream().map(field -> {
            var path = FieldPath.ROOT;
            for (String segment : field.key().split("\\.")) path = path.field(segment);
            String key = KEY + "field." + entry.help() + "." + field.key();
            return new FormCatalog.Section(path, key, Component.translatableWithFallback(key, field.key()).getString(),
                    Component.translatableWithFallback(key + ".help", "").getString());
        }).toList();
        return binding.form(Component.translatable(entry.labelKey()), FormPresentation.EMPTY.withCatalog(new FormCatalog(sections)));
    }
}
