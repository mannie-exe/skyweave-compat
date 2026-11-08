package design.inherent.skyweavecompat.terrain;

/** Keeps explicit apron metadata separate from native coverage and attachment semantics. */
public interface PerimeterColumn {
    GranularEdges.Fragment skyweave$fragment();
    void skyweave$fragment(GranularEdges.Fragment fragment);
}
