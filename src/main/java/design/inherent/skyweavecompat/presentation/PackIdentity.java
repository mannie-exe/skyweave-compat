package design.inherent.skyweavecompat.presentation;

import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import net.neoforged.fml.ModList;

/** Installed resource-mod metadata, separate from its own mod version or user settings. */
public record PackIdentity(String version) {
    private static final Pattern VERSION = Pattern.compile("[A-Za-z0-9][A-Za-z0-9.+_-]{0,127}");

    public PackIdentity {
        if (version == null || !VERSION.matcher(version).matches())
            throw new IllegalArgumentException("Invalid installed pack version");
    }

    public static Optional<PackIdentity> read(Map<String, ?> properties) {
        if (properties == null) return Optional.empty();
        Object schema = properties.get("skyweave:identity-schema");
        if (!(schema instanceof Integer || schema instanceof Long) || ((Number) schema).longValue() != 1
                || !"create_skyweave".equals(properties.get("skyweave:pack-id"))) return Optional.empty();
        Object version = properties.get("skyweave:pack-version");
        return version instanceof String text && VERSION.matcher(text).matches()
                ? Optional.of(new PackIdentity(text)) : Optional.empty();
    }

    public static Optional<PackIdentity> installed(ModList mods) {
        return mods.getModContainerById("skyweave_resource_patches")
                .flatMap(mod -> read(mod.getModInfo().getModProperties()));
    }
}
