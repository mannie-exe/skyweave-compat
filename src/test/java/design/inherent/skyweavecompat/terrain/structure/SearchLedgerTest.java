/* SPDX-License-Identifier: Apache-2.0 */
package design.inherent.skyweavecompat.terrain.structure;

import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Random;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class SearchLedgerTest {
    @TempDir Path root;
    private static byte[] key(long n) { return ByteBuffer.allocate(8).putLong(n).array(); }

    @Test void exactMembershipSurvivesManyMemoryBatchesAndSignedCoordinates() throws Exception {
        var expected = new HashSet<Long>();
        var random = new Random(43);
        try (var ledger = new SearchLedger(8, 64, root)) {
            for (int n = 0; n < 40000; n++) {
                long value = random.nextInt(20000) - 10000;
                assertEquals(expected.add(value), ledger.add(key(value)), "Membership differs at " + n);
                assertTrue(ledger.buffered() < 64);
                assertTrue(ledger.runs() <= 9);
            }
            assertEquals(expected.size(), ledger.size());
            for (long value : expected) assertFalse(ledger.add(key(value)));
        }
        try (var files = Files.list(root)) { assertEquals(0, files.count()); }
    }

    @Test void callerMutationCannotChangeRetainedIdentityAndCleanupIsIdempotent() throws Exception {
        var ledger = new SearchLedger(8, 2, root);
        var original = key(7);
        assertTrue(ledger.add(original)); original[0]++;
        assertFalse(ledger.add(key(7)));
        assertTrue(ledger.add(original));
        ledger.close(); ledger.close();
        assertThrows(IllegalStateException.class, () -> ledger.add(key(7)));
        try (var files = Files.list(root)) { assertEquals(0, files.count()); }
    }

    @Test void cumulativeAdmissionsCrossFormerDefaultCapWithoutGrowingTheMemoryBatch() throws Exception {
        try (var ledger = new SearchLedger(8, 1024, root)) {
            for (int n = 0; n < 100000; n++) {
                assertTrue(ledger.add(key(n)));
                assertTrue(ledger.buffered() < 1024);
            }
            assertEquals(100000, ledger.size());
            for (int n : new int[]{0, 1023, 1024, 65535, 65536, 99999}) assertFalse(ledger.add(key(n)));
            assertTrue(ledger.runs() <= 7);
        }
        try (var files = Files.list(root)) { assertEquals(0, files.count()); }
    }

    @Test void concurrentProducerAndSavedInventoryAdmitEachAddressOnce() throws Exception {
        try (var ledger = new SearchLedger(8, 16, root); var executor = Executors.newFixedThreadPool(2)) {
            var a = executor.submit(() -> { for (int i = 0; i < 2000; i++) ledger.add(key(i)); return null; });
            var b = executor.submit(() -> { for (int i = 3000; i >= 1000; i--) ledger.add(key(i)); return null; });
            a.get(); b.get(); assertEquals(3001, ledger.size());
        }
    }

    @Test void fullWidthPhysicalIdentityIsComparedRatherThanItsPrefix() throws Exception {
        try (var ledger = new SearchLedger(36, 1, root)) {
            var a = new byte[36]; var b = a.clone(); b[35] = 1;
            assertTrue(ledger.add(a)); assertTrue(ledger.add(b));
            assertFalse(ledger.add(a)); assertFalse(ledger.add(b));
        }
    }

    @Test void diskFailureIsExplicitAndTheFailedLedgerCannotPublishMoreAdmissions() throws Exception {
        var file = root.resolve("not-a-directory"); Files.writeString(file, "held");
        try (var ledger = new SearchLedger(8, 1, file)) {
            assertThrows(java.io.IOException.class, () -> ledger.add(key(1)));
            assertThrows(IllegalStateException.class, () -> ledger.add(key(2)));
        }
        assertEquals("held", Files.readString(file));
    }

    @Test void incompleteSpillFailsInsteadOfReportingAbsenceAndReleasesItsOpenRun() throws Exception {
        var ledger = new SearchLedger(8, 2, root);
        assertTrue(ledger.add(key(1))); assertTrue(ledger.add(key(2)));
        Path folder;
        try (var paths = Files.list(root)) { folder = paths.findFirst().orElseThrow(); }
        Path run;
        try (var paths = Files.list(folder)) { run = paths.findFirst().orElseThrow(); }
        Files.write(run, new byte[0]);
        assertThrows(java.io.IOException.class, () -> ledger.add(key(1)));
        assertEquals(2, ledger.size());
        assertThrows(IllegalStateException.class, () -> ledger.add(key(3)));
        ledger.close();
        assertFalse(Files.exists(folder));
    }
}
