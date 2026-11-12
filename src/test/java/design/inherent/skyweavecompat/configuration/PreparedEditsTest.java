package design.inherent.skyweavecompat.configuration;

import com.electronwill.nightconfig.core.CommentedConfig;
import com.electronwill.nightconfig.toml.TomlWriter;
import design.inherent.skyweavecompat.Feature;
import design.inherent.skyweavecompat.FeatureSettings;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.neoforged.neoforge.common.ModConfigSpec;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class PreparedEditsTest {
    @TempDir Path directory;
    private JsonEdits json() throws IOException {
        var definition = JsonSettings.find("skyweave_compat-search.json");
        Files.writeString(directory.resolve(definition.filename()), definition.defaults());
        return new JsonEdits(directory, definition);
    }
    @Test void jsonPreparationFreezesTheSubmissionAndPreservesNewerTextAndRevert() throws Exception {
        var edits = json(); edits.set("timeLimitSeconds", "300"); var prepared = edits.prepare();
        edits.set("timeLimitSeconds", "200"); prepared.save();
        assertEquals(300, design.inherent.skyweavecompat.terrain.structure.SearchSettings.parse(
                Files.readString(directory.resolve(edits.definition().filename()))).timeLimitSeconds());
        assertEquals("200", edits.value("timeLimitSeconds")); assertTrue(edits.dirty());
        var reverted = edits.prepare(); edits.discard(); reverted.save();
        assertEquals("300", edits.value("timeLimitSeconds")); assertTrue(edits.dirty());
        assertEquals(200, design.inherent.skyweavecompat.terrain.structure.SearchSettings.parse(
                Files.readString(directory.resolve(edits.definition().filename()))).timeLimitSeconds());
        assertThrows(IllegalStateException.class, reverted::save);
    }
    @Test void reloadInvalidatesPreparedWorkBeforeDiskChanges() throws Exception {
        var edits = json(); edits.set("timeLimitSeconds", "300"); var prepared = edits.prepare();
        String before = Files.readString(directory.resolve(edits.definition().filename()));
        edits.reload(); assertThrows(IllegalStateException.class, prepared::save);
        assertEquals(before, Files.readString(directory.resolve(edits.definition().filename())));
    }
    @Test void propertiesPreparationKeepsALaterRevertAndComments() throws Exception {
        Path file = directory.resolve(FeatureSettings.FILE); Files.writeString(file, "# retained\n");
        var edits = StartupEdits.open(directory); edits.set(Feature.DH_CACHE, false);
        var prepared = edits.prepare(); edits.discard(); prepared.save();
        assertEquals("# retained\ndistant_horizons.world_cache=false\n", Files.readString(file));
        assertTrue(edits.value(Feature.DH_CACHE)); assertTrue(edits.dirty());
        assertFalse(edits.saved().enabled(Feature.DH_CACHE));
    }
    @Test void registeredReloadInvalidatesTheProposalBeforeNativeMutation() throws Exception {
        var builder = new ModConfigSpec.Builder(); builder.defineInRange("width", 2.0, 1.0, 8.0);
        var spec = builder.build(); var config = CommentedConfig.inMemory(); spec.correct(config);
        Path file = directory.resolve("createredstonelinkgui-client.toml");
        String original = new TomlWriter().writeToString(config); Files.writeString(file, original);
        var edits = new RegisteredEdits(directory, file.getFileName().toString(), spec);
        edits.set("width", "3.5"); var prepared = edits.prepare(); edits.reload(); edits.set("width", "4.25");
        assertThrows(IllegalStateException.class, prepared::save);
        assertEquals(original, Files.readString(file)); assertEquals("4.25", edits.value("width")); assertTrue(edits.dirty());
    }
    @Test void oversizedOutputIsRefusedDuringPreparationWithoutWriting() throws Exception {
        Path file = directory.resolve(FeatureSettings.FILE);
        String baseline = "#" + "a".repeat(ConfigFile.LIMIT - 2) + "\n"; Files.writeString(file, baseline);
        var edits = StartupEdits.open(directory); edits.set(Feature.DH_CACHE, false);
        assertThrows(IOException.class, edits::prepare);
        assertTrue(edits.dirty()); assertEquals(baseline, Files.readString(file));
    }
    @Test void batchCopiesItsOrderChecksAgainAndCannotBeReplayed() {
        var saved = new ArrayList<String>(); int[] checks = {0};
        var first = new SettingsCommit.Write("first", () -> checks[0]++, () -> saved.add("first"));
        var writes = new ArrayList<>(List.of(first)); var plan = SettingsCommit.prepare(writes); writes.clear();
        assertTrue(plan.save().success()); assertEquals(List.of("first"), saved); assertEquals(2, checks[0]);
        assertThrows(IllegalStateException.class, plan::save); assertThrows(IllegalStateException.class, first::save);
    }
}
