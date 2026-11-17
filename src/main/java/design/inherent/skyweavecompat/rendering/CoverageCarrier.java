package design.inherent.skyweavecompat.rendering;

/** Private attachment carried through native source, builder, upload and publication ownership. */
public interface CoverageCarrier {
    CoverageColumns skyweave$coverage();
    void skyweave$coverage(CoverageColumns columns);
}
