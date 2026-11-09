package design.inherent.skyweavecompat.terrain;

import org.sathrek.sky_archipelago.worldgen.generator.field.model.IslandDescriptor;

/** Shared planned component labels. Shoreline and fringe are independent of outline and interior. */
public record IslandComponents(Shoreline shoreline, Fringe fringe) {
    public enum Shoreline { NONE, NATIVE, COASTAL, PERIMETER }
    public enum Fringe { NONE, MIXED, DISINTEGRATING }

    public static IslandComponents resolve(IslandDescriptor island, IslandStyles styles) {
        var shoreline = !island.archetype().waterBased() ? Shoreline.NONE
                : OceanIslands.supportsShorelineTreatment(island.archetype())
                    ? Shoreline.valueOf(OceanConfiguration.profile().mode(island.seed()).name()) : Shoreline.NATIVE;
        return new IslandComponents(shoreline, styles.style(island).fringe());
    }
}
