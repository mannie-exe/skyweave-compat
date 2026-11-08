package design.inherent.skyweavecompat.terrain.structure;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import java.util.*;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;
import org.sathrek.sky_archipelago.worldgen.generator.structure.StructurePlacementPassContext;

/** Preserve qualified temple habitat/exclusion rules after replacing their source lattice. */
final class YungTempleRules {
    private static final Set<String> TYPES = Set.of("betterdeserttemples:desert_temple", "betterjungletemples:jungle_temple");
    private record Rule(Map<StructureFamily, Integer> gaps) {}
    private final Map<ResourceLocation, Rule> rules;

    private YungTempleRules(Map<ResourceLocation, Rule> rules) { this.rules = Map.copyOf(rules); }

    static boolean supported(StructurePlacement placement) {
        var key = BuiltInRegistries.STRUCTURE_PLACEMENT.getKey(placement.type());
        return key != null && TYPES.contains(key.toString());
    }

    static YungTempleRules capture(Map<ResourceLocation, StructurePlacement> placements, RegistryAccess registries) {
        var result = new HashMap<ResourceLocation, Rule>();
        var ops = RegistryOps.create(JsonOps.INSTANCE, registries);
        for (var entry : placements.entrySet()) {
            var encoded = StructurePlacement.CODEC.encodeStart(ops, entry.getValue()).getOrThrow().getAsJsonObject();
            var gaps = new EnumMap<StructureFamily, Integer>(StructureFamily.class);
            for (String name : List.of("exclusion_zone", "enhanced_exclusion_zone"))
                if (encoded.has(name)) collect(encoded.getAsJsonObject(name), registries, gaps);
            result.put(entry.getKey(), new Rule(Map.copyOf(gaps)));
        }
        return new YungTempleRules(result);
    }

    private static void collect(JsonObject zone, RegistryAccess registries, Map<StructureFamily, Integer> gaps) {
        int distance = zone.get("chunk_count").getAsInt();
        if (distance < 0 || distance > 128) throw new IllegalStateException("Review changed YUNG exclusion distance");
        var sets = registries.registryOrThrow(Registries.STRUCTURE_SET);
        var selected = new ArrayList<Holder<StructureSet>>();
        JsonElement target = zone.get("other_set");
        var references = target.isJsonArray() ? target.getAsJsonArray().asList() : List.of(target);
        for (var reference : references) {
            String id = reference.getAsString();
            if (id.startsWith("#")) selected.addAll(sets.getTag(TagKey.create(Registries.STRUCTURE_SET,
                    ResourceLocation.parse(id.substring(1)))).orElseThrow().stream().toList());
            else selected.add(sets.getHolderOrThrow(net.minecraft.resources.ResourceKey.create(Registries.STRUCTURE_SET,
                    ResourceLocation.parse(id))));
        }
        for (var holder : selected) {
            var membership = new HashMap<String, StructureFamily>();
            for (var entry : holder.value().structures()) membership.put(entry.structure().unwrapKey().orElseThrow()
                    .location().toString(), StructureMembership.family(entry.structure()));
            var family = StructureFamily.forSet(membership.keySet(), membership::get);
            if (family == null) throw new IllegalStateException("Review unmanaged YUNG exclusion target " + holder.unwrapKey());
            gaps.merge(family, distance, Math::max);
        }
    }

    boolean allows(StructureLayout layout, List<StructureLayout> sameFamily,
            Map<StructureFamily, List<StructureLayout>> earlier, StructurePlacementPassContext pass) {
        var rule = rules.get(layout.id());
        if (rule == null) return true;
        var root = layout.pieces().getFirst();
        int x = Math.floorDiv(root.minX() + root.maxX(), 2), z = Math.floorDiv(root.minZ() + root.maxZ(), 2);
        for (var gap : rule.gaps().entrySet()) {
            var neighbors = gap.getKey() == layout.family() ? sameFamily : earlier.getOrDefault(gap.getKey(), List.of());
            for (var other : neighbors) {
                var anchor = other.pieces().getFirst();
                int ox = Math.floorDiv(anchor.minX() + anchor.maxX(), 2), oz = Math.floorDiv(anchor.minZ() + anchor.maxZ(), 2);
                if (near(x, z, ox, oz, gap.getValue())) return false;
            }
        }
        // Native temples search a 48-block square in two-quart steps at Y120.
        // Use the same horizontal scale at each actual island surface; physical
        // accepted rivers are painted after biome fill, so include their plan too.
        for (int dx = -48; dx <= 48; dx += 8) for (int dz = -48; dz <= 48; dz += 8) {
            int sx = x + dx, sz = z + dz;
            var column = pass.islandField().sampleColumn(sx, sz, pass.settings());
            if (!column.exists()) continue;
            var biome = pass.generator().projectedBiomeSource().getNoiseBiome(sx >> 2, column.topY() >> 2,
                    sz >> 2, pass.randomState().sampler());
            if (biome.is(BiomeTags.IS_OCEAN) || biome.is(BiomeTags.IS_RIVER)
                    || pass.islandField().sampleHydrologyColumn(sx, sz, pass.settings()).wet()) return false;
        }
        return true;
    }

    static boolean near(int x, int z, int otherX, int otherZ, int chunks) {
        return Math.abs((long)Math.floorDiv(x, 16) - Math.floorDiv(otherX, 16)) <= chunks
                && Math.abs((long)Math.floorDiv(z, 16) - Math.floorDiv(otherZ, 16)) <= chunks;
    }
}
