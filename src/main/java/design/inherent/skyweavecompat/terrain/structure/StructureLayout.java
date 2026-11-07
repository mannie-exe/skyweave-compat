package design.inherent.skyweavecompat.terrain.structure;

import java.util.List;
import net.minecraft.resources.ResourceLocation;

/** A native fitted choice, retaining geometry rather than mutable native starts or reservations. */
public record StructureLayout(ResourceLocation id, StructureSelection.Site site, List<StructureVolume.Box> pieces, int padding,
        StructureVolume.Box nativeBounds, StructureVolume.Box reservationBounds) {
    public StructureLayout(ResourceLocation id, StructureSelection.Site site, List<StructureVolume.Box> pieces, int padding) {
        this(id, site, pieces, padding, StructureVolume.bounds(pieces), StructureVolume.bounds(pieces));
    }
    public StructureLayout {
        pieces = List.copyOf(pieces);
        if (pieces.isEmpty() || padding < 0) throw new IllegalArgumentException("Invalid structure volume");
    }
    public boolean conflicts(StructureLayout other) {
        // Preserve native occupied bounds except for the deliberate neighboring-village policy.
        if (StructureFamily.forId(id.toString()) != StructureFamily.VILLAGES
                || StructureFamily.forId(other.id().toString()) != StructureFamily.VILLAGES)
            return reservationBounds.intersects(other.reservationBounds(), padding, other.padding());
        return pieces.stream().anyMatch(a -> other.pieces().stream().anyMatch(b -> a.intersects(b, padding, other.padding())));
    }
    public boolean matches(StructureVolume.Box bounds) {
        // The village committer supplies its raw grounded union; other routes use terrain-adjusted bounds.
        return nativeBounds.equals(bounds) || reservationBounds.equals(bounds) || StructureVolume.bounds(pieces).equals(bounds);
    }
}
