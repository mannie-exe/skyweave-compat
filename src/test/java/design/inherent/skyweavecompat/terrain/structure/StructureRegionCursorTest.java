/* SPDX-License-Identifier: Apache-2.0 */
package design.inherent.skyweavecompat.terrain.structure;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class StructureRegionCursorTest {
    @TempDir Path root;

    @Test void pagesCompleteInventoryAndDeduplicatesPendingAndCachedRegions() throws Exception {
        var expected = new HashSet<ChunkPos>();
        for (int x = -600; x < 600; x++) {
            Files.createFile(root.resolve("r." + x + ".0.mca"));
            expected.add(new ChunkPos(x, 0));
        }
        expected.add(new ChunkPos(700, 0));
        var found = new HashSet<ChunkPos>(); int pages = 0;
        try (var cursor = new StructureRegionCursor(root,
                List.of(new ChunkPos(0, 0), new ChunkPos(0, 0), new ChunkPos(700, 0)), -600 * 32, 0, 700 * 32, 31)) {
            while (true) {
                var page = cursor.next(); pages++;
                assertTrue(page.positions().size() <= StructureRegionCursor.PAGE);
                for (var position : page.positions()) assertTrue(found.add(position), "Repeated region " + position);
                assertFalse(page.limited());
                if (page.complete()) break;
                assertTrue(pages < 10);
            }
            assertEquals(expected, found); assertTrue(pages > 1);
            assertTrue(cursor.next().complete());
        }
    }

    @Test void sparseDirectoryYieldsEvenWhenNoNameMatchesTheRadius() throws Exception {
        for (int x = 100; x < 700; x++) Files.createFile(root.resolve("r." + x + ".0.mca"));
        try (var cursor = new StructureRegionCursor(root, List.of(), 0, 0, 31, 31)) {
            var first = cursor.next();
            assertTrue(first.positions().isEmpty()); assertFalse(first.complete());
            while (!cursor.next().complete()) { /* Bounded pages eventually exhaust the directory. */ }
        }
    }

    @Test void canonicalNamesNegativeBoundsAndExplicitClosePreserveTheDirectory() throws Exception {
        Files.createFile(root.resolve("r.-1.-1.mca")); Files.createFile(root.resolve("r.00.0.mca"));
        Files.createFile(root.resolve("r.invalid.0.mca")); Files.createFile(root.resolve("r.0.0.mca"));
        try (var cursor = new StructureRegionCursor(root, List.of(), -1, -1, 0, 0)) {
            assertEquals(java.util.Set.of(new ChunkPos(-1, -1), new ChunkPos(0, 0)), new HashSet<>(cursor.next().positions()));
            cursor.close(); assertTrue(cursor.next().complete());
        }
        try (var files = Files.list(root)) { assertEquals(4, files.count()); }
    }

    @Test void missingFreshWorldFolderIsEmptyButAnInvalidStoragePathIsFailure() throws Exception {
        try (var cursor = new StructureRegionCursor(root.resolve("new-world"), List.of(), 0, 0, 31, 31)) {
            assertTrue(cursor.next().complete());
        }
        var file = root.resolve("not-a-folder"); Files.writeString(file, "held");
        assertThrows(java.io.IOException.class, () -> new StructureRegionCursor(file, List.of(), 0, 0, 31, 31));
        assertEquals("held", Files.readString(file));
    }
}
