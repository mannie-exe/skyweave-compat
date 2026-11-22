package design.inherent.skyweavecompat.configuration;

import design.inherent.reform.definition.DocumentId;
import design.inherent.reform.value.ValueType;
import design.inherent.reform.value.ValueTypes;

/** Advisory reference identities; absence never replaces the owner's portable-value validation. */
public final class SettingsReferences {
    public enum Source {
        BLOCKS, BLOCK_SELECTORS, BIOMES, BIOME_TAGS, MAPS;
        public DocumentId id() { return new DocumentId("skyweave_compat", "references/" + name().toLowerCase(java.util.Locale.ROOT)); }
    }
    private SettingsReferences() {}

    public static Source source(String filename, String field) {
        return switch (filename) {
            case "skyweave_compat-wayfinder.json" -> field.equals("blockedBlocks") ? Source.BLOCK_SELECTORS : null;
            case "skyweave_compat-shapes.json" -> field.equals("fractures.biomes") ? Source.BIOMES : null;
            case "skyweave_compat-encounters.json" -> field.endsWith(".biomeTags") ? Source.BIOME_TAGS : null;
            case "skyweave_compat-bluemap.json" -> field.equals("maps") ? Source.MAPS : null;
            case "skyweave_compat-rivers.json" -> switch (field) {
                case "fluid_block" -> Source.BLOCKS;
                case "temperate_biome", "cold_biome", "excluded_biomes" -> Source.BIOMES;
                default -> null;
            };
            default -> null;
        };
    }

    public static ValueType<String> text(String filename, String field, int length) {
        var type = ValueTypes.text(length);
        var source = source(filename, field);
        return source == null ? type : ValueTypes.reference(type, source.id());
    }
}
