package design.inherent.skyweavecompat.terrain;

/** Keeps explicit fringe metadata separate from native coverage and attachment semantics. */
public interface FringeColumn {
    FragmentFringes.Fragment skyweave$fragment();
    void skyweave$fragment(FragmentFringes.Fragment fragment);
}
