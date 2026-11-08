package design.inherent.skyweavecompat.terrain.structure;

import java.util.EnumSet;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.structures.JigsawStructure;
import org.sathrek.sky_archipelago.worldgen.structure.StructurePlacementCategory;
import org.sathrek.sky_archipelago.worldgen.structure.StructurePlacementPolicy;

/** Explicit family tags extend the native roster, without class-name or namespace heuristics. */
public final class StructureMembership {
    private StructureMembership() {}

    public static StructureFamily family(Holder<Structure> holder) {
        var id = holder.unwrapKey().map(key -> key.location().toString()).orElse("");
        var nativeFamily = StructureFamily.forId(id);
        var tagged = EnumSet.noneOf(StructureFamily.class);
        for (var family : StructureFamily.values())
            if (holder.is(TagKey.create(Registries.STRUCTURE,
                    ResourceLocation.fromNamespaceAndPath("skyweave_compat", "managed/" + family.key)))) tagged.add(family);
        return select(nativeFamily, tagged, holder.value() instanceof JigsawStructure || holder.value() instanceof IslandJigsaw
                || holder.value() instanceof IslandUnderground);
    }

    static StructureFamily select(StructureFamily nativeFamily, java.util.Set<StructureFamily> tagged, boolean supported) {
        if (tagged.size() > 1 || nativeFamily != null && !tagged.isEmpty() && !tagged.contains(nativeFamily))
            throw new IllegalArgumentException("Conflicting managed structure families: " + tagged);
        return nativeFamily != null ? nativeFamily : supported && !tagged.isEmpty() ? tagged.iterator().next() : null;
    }

    static StructurePlacementCategory category(Holder<Structure> structure, StructureFamily family, StructurePlacementPolicy policy) {
        var id = structure.unwrapKey().orElseThrow().location();
        // User-authored native overrides and existing vanilla routes retain their authority.
        if (StructureFamily.forId(id.toString()) != null || policy.categoryOverrides().containsKey(id)) return policy.categoryFor(id);
        return switch (family) {
            case VILLAGES -> StructurePlacementCategory.GROUND_VILLAGE;
            case ANCIENT_CITIES, MINESHAFTS, TRIAL_CHAMBERS -> StructurePlacementCategory.UNDERGROUND;
            case DESERT_TEMPLES, JUNGLE_TEMPLES, SWAMP_HUTS, OUTPOSTS, IGLOOS, MANSIONS, TRAIL_RUINS -> StructurePlacementCategory.DEFAULT;
            default -> policy.categoryFor(id);
        };
    }
}
