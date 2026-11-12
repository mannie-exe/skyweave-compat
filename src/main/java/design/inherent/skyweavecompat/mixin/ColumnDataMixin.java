package design.inherent.skyweavecompat.mixin;

import org.sathrek.sky_archipelago.worldgen.generator.field.model.ColumnData;
import design.inherent.skyweavecompat.terrain.LayeredColumn;
import org.sathrek.sky_archipelago.worldgen.generator.field.TerrainColumn;
import org.spongepowered.asm.mixin.*;
import java.util.List;
import org.sathrek.sky_archipelago.worldgen.generator.field.model.ResolvedContributor;

@Mixin(value = ColumnData.class, remap = false)
public abstract class ColumnDataMixin implements LayeredColumn {
    @Unique private List<TerrainColumn> skyweave$exterior;
    @Unique private boolean skyweave$exactInterior;
    @Unique private List<ResolvedContributor> skyweave$contributors = List.of();
    @Shadow public abstract List<TerrainColumn> segments();
    @Override public boolean skyweave$hasExterior() { return skyweave$exterior != null; }
    @Override public boolean skyweave$requiresExactInterior() { return skyweave$exactInterior; }
    @Override public void skyweave$requireExactSampling() { skyweave$exactInterior = true; }
    @Override public List<TerrainColumn> skyweave$exterior() {
        return skyweave$exterior == null ? segments() : skyweave$exterior;
    }
    @Override public void skyweave$exterior(List<TerrainColumn> exterior, boolean exactInterior) {
        if (skyweave$exterior != null) throw new IllegalStateException("Column exterior already bound");
        skyweave$exterior = List.copyOf(exterior);
        skyweave$exactInterior |= exactInterior;
    }
    @Override public List<ResolvedContributor> skyweave$contributors() { return skyweave$contributors; }
    @Override public void skyweave$contributors(List<ResolvedContributor> contributors) {
        if (!skyweave$contributors.isEmpty()) throw new IllegalStateException("Column contributors already bound");
        skyweave$contributors = List.copyOf(contributors);
    }
}
