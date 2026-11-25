/* SPDX-License-Identifier: Apache-2.0 */
package design.inherent.skyweavecompat.configuration;

import design.inherent.reform.edit.DraftNode;
import design.inherent.reform.storage.OwnedFile;
import design.inherent.reform.storage.TextStore;
import design.inherent.skyweavecompat.terrain.structure.SearchSettings;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class SearchSettingsMigrationTest {
    @TempDir Path root;

    @Test void runtimeAndActualTypedEditorRetireCutoffsWithoutImplicitWrites() throws Exception {
        String legacy = "{\r\n  \"candidateLimit\": 16, \"timeLimitSeconds\": 5,\r\n  \"concurrentChunks\": 1, \"tickBudgetMillis\": 2\r\n}\r\n";
        var path = ConfigLocations.file(root, SearchSettings.FILE);
        Files.createDirectories(path.getParent()); Files.writeString(path, legacy);
        assertEquals(new SearchSettings(16, 1, 2), SearchSettings.load(path.getParent()));
        assertEquals(legacy, Files.readString(path));
        var store = ReformDocuments.json(root, SearchSettings.FILE, PolicyDefinitions.SEARCH);
        var read = store.read();
        assertEquals(TextStore.State.READY, read.state(), read.report().toString());
        var draft = read.draft().orElseThrow();
        assertEquals("16", ((DraftNode.Scalar)draft.at(ReformDocuments.path("membershipBatchSize"))).raw());
        assertFalse(draft.contains(ReformDocuments.path("timeLimitSeconds")));
        assertFalse(draft.contains(ReformDocuments.path("candidateLimit")));
        assertEquals(OwnedFile.Persistence.UNCHANGED,
                store.write(store.prepareWrite(read, draft), () -> {}).persistence().state());
        assertEquals(legacy, Files.readString(path));
        var edited = draft.change(ReformDocuments.path("tickBudgetMillis"), ignored -> new DraftNode.Scalar("3"));
        assertEquals(OwnedFile.Persistence.SAVED,
                store.write(store.prepareWrite(read, edited), () -> {}).persistence().state());
        assertEquals(new SearchSettings(16, 1, 3), SearchSettings.load(path.getParent()));
        var saved = ProfileJson.object(Files.readString(path));
        assertEquals(java.util.Set.of("membershipBatchSize", "concurrentChunks", "tickBudgetMillis"), saved.keySet());
        assertFalse(Files.readString(path).replace("\r\n", "").contains("\n"));
    }
}
