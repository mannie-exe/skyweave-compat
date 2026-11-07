package design.inherent.skyweavecompat.terrain.structure;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class StructureLayoutTest {
    @Test void nativeInflatedBoundsIdentifyAPlanAndPortalSpreadRetainsItsOccupiedVolume() {
        var site = new StructureSelection.Site(0, 0);
        var piece = new StructureVolume.Box(0, 0, 0, 4, 4, 4);
        var nativeBounds = new StructureVolume.Box(-12, -12, -12, 16, 16, 16);
        var first = new StructureLayout(ResourceLocation.withDefaultNamespace("village_plains"), site,
                List.of(piece), 6, nativeBounds, nativeBounds);
        assertTrue(first.matches(nativeBounds));
        assertTrue(first.matches(piece));
        var spread = new StructureVolume.Box(-14, -9, -14, 18, 5, 18);
        var portal = new StructureLayout(ResourceLocation.withDefaultNamespace("ruined_portal"), site,
                List.of(piece), 1, piece, spread);
        var nearby = new StructureLayout(ResourceLocation.withDefaultNamespace("pillager_outpost"), site,
                List.of(new StructureVolume.Box(12, 0, 0, 14, 4, 4)), 2);
        assertTrue(portal.matches(spread));
        assertFalse(piece.intersects(nearby.pieces().getFirst(), 1, 2));
        assertTrue(portal.conflicts(nearby));
    }

    @Test void onlyNeighboringVillagesUseTheirSeparateBuildingsInsteadOfTheWholeRegion() {
        var site = new StructureSelection.Site(0, 0);
        var buildings = List.of(new StructureVolume.Box(0, 0, 0, 4, 4, 4), new StructureVolume.Box(100, 0, 100, 104, 4, 104));
        var region = new StructureVolume.Box(-12, -12, -12, 116, 16, 116);
        var first = new StructureLayout(ResourceLocation.withDefaultNamespace("village_plains"), site, buildings, 6, region, region);
        var pieces = List.of(new StructureVolume.Box(60, 0, 0, 64, 4, 4));
        var neighbor = new StructureLayout(ResourceLocation.withDefaultNamespace("village_taiga"), site, pieces, 6);
        assertFalse(first.conflicts(neighbor));
        var temple = new StructureLayout(ResourceLocation.withDefaultNamespace("jungle_pyramid"), site, pieces, 1);
        assertTrue(first.conflicts(temple));
    }

    @Test void differentFamiliesCanUseTheSameHorizontalSiteWithoutOverwritingTheirGeometry() {
        var site = new StructureSelection.Site(4, -5);
        var village = new StructureLayout(ResourceLocation.withDefaultNamespace("village_plains"), site,
                List.of(new StructureVolume.Box(64, 300, -80, 70, 310, -74)), 6);
        var mine = new StructureLayout(ResourceLocation.withDefaultNamespace("mineshaft"), site,
                List.of(new StructureVolume.Box(64, 200, -80, 70, 210, -74)), 0);
        var mutable = new ArrayList<>(List.of(village));
        var plan = new StructureDistribution.Plan(new StructureSelection.Owner(0, 0), Map.of(), Map.of(),
                Map.of(StructureFamily.VILLAGES, mutable, StructureFamily.MINESHAFTS, List.of(mine)));
        mutable.clear();
        assertSame(village, plan.layout(StructureFamily.VILLAGES, site));
        assertSame(mine, plan.layout(StructureFamily.MINESHAFTS, site));
        assertFalse(village.conflicts(mine));
        assertNull(plan.layout(StructureFamily.OUTPOSTS, site));
        assertThrows(UnsupportedOperationException.class, () -> plan.layouts().get(StructureFamily.VILLAGES).clear());
    }
}
