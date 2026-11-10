package design.inherent.skyweavecompat.terrain.structure;

/** Metadata supplied only by a version-qualified optional custom-jigsaw bridge. */
public interface IslandJigsaw {
    int skyweave$assemblyDepth();
    int skyweave$horizontalReach();
    java.util.List<net.minecraft.core.Holder<net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool>>
            skyweave$possibleRoots(net.minecraft.core.RegistryAccess registries);
    int skyweave$maximumRootOffset();
}
