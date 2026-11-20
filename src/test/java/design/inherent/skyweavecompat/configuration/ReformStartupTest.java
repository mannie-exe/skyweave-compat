package design.inherent.skyweavecompat.configuration;

import design.inherent.reform.definition.FieldPath;
import design.inherent.reform.edit.DraftNode;
import design.inherent.reform.edit.DraftTree;
import design.inherent.reform.owner.CommitReceipt;
import design.inherent.reform.owner.FileOwner;
import design.inherent.reform.owner.OwnerContext;
import design.inherent.reform.owner.PolicyRefusal;
import design.inherent.reform.storage.PropertiesStore;
import design.inherent.skyweavecompat.Feature;
import design.inherent.skyweavecompat.FeatureSettings;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletionException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class ReformStartupTest {
    @TempDir Path root;

    @Test void nativeReplacementPreservesCommentsOmissionsAndNoOpBytes() throws Exception {
        String original = "# retain me\r\nquestlog.interface : true\r\n! retained too\r\nterrain.preset=false";
        var path = properties(original);
        var active = FeatureSettings.read(new StringReader(original));
        var store = ReformDocuments.startup(root, active, Map.of(), true);
        var read = store.read();
        var draft = read.draft().orElseThrow();
        assertEquals(design.inherent.reform.storage.OwnedFile.Persistence.UNCHANGED,
                store.write(store.prepareWrite(read, draft), () -> {}).persistence().state());
        assertEquals(original, Files.readString(path));
        assertEquals(design.inherent.reform.storage.OwnedFile.Persistence.SAVED,
                store.write(store.prepareWrite(read, edit(draft, Feature.QUESTLOG_INTERFACE, false)), () -> {}).persistence().state());
        String saved = Files.readString(path);
        assertEquals("# retain me\r\nquestlog.interface=false\r\n! retained too\r\nterrain.preset=false", saved);
        var parsed = FeatureSettings.read(new StringReader(saved));
        assertFalse(parsed.enabled(Feature.QUESTLOG_INTERFACE));
        assertFalse(parsed.values().containsKey(Feature.SABLE_GRAVES));
        assertTrue(active.enabled(Feature.QUESTLOG_INTERFACE));
    }

    @Test void serializedOutputIsRefusedBeforeAnEffectCanBePrepared() throws Exception {
        String baseline = "#" + "a".repeat(ConfigFile.LIMIT - 2) + "\n";
        var path = properties(baseline);
        var active = FeatureSettings.read(new StringReader(baseline));
        var store = ReformDocuments.startup(root, active, Map.of(), true);
        try (var context = context(); var session = owner(context, store, active).open(context.access(() -> true, () -> true))) {
            var read = session.read().toCompletableFuture().join();
            var draft = read.saved().draft().orElseThrow();
            var noOp = session.commit(session.prepare(read, draft).toCompletableFuture().join()).toCompletableFuture().join();
            assertEquals(CommitReceipt.Persistence.UNCHANGED, noOp.persistence());
            var oversized = edit(draft, Feature.DH_CACHE, false);
            var failure = assertThrows(CompletionException.class, () -> session.prepare(read, oversized).toCompletableFuture().join());
            var refusal = assertInstanceOf(PolicyRefusal.class, failure.getCause());
            assertEquals("storage.output", refusal.report().issues().getFirst().code());
            assertEquals(baseline, Files.readString(path));
        }
    }

    @Test void nativeSyntaxAndExplicitFalseRemainWhileActiveSelectionStaysUnchanged() throws Exception {
        String syntax = "# owner comment\r\n  questlog\\.interface = tr\\\r\n  ue\r\n";
        var path = properties(syntax);
        var active = FeatureSettings.read(new StringReader(syntax));
        var store = ReformDocuments.startup(root, active, Map.of(), true);
        try (var context = context(); var session = owner(context, store, active).open(context.access(() -> true, () -> true))) {
            var read = session.read().toCompletableFuture().join();
            var draft = read.saved().draft().orElseThrow();
            assertTrue(read.effective().value().orElseThrow().enabled(Feature.DH_CACHE));
            var prepared = session.prepare(read, edit(draft, Feature.DH_CACHE, false)).toCompletableFuture().join();
            var receipt = session.commit(prepared).toCompletableFuture().join();
            assertEquals(CommitReceipt.Persistence.SAVED, receipt.persistence());
            assertEquals(CommitReceipt.Application.STAGED, receipt.application());
            assertEquals(syntax + Feature.DH_CACHE.key + "=false\r\n", Files.readString(path));
            assertEquals(Boolean.FALSE, FeatureSettings.read(new StringReader(Files.readString(path))).values().get(Feature.DH_CACHE));
            assertTrue(session.read().toCompletableFuture().join().effective().value().orElseThrow().enabled(Feature.DH_CACHE));
        }
    }

    @Test void incompatibleOptionalVersionCannotBeEnabledThroughTheStore() throws Exception {
        var feature = Feature.FTB_CREATIVE_RETURN;
        var path = properties(feature.key + "=false\n");
        var active = FeatureSettings.read(new StringReader(Files.readString(path)));
        var store = ReformDocuments.startup(root, active, Map.of("ftblibrary", "9.0"), true);
        var draft = store.read().draft().orElseThrow();
        assertFalse(store.inspectDraft(edit(draft, feature, true)).report().valid());
        assertEquals(feature.key + "=false\n", Files.readString(path));
    }

    private Path properties(String text) throws Exception {
        Files.createDirectories(ConfigLocations.ownedDirectory(root));
        return Files.writeString(ConfigLocations.file(root, FeatureSettings.FILE), text);
    }

    private static DraftTree edit(DraftTree draft, Feature feature, boolean enabled) {
        return draft.change(FieldPath.ROOT.field(feature.key),
                existing -> new DraftNode.Defaulted(true, new DraftNode.Scalar(Boolean.toString(enabled))));
    }

    private static OwnerContext context() {
        return new OwnerContext(Runnable::run, Runnable::run, () -> {}, OwnerContext.Limits.STANDARD);
    }

    private static FileOwner<FeatureSettings> owner(OwnerContext context, PropertiesStore<FeatureSettings> store,
            FeatureSettings active) {
        return FileOwner.startup(context, store, 8, Optional.of(ReformDocuments.selectedStartup(store.definition(), active)))
                .toCompletableFuture().join();
    }
}
