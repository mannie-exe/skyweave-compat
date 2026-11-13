package design.inherent.skyweavecompat.configuration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class SettingsReceiptsTest {
    @TempDir Path directory;
    private SettingsCommit.Write failing(String name, String action) throws Exception {
        Files.writeString(directory.resolve(name),"before"); var file=new ConfigFile(directory,name);
        return new SettingsCommit.Write(name,new SettingsCommit.Text("before","after"),() -> file.check("before"),() -> {
            if (action.equals("written")) file.write("before","after");
            if (action.equals("foreign")) Files.writeString(file.path(),"external");
            if (action.equals("unreadable")) Files.delete(file.path());
            throw new IOException("owner failed");
        }, () -> new SettingsCommit.Observation(SettingsCommit.Persistence.WRITTEN,"after"),
                () -> SettingsCommit.observe(file,"before","after"::equals));
    }
    @Test void preflightRefusalHasAnOutcomeForEveryOwnerWithoutClaimingWrites() {
        var good=new SettingsCommit.Write("first",() -> {},() -> fail("Must not write"));
        var bad=new SettingsCommit.Write("second",() -> {throw new IOException("changed");},() -> fail("Must not write"));
        var result=SettingsCommit.save(List.of(good,bad));
        assertEquals(List.of(SettingsCommit.Persistence.UNATTEMPTED,SettingsCommit.Persistence.REFUSED),result.receipts().stream().map(SettingsCommit.Receipt::state).toList());
        assertTrue(result.written().isEmpty()); assertFalse(result.success());
    }
    @Test void aThrowAfterObservedPersistenceDoesNotEraseItOrAttemptLaterOwners() throws Exception {
        var first=new SettingsCommit.Write("first",() -> {},() -> {});
        var result=SettingsCommit.save(List.of(first,failing("second","written"),new SettingsCommit.Write("third",() -> {},() -> fail("Not attempted"))));
        assertEquals(List.of("first","second"),result.written()); assertEquals("second",result.failed());
        assertEquals("after",result.receipts().get(1).savedText()); assertNotNull(result.receipts().get(1).failure());
        assertEquals(SettingsCommit.Persistence.UNATTEMPTED,result.receipts().getLast().state());
    }
    @Test void unchangedDiskIsFailedButForeignOrUnreadableDiskIsUnknown() throws Exception {
        for (String action : List.of("unchanged","foreign","unreadable")) {
            var receipt=SettingsCommit.save(List.of(failing(action,action))).receipts().getFirst();
            assertEquals(action.equals("unchanged") ? SettingsCommit.Persistence.FAILED : SettingsCommit.Persistence.UNKNOWN,receipt.state());
            assertNull(receipt.savedText());
            if (action.equals("foreign")) assertEquals("external",Files.readString(directory.resolve(action)));
            if (action.equals("unreadable")) assertEquals(1,receipt.failure().getSuppressed().length);
        }
    }
    private JsonEdits json() throws Exception {
        var definition=JsonSettings.find("skyweave_compat-search.json");
        Files.writeString(directory.resolve(definition.filename()),definition.defaults()); return new JsonEdits(directory,definition);
    }
    @Test void deferredJsonAcknowledgementDoesNotMutateTheUiSessionOnAWorkerAndKeepsNewerTyping() throws Exception {
        var edits=json(); var acknowledgements=new ArrayDeque<Runnable>(); edits.set("timeLimitSeconds","300");
        var write=edits.prepare(acknowledgements::addLast); var outcome=new java.util.concurrent.atomic.AtomicReference<SettingsCommit.Result>();
        var thread=new Thread(() -> outcome.set(SettingsCommit.save(List.of(write)))); thread.start(); thread.join();
        assertTrue(outcome.get().success()); assertNotNull(outcome.get().receipts().getFirst().savedText());
        assertTrue(edits.dirty()); edits.set("timeLimitSeconds","200"); acknowledgements.removeFirst().run();
        assertEquals("200",edits.value("timeLimitSeconds")); assertTrue(edits.dirty());
        assertEquals(300,design.inherent.skyweavecompat.terrain.structure.SearchSettings.parse(Files.readString(directory.resolve(edits.definition().filename()))).timeLimitSeconds());
    }
    @Test void reloadDetachesAnOldAcknowledgementInsteadOfClobberingItsNewSession() throws Exception {
        var edits=json(); var queue=new ArrayDeque<Runnable>(); edits.set("timeLimitSeconds","300");
        assertTrue(SettingsCommit.save(List.of(edits.prepare(queue::addLast))).success());
        edits.reload(); edits.set("timeLimitSeconds","180"); queue.removeFirst().run();
        assertEquals("180",edits.value("timeLimitSeconds")); assertTrue(edits.dirty());
    }
}
