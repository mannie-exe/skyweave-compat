package design.inherent.skyweavecompat.configuration;

import design.inherent.skyweavecompat.terrain.GenerationConfiguration;
import design.inherent.skyweavecompat.terrain.ShapeConfiguration;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class ProfileEditPlanTest {
    @TempDir Path directory;
    private JsonEdits create(String filename) throws IOException {
        var definition = JsonSettings.find(filename);
        Files.writeString(directory.resolve(filename), definition.defaults());
        return new JsonEdits(directory, definition);
    }
    private static void compact(JsonEdits generation) {
        generation.set("spacing", "400"); generation.set("jitter", "0");
    }
    private void validOnDisk() throws IOException {
        var shapes = ShapeConfiguration.parse(Files.readString(directory.resolve(ShapeConfiguration.FILE)));
        GenerationConfiguration.parse(Files.readString(directory.resolve(GenerationConfiguration.FILE)), shapes);
    }
    @Test void proposedPairsChooseSafePrefixesInBothDirectionsWithoutChangingRuntime() throws Exception {
        var runningGeneration = GenerationConfiguration.profile(); var runningShapes = ShapeConfiguration.profile();
        var generation = create(GenerationConfiguration.FILE); var shapes = create(ShapeConfiguration.FILE);
        compact(generation); shapes.set("enabled", "false");
        var result = ProfileEditPlan.prepare(directory, List.of(generation.prepare(), shapes.prepare())).save();
        assertTrue(result.success(), String.valueOf(result.failure()));
        assertEquals(List.of(ShapeConfiguration.FILE, GenerationConfiguration.FILE), result.written());
        validOnDisk(); assertFalse(generation.dirty()); assertFalse(shapes.dirty());
        generation.reset("spacing"); generation.reset("jitter"); shapes.set("enabled", "true");
        result = ProfileEditPlan.prepare(directory, List.of(shapes.prepare(), generation.prepare())).save();
        assertTrue(result.success());
        assertEquals(List.of(GenerationConfiguration.FILE, ShapeConfiguration.FILE), result.written());
        validOnDisk(); assertSame(runningGeneration, GenerationConfiguration.profile()); assertSame(runningShapes, ShapeConfiguration.profile());
    }
    @Test void invalidFinalBundleAndMissingDependencyDoNotWriteOrCreateFiles() throws Exception {
        var generation = create(GenerationConfiguration.FILE); var shapes = create(ShapeConfiguration.FILE);
        String original = Files.readString(directory.resolve(GenerationConfiguration.FILE));
        compact(generation);
        assertThrows(IllegalArgumentException.class, () -> ProfileEditPlan.prepare(directory, List.of(generation.prepare())));
        assertEquals(original, Files.readString(directory.resolve(GenerationConfiguration.FILE)));
        Files.delete(directory.resolve(ShapeConfiguration.FILE)); generation.set("spacing", "2600");
        assertThrows(IOException.class, generation::save);
        assertFalse(Files.exists(directory.resolve(ShapeConfiguration.FILE))); assertTrue(generation.dirty());
    }
    @Test void validFinalPairWithoutAnySafePersistedPrefixRefusesBeforeWriting() throws Exception {
        var generationDefinition = JsonSettings.find(GenerationConfiguration.FILE);
        var shapeDefinition = JsonSettings.find(ShapeConfiguration.FILE);
        var generationJson = com.google.gson.JsonParser.parseString(generationDefinition.defaults()).getAsJsonObject();
        generationJson.addProperty("spacing", 95); generationJson.addProperty("jitter", 0);
        generationJson.addProperty("minimumClusterGap", 0);
        generationJson.addProperty("anchorMinRadius", 100); generationJson.addProperty("anchorMaxRadius", 100);
        generationJson.addProperty("companionMinOrbit", 0); generationJson.addProperty("companionMaxOrbit", 0);
        generationJson.addProperty("companionMinRadius", 1); generationJson.addProperty("companionMaxRadius", 1);
        var shapeJson = com.google.gson.JsonParser.parseString(shapeDefinition.defaults()).getAsJsonObject();
        shapeJson.getAsJsonObject("outline").addProperty("aspectChance", 1);
        shapeJson.getAsJsonObject("outline").addProperty("maximumAspectRatio", 1.1025);
        shapeJson.getAsJsonObject("fringe").addProperty("enabled", false);
        shapeJson.getAsJsonObject("fringe").addProperty("disintegratingScale", 1);
        String originalGeneration = generationJson.toString(), originalShape = shapeJson.toString();
        Files.writeString(directory.resolve(GenerationConfiguration.FILE), originalGeneration);
        Files.writeString(directory.resolve(ShapeConfiguration.FILE), originalShape);
        validOnDisk();
        var generation = new JsonEdits(directory, generationDefinition); var shapes = new JsonEdits(directory, shapeDefinition);
        generation.set("spacing", "434"); generation.set("anchorMinRadius", "500"); generation.set("anchorMaxRadius", "500");
        shapes.set("outline.maximumAspectRatio", "1"); shapes.set("fringe.enabled", "true");
        // Both endpoints pass; each single-document intermediate needs nine cells, not eight.
        var beforeShapes = ShapeConfiguration.parse(originalShape); var afterShapes = ShapeConfiguration.parse(shapes.candidate());
        var before = ProfileJson.generation(originalGeneration); var after = ProfileJson.generation(generation.candidate());
        assertEquals(8, before.conflictCells(beforeShapes)); assertEquals(8, after.conflictCells(afterShapes));
        assertEquals(9, before.conflictCells(afterShapes)); assertEquals(9, after.conflictCells(beforeShapes));
        var refused = assertThrows(IllegalArgumentException.class,
                () -> ProfileEditPlan.prepare(directory, List.of(generation.prepare(), shapes.prepare())));
        assertTrue(refused.getMessage().contains("no safe partial-save order"));
        assertEquals(originalGeneration, Files.readString(directory.resolve(GenerationConfiguration.FILE)));
        assertEquals(originalShape, Files.readString(directory.resolve(ShapeConfiguration.FILE)));
        assertTrue(generation.dirty()); assertTrue(shapes.dirty());
    }
    @Test void partialWriteLeavesAValidPairAndUnattemptedDraft() throws Exception {
        var generation = create(GenerationConfiguration.FILE); var shapes = create(ShapeConfiguration.FILE);
        compact(generation); shapes.set("enabled", "false");
        var submitted = generation.prepare();
        var failing = new SettingsCommit.Write(submitted.filename(), submitted.text(), submitted::check,
                () -> { throw new IOException("native writer refused"); });
        var result = ProfileEditPlan.prepare(directory, List.of(failing, shapes.prepare())).save();
        assertFalse(result.success()); assertEquals(List.of(ShapeConfiguration.FILE), result.written());
        assertEquals(GenerationConfiguration.FILE, result.failed()); validOnDisk();
        assertTrue(generation.dirty()); assertFalse(shapes.dirty());
    }
    @Test void unchangedDependencyIsCheckedBeforeAndDuringTheBatch() throws Exception {
        var generation = create(GenerationConfiguration.FILE); create(ShapeConfiguration.FILE);
        generation.set("spacing", "2600");
        var shapePath = directory.resolve(ShapeConfiguration.FILE); String original = Files.readString(shapePath);
        var firstPlan = ProfileEditPlan.prepare(directory, List.of(generation.prepare()));
        Files.writeString(shapePath, original + " ");
        var refused = firstPlan.save(); assertTrue(refused.failure() instanceof ConfigFile.ChangedOnDisk);
        assertTrue(refused.written().isEmpty()); assertTrue(generation.dirty());
        Files.writeString(shapePath, original);
        var external = new SettingsCommit.Write("other", () -> {}, () -> Files.writeString(shapePath, original + " "));
        var result = ProfileEditPlan.prepare(directory, List.of(external, generation.prepare())).save();
        assertEquals(List.of("other"), result.written()); assertEquals(GenerationConfiguration.FILE, result.failed());
        assertTrue(result.failure() instanceof ConfigFile.ChangedOnDisk);
        assertEquals(2400, ProfileJson.generation(Files.readString(directory.resolve(GenerationConfiguration.FILE))).spacing());
    }
    @Test void dependencySymlinksAndDuplicateOwnersAreNotAdmitted() throws Exception {
        var generation = create(GenerationConfiguration.FILE); create(ShapeConfiguration.FILE);
        generation.set("spacing", "2600");
        var write = generation.prepare();
        assertThrows(IllegalArgumentException.class, () -> ProfileEditPlan.prepare(directory, List.of(write, write)));
        var shape = directory.resolve(ShapeConfiguration.FILE); var outside = directory.resolve("outside.json");
        Files.move(shape, outside); Files.createSymbolicLink(shape, outside);
        assertThrows(IOException.class, () -> ProfileEditPlan.prepare(directory, List.of(generation.prepare())));
        assertTrue(generation.dirty());
    }
}
