package design.inherent.skyweavecompat.configuration;

import design.inherent.reform.definition.FieldPath;
import design.inherent.reform.edit.DraftNode;
import design.inherent.reform.storage.OwnedFile;
import design.inherent.reform.storage.TextStore;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class ReformJsonStoreTest {
    @TempDir Path root;

    @Test void allCurrentOwnedProfilesRetainTheirCompleteDomainValidatorAndNoOpBytes() throws Exception {
        Files.createDirectories(ConfigLocations.ownedDirectory(root));
        var sources = new java.util.ArrayList<>(JsonSettings.FILES);
        if (sources.stream().noneMatch(source -> source.filename().equals("skyweave_compat-rivers.json")))
            sources.add(design.inherent.skyweavecompat.terrain.RiverConfiguration.definition());
        for (var source : sources) {
            var path = ConfigLocations.file(root, source.filename());
            String original = source.defaults();
            Files.writeString(path, original);
            var store = ReformJsonStore.map(root, source);
            var read = store.read();
            assertEquals(TextStore.State.READY, read.state(), source.filename() + " " + read.report());
            var result = store.write(store.prepareWrite(read, read.draft().orElseThrow()), () -> {});
            assertEquals(OwnedFile.Persistence.UNCHANGED, result.persistence().state(), source.filename());
            assertEquals(original, Files.readString(path), source.filename());
            var malformed = original.replaceFirst("\\{", "{\"unknown\": true,");
            Files.writeString(path, malformed);
            assertEquals(TextStore.State.INVALID, store.read().state(), source.filename());
            assertEquals(malformed, Files.readString(path));
        }
    }

    @Test void existingShapeOfAProfileIsNormalizedInTheDraftAndOnlyChangesOnExplicitSave() throws Exception {
        Files.createDirectories(ConfigLocations.ownedDirectory(root));
        var source = JsonSettings.find("skyweave_compat-structures.json");
        var old = ProfileJson.object(source.defaults());
        old.remove("villageFitting");
        var path = ConfigLocations.file(root, source.filename());
        Files.writeString(path, old.toString());
        var store = ReformJsonStore.map(root, source);
        var read = store.read();
        assertEquals(TextStore.State.READY, read.state(), read.report().toString());
        assertEquals(old.toString(), Files.readString(path));
        assertTrue(read.draft().orElseThrow().contains(FieldPath.ROOT.field("villageFitting")));
        var noop = store.write(store.prepareWrite(read, read.draft().orElseThrow()), () -> {});
        assertEquals(OwnedFile.Persistence.UNCHANGED, noop.persistence().state());
        assertEquals(old.toString(), Files.readString(path));
        var changed = read.draft().orElseThrow().change(FieldPath.ROOT.field("frequencyMultiplier"), ignored -> new DraftNode.Scalar("0.5"));
        var saved = store.write(store.prepareWrite(read, changed), () -> {});
        assertEquals(OwnedFile.Persistence.SAVED, saved.persistence().state());
        assertEquals(.5, ProfileJson.object(Files.readString(path)).get("frequencyMultiplier").getAsDouble());
        assertDoesNotThrow(() -> source.validate().accept(Files.readString(path)));
    }

    @Test void completeCrossFieldRulesAndStaleSourceRefuseWithoutReplacingTheFile() throws Exception {
        Files.createDirectories(ConfigLocations.ownedDirectory(root));
        var source = JsonSettings.find("skyweave_compat-interiors.json");
        var path = ConfigLocations.file(root, source.filename());
        Files.writeString(path, source.defaults());
        var store = ReformJsonStore.map(root, source);
        var read = store.read();
        var invalid = read.draft().orElseThrow()
                .change(FieldPath.ROOT.field("noiseChance"), ignored -> new DraftNode.Scalar("0.8"))
                .change(FieldPath.ROOT.field("hollowChance"), ignored -> new DraftNode.Scalar("0.8"));
        assertFalse(store.inspectDraft(invalid).report().valid());
        assertEquals(source.defaults(), Files.readString(path));
        var draft = read.draft().orElseThrow().change(FieldPath.ROOT.field("shellThickness"), ignored -> new DraftNode.Scalar("12"));
        var proposal = store.prepareWrite(read, draft);
        String external = source.defaults() + "\n";
        Files.writeString(path, external);
        assertEquals(OwnedFile.Persistence.REFUSED, store.write(proposal, () -> {}).persistence().state());
        assertEquals(external, Files.readString(path));
    }

    @Test void typedSourceUsesTheExistingNormalizationAndPreservesLineEndingsOnSave() throws Exception {
        Files.createDirectories(ConfigLocations.ownedDirectory(root));
        var source = JsonSettings.find("skyweave_compat-wayfinder.json");
        var old = ProfileJson.object(source.defaults());
        String original = new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(old).replace("\n", "\r\n") + "\r\n";
        var path = ConfigLocations.file(root, source.filename());
        Files.writeString(path, original);
        assertDoesNotThrow(() -> source.validate().accept(original));
        var store = ReformDocuments.json(root, source.filename(), PolicyDefinitions.WAYFINDER);
        var read = store.read();
        assertEquals(TextStore.State.READY, read.state(), read.report().toString());
        assertEquals(OwnedFile.Persistence.UNCHANGED,
                store.write(store.prepareWrite(read, read.draft().orElseThrow()), () -> {}).persistence().state());
        assertEquals(original, Files.readString(path));
        var changed = read.draft().orElseThrow().change(FieldPath.ROOT.field("stepsPerTick"), ignored -> new DraftNode.Scalar("128"));
        assertEquals(OwnedFile.Persistence.SAVED, store.write(store.prepareWrite(read, changed), () -> {}).persistence().state());
        String actual = Files.readString(path);
        assertFalse(actual.replace("\r\n", "").contains("\n"));
        assertEquals(128, design.inherent.skyweavecompat.wayfinder.WayfinderSettings.parse(actual).stepsPerTick());
    }
}
