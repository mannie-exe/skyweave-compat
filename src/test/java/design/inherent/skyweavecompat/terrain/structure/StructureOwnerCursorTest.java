/* SPDX-License-Identifier: Apache-2.0 */
package design.inherent.skyweavecompat.terrain.structure;

import design.inherent.skyweavecompat.terrain.ClusterLayout;
import design.inherent.skyweavecompat.terrain.IslandSearch;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class StructureOwnerCursorTest {
    @Test void completeOwnerDomainMatchesExistingBoundedPlannerAtNegativeAndOffsetOrigins() {
        for (var input : new int[][]{{0,0,1000,128,2},{-129,257,900,512,3},{29990000,-29990000,100,64,16}}) {
            var original = new IslandSearch<Integer>(input[0],input[1],input[2],input[3],input[4],Integer::compare);
            var expected = new HashSet<ClusterLayout.Cell>();
            while (!original.complete()) expected.add(original.next());
            var cursor = new StructureOwnerCursor(input[0],input[1],input[2],input[3],input[4]);
            var actual = new HashSet<ClusterLayout.Cell>();
            while (!cursor.complete()) assertTrue(actual.add(cursor.next()));
            assertEquals(expected, actual); assertEquals(expected.size(), cursor.visited());
            assertThrows(java.util.NoSuchElementException.class, cursor::next);
        }
    }

    @Test void nearRingTraversalUsesEveryPerimeterCellExactlyOnce() {
        var cursor = new StructureOwnerCursor(0,0,1,1,0);
        var actual = new ArrayList<ClusterLayout.Cell>();
        while (!cursor.complete()) actual.add(cursor.next());
        assertEquals(List.of(new ClusterLayout.Cell(0,0),new ClusterLayout.Cell(-1,-1),new ClusterLayout.Cell(-1,0),
                new ClusterLayout.Cell(-1,1),new ClusterLayout.Cell(0,-1),new ClusterLayout.Cell(0,1),
                new ClusterLayout.Cell(1,-1),new ClusterLayout.Cell(1,0),new ClusterLayout.Cell(1,1)),actual);
    }

    @Test void maximumRadiusContinuesPastFormer65536OwnerCellLimit() {
        var cursor = new StructureOwnerCursor(0,0,131072,512,3);
        var seen = new HashSet<ClusterLayout.Cell>();
        assertEquals(269361, cursor.total());
        while (!cursor.complete()) {
            var cell = cursor.next();
            assertTrue(cell.x() >= -259 && cell.x() <= 259 && cell.z() >= -259 && cell.z() <= 259);
            assertTrue(seen.add(cell));
            if (cursor.visited() == 65536) assertFalse(cursor.complete());
        }
        assertEquals(269361, seen.size());
    }
}
