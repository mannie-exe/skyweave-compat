package design.inherent.skyweavecompat.configuration;

import design.inherent.reform.definition.DocumentDefinition;
import design.inherent.reform.edit.DraftLimits;
import design.inherent.reform.serialization.JsonLimits;
import design.inherent.reform.storage.JsonStore;
import design.inherent.reform.storage.OwnedFile;
import design.inherent.reform.storage.StorageBinding;
import java.nio.file.Path;

/** Retains Compat's existing bare JSON and configuration-file bounds. */
public final class ReformDocuments {
    private static final JsonLimits JSON = new JsonLimits(ConfigFile.LIMIT, 16, ConfigFile.LIMIT, ConfigFile.LIMIT,
            128, 4096);

    private ReformDocuments() {}

    public static <T> JsonStore<T> json(Path configRoot, String filename, DocumentDefinition<T> definition) {
        var storage = StorageBinding.relative(ConfigLocations.directory(configRoot.toAbsolutePath(), filename), filename);
        return new JsonStore<>(definition, new OwnedFile(storage, ConfigFile.LIMIT), JsonStore.Format.EXISTING_BARE,
                JSON, DraftLimits.CONFIGURATION);
    }
}
