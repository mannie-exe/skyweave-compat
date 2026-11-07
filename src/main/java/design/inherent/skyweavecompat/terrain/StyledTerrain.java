package design.inherent.skyweavecompat.terrain;

/** One immutable modifier resolver shared by a native field and its samplers. */
public interface StyledTerrain {
    IslandStyles skyweave$styles();
    void skyweave$styles(IslandStyles styles);
}
