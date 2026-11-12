package design.inherent.skyweavecompat.configuration;

import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class EditSessionTest {
    @Test void acknowledgementPreservesNewerRawInputAndOtherFieldEdits() {
        var session = new EditSession<>(Map.of("min", "1", "max", "9"));
        session.set("min", "2"); var submitted = session.submit();
        session.set("min", "-"); session.set("max", "12");
        session.acknowledge(submitted, submitted.values());
        assertEquals("2", session.saved("min")); assertEquals("-", session.value("min"));
        assertEquals(Map.of("min", "-", "max", "12"), session.changes());
        assertThrows(UnsupportedOperationException.class, () -> submitted.values().put("min", "4"));
    }
    @Test void revertWhileSavingBecomesADraftAgainstTheWrittenBaseline() {
        var session = new EditSession<>(Map.of("enabled", false));
        session.set("enabled", true); var submitted = session.submit(); session.discard();
        session.acknowledge(submitted, submitted.values());
        assertTrue(session.saved("enabled")); assertFalse(session.value("enabled"));
        assertTrue(session.dirty()); session.discard(); assertTrue(session.value("enabled"));
    }
    @Test void unchangedSubmissionClearsOnlyAcknowledgedEditsAndAcceptsCanonicalReadback() {
        var session = new EditSession<>(Map.of("number", "1"));
        session.set("number", "2.0"); var submitted = session.submit();
        session.acknowledge(submitted, Map.of("number", "2"));
        assertFalse(session.dirty()); assertEquals("2", session.value("number"));
        assertThrows(IllegalStateException.class, () -> session.acknowledge(submitted, submitted.values()));
    }
    @Test void reloadAndForeignSessionInvalidateOldAcknowledgements() {
        var session = new EditSession<>(Map.of("n", "1"));
        session.set("n", "2"); var submitted = session.submit(); session.reload(Map.of("n", "3"));
        assertThrows(IllegalStateException.class, () -> session.acknowledge(submitted, submitted.values()));
        assertEquals("3", session.value("n"));
        var foreign = new EditSession<>(Map.of("n", "3"));
        assertThrows(IllegalStateException.class, () -> session.acknowledge(foreign.submit(), Map.of("n", "3")));
    }
    @Test void unknownKeysAndSchemaChangesCannotDiscardDrafts() {
        var session = new EditSession<>(Map.of("n", "1")); session.set("n", "2");
        assertThrows(IllegalArgumentException.class, () -> session.set("absent", "4"));
        assertThrows(IllegalArgumentException.class, () -> session.reload(Map.of("other", "1")));
        assertThrows(IllegalArgumentException.class, () -> session.acknowledge(session.submit(), Map.of("other", "1")));
        assertEquals("2", session.value("n")); assertTrue(session.dirty());
    }
}
