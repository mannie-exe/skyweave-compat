package design.inherent.skyweavecompat.terrain.structure;

import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class StructureVolumeTest {
    @Test void neighboringVillageRegionsCanOverlapWithoutBuildingsColliding() {
        var first = List.of(new StructureVolume.Box(0, 0, 0, 4, 4, 4), new StructureVolume.Box(100, 0, 100, 104, 4, 104));
        var second = List.of(new StructureVolume.Box(60, 0, 0, 64, 4, 4));
        assertTrue(StructureVolume.bounds(first).intersects(StructureVolume.bounds(second), 6));
        assertFalse(StructureVolume.conflicts(first, second, 6));
        assertTrue(StructureVolume.conflicts(first, List.of(new StructureVolume.Box(4, 4, 4, 8, 8, 8)), 0));
    }

    @Test void nativePaddingAndVerticalSeparationArePreservedWithoutIntegerOverflow() {
        var box = new StructureVolume.Box(0, 0, 0, 4, 4, 4);
        assertTrue(box.intersects(new StructureVolume.Box(0, 16, 0, 4, 20, 4), 6));
        assertFalse(box.intersects(new StructureVolume.Box(0, 17, 0, 4, 20, 4), 6));
        assertFalse(new StructureVolume.Box(Integer.MIN_VALUE, 0, 0, Integer.MIN_VALUE + 4, 4, 4)
                .intersects(new StructureVolume.Box(Integer.MAX_VALUE - 4, 0, 0, Integer.MAX_VALUE, 4, 4), 6));
        assertThrows(IllegalArgumentException.class, () -> StructureVolume.conflicts(List.of(), List.of(), -1));
        assertThrows(IllegalArgumentException.class, () -> StructureVolume.bounds(List.of()));
    }

    @Test void differentFamilyPaddingDoesNotAddVillageClearanceToUndergroundPieces() {
        var village = new StructureVolume.Box(0, 100, 0, 10, 110, 10);
        var underground = new StructureVolume.Box(0, 80, 0, 10, 93, 10);
        assertFalse(village.intersects(underground, 6, 0));
        assertTrue(village.intersects(new StructureVolume.Box(0, 80, 0, 10, 94, 10), 6, 0));
        assertEquals(village.intersects(underground, 6, 0), underground.intersects(village, 0, 6));
        assertThrows(IllegalArgumentException.class, () -> village.intersects(underground, 0, -1));
    }
}
