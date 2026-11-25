package design.inherent.skyweavecompat.configuration;

import design.inherent.skyweavecompat.terrain.structure.SearchSettings;
import design.inherent.skyweavecompat.wayfinder.WayfinderSettings;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SettingsPolicyTest {
    @Test void effectiveChangesDoNotRewriteAnAdmittedJobsSchedulingSnapshot() {
        var policy = new SettingsPolicy<>(SearchSettings.DEFAULT, () -> {});
        var admitted = policy.read(() -> true);
        var changed = new SearchSettings(1,1);
        var prepared = policy.prepare(admitted.revision(), changed, () -> true);
        assertEquals(SearchSettings.DEFAULT, policy.current());
        var applied = prepared.apply(() -> true);
        assertEquals(changed, applied.value()); assertNotSame(admitted.revision(), applied.revision());
        assertEquals(2, admitted.value().concurrentChunks()); assertEquals(5, admitted.value().tickBudgetMillis());
        assertThrows(IllegalStateException.class, () -> prepared.apply(() -> true));
        assertThrows(IllegalStateException.class, () -> policy.prepare(admitted.revision(), changed, () -> true));
        var identical = policy.prepare(applied.revision(), changed, () -> true).apply(() -> true);
        assertSame(applied.revision(), identical.revision());
    }
    @Test void authorityIsCheckedAtReadPrepareAndExecutionRatherThanCapturedAsPermission() {
        var allowed = new AtomicBoolean(true);
        var policy = new SettingsPolicy<>(SearchSettings.DEFAULT, () -> {});
        var read = policy.read(allowed::get);
        var prepared = policy.prepare(read.revision(), new SearchSettings(1,2), allowed::get);
        allowed.set(false);
        assertThrows(SecurityException.class, () -> policy.read(allowed::get));
        assertThrows(SecurityException.class, () -> policy.prepare(read.revision(), SearchSettings.DEFAULT, allowed::get));
        assertThrows(SecurityException.class, () -> prepared.apply(allowed::get));
        allowed.set(true);
        assertEquals(SearchSettings.DEFAULT, policy.current());
        assertSame(read.revision(), policy.read(allowed::get).revision());
        assertThrows(IllegalStateException.class, () -> prepared.apply(allowed::get));
    }
    @Test void equalValuesInDifferentServerLifecyclesNeverMakeTheirRevisionsInterchangeable() {
        var first = new SettingsPolicy<>(SearchSettings.DEFAULT, () -> {});
        var second = new SettingsPolicy<>(SearchSettings.DEFAULT, () -> {});
        var read = first.read(() -> true);
        var prepared = first.prepare(read.revision(),new SearchSettings(1,2),() -> true);
        assertThrows(IllegalStateException.class, () -> second.prepare(read.revision(),SearchSettings.DEFAULT,() -> true));
        first.close();
        assertThrows(IllegalStateException.class, () -> first.read(() -> true));
        assertThrows(IllegalStateException.class, () -> prepared.apply(() -> true));
        assertEquals(SearchSettings.DEFAULT, second.current());
    }
    @Test void publicationMustExecuteOnTheOwnerThreadAndStalePreparedChangesCannotWin() throws Exception {
        Thread owner = Thread.currentThread();
        var policy = new SettingsPolicy<>(SearchSettings.DEFAULT, () -> {
            if (Thread.currentThread() != owner) throw new IllegalStateException("Wrong thread");
        });
        var read = policy.read(() -> true);
        var older = policy.prepare(read.revision(),new SearchSettings(1,1),() -> true);
        var failures = new java.util.concurrent.atomic.AtomicInteger();
        var thread = new Thread(() -> {
            assertThrows(IllegalStateException.class, () -> policy.current()); failures.incrementAndGet();
            assertThrows(IllegalStateException.class, () -> older.apply(() -> true)); failures.incrementAndGet();
        });
        thread.start(); thread.join(); assertEquals(2,failures.get());
        policy.prepare(read.revision(),new SearchSettings(2,2),() -> true).apply(() -> true);
        assertThrows(IllegalStateException.class, () -> older.apply(() -> true));
        assertEquals(2,policy.current().tickBudgetMillis());
    }
    @Test void policyValuesRemainImmutableAfterTheAuthoringListChanges() {
        var blocked = new java.util.ArrayList<>(List.of("minecraft:water"));
        var initial = new WayfinderSettings(true,0,16,256,4,200,blocked);
        var policy = new SettingsPolicy<>(initial,() -> {});
        blocked.clear(); assertEquals(List.of("minecraft:water"),policy.current().blockedBlocks());
        assertThrows(UnsupportedOperationException.class, () -> policy.current().blockedBlocks().clear());
    }
}
