package design.inherent.skyweavecompat.configuration;

import design.inherent.reform.definition.DocumentId;
import design.inherent.reform.edit.Candidate;
import design.inherent.reform.edit.DraftLimits;
import design.inherent.reform.edit.DraftTree;
import design.inherent.reform.operation.DocumentActions;
import design.inherent.reform.operation.DocumentBatch;
import design.inherent.reform.operation.OperationDescriptor.EntryPoint;
import design.inherent.reform.operation.OperationHandle;
import design.inherent.reform.owner.EditorActor;
import design.inherent.reform.owner.FileOwner;
import design.inherent.reform.owner.OwnerContext;
import design.inherent.reform.serialization.JsonInput;
import design.inherent.reform.serialization.JsonInputException;
import design.inherent.reform.serialization.JsonLimits;
import design.inherent.reform.serialization.SourcePath;
import design.inherent.reform.storage.OwnedFile;
import design.inherent.reform.storage.StorageBinding;
import design.inherent.reform.storage.TextStore;
import design.inherent.skyweavecompat.maps.MapZoomSettings;
import design.inherent.skyweavecompat.terrain.GenerationConfiguration;
import design.inherent.skyweavecompat.terrain.GenerationProfile;
import design.inherent.skyweavecompat.terrain.ShapeConfiguration;
import design.inherent.skyweavecompat.terrain.ShapeProfile;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class ReformProfileBatchTest {
    @TempDir Path root;

    @Test void validFinalPairWithoutASafePersistedPrefixRefusesBeforeWriting() throws Exception {
        Files.createDirectories(ConfigLocations.ownedDirectory(root));
        var generationPath = authored(GenerationConfiguration.FILE);
        var shapePath = authored(ShapeConfiguration.FILE);
        var generationJson = ProfileJson.object(Files.readString(generationPath));
        generationJson.addProperty("spacing", 95); generationJson.addProperty("jitter", 0);
        generationJson.addProperty("minimumClusterGap", 0);
        generationJson.addProperty("anchorMinRadius", 100); generationJson.addProperty("anchorMaxRadius", 100);
        generationJson.addProperty("companionMinOrbit", 0); generationJson.addProperty("companionMaxOrbit", 0);
        generationJson.addProperty("companionMinRadius", 1); generationJson.addProperty("companionMaxRadius", 1);
        var shapeJson = ProfileJson.object(Files.readString(shapePath));
        shapeJson.getAsJsonObject("outline").addProperty("aspectChance", 1);
        shapeJson.getAsJsonObject("outline").addProperty("maximumAspectRatio", 1.1025);
        shapeJson.getAsJsonObject("fringe").addProperty("enabled", false);
        shapeJson.getAsJsonObject("fringe").addProperty("disintegratingScale", 1);
        String originalGeneration = generationJson.toString(), originalShape = shapeJson.toString();
        Files.writeString(generationPath, originalGeneration);
        Files.writeString(shapePath, originalShape);
        var beforeShapes = ShapeConfiguration.parse(originalShape);
        var before = readGeneration(generationPath, shapePath);
        try (var context = new OwnerContext(Runnable::run, Runnable::run, () -> {}, OwnerContext.Limits.STANDARD)) {
            var access = context.access(() -> true, () -> true);
            var generationStore = ReformDocuments.json(root, GenerationConfiguration.FILE, ProfileEditPlan.GENERATION);
            var shapeStore = ReformDocuments.json(root, ShapeConfiguration.FILE, ProfileEditPlan.SHAPES);
            try (var generation = editor(context, access, defaults(context, generationStore), generationStore);
                    var shapes = editor(context, access, defaults(context, shapeStore), shapeStore)) {
                await(generation.edit(EntryPoint.GUI, "spacing", "434"));
                await(generation.edit(EntryPoint.GUI, "anchorMinRadius", "500"));
                await(generation.edit(EntryPoint.GUI, "anchorMaxRadius", "500"));
                await(shapes.edit(EntryPoint.GUI, "outline.maximumAspectRatio", "1"));
                await(shapes.edit(EntryPoint.GUI, "fringe.enabled", "true"));
                try (var batch = new DocumentBatch(context, access, new DocumentId("skyweave_compat", "profile_review"),
                        List.of(DocumentBatch.member(generation, DocumentBatch.Intent.COMMIT), DocumentBatch.member(shapes, DocumentBatch.Intent.COMMIT)),
                        ProfileEditPlan::review, DocumentBatch.Limits.STANDARD)) {
                    var review = await(batch.review(EntryPoint.GUI));
                    var after = review.values().candidate(ProfileEditPlan.GENERATION).orElseThrow();
                    var afterShapes = review.values().candidate(ProfileEditPlan.SHAPES).orElseThrow();
                    assertEquals(8, before.conflictCells(beforeShapes)); assertEquals(8, after.conflictCells(afterShapes));
                    assertEquals(9, before.conflictCells(afterShapes)); assertEquals(9, after.conflictCells(beforeShapes));
                    assertFalse(review.valid());
                    assertEquals("profile.order", review.check().documents().get(ProfileEditPlan.GENERATION.id()).issues().getFirst().code());
                    assertEquals(originalGeneration, Files.readString(generationPath));
                    assertEquals(originalShape, Files.readString(shapePath));
                    assertTrue(generation.view().dirty()); assertTrue(shapes.view().dirty());
                }
            }
        }
    }

    @Test void omittedPartnerAndInvalidFinalPairRefuseWithoutMaterializingDependencies() throws Exception {
        Files.createDirectories(ConfigLocations.ownedDirectory(root));
        var generationPath = authored(GenerationConfiguration.FILE);
        var shapePath = ConfigLocations.file(root, ShapeConfiguration.FILE);
        String original = Files.readString(generationPath);
        try (var context = new OwnerContext(Runnable::run, Runnable::run, () -> {}, OwnerContext.Limits.STANDARD)) {
            var access = context.access(() -> true, () -> true);
            var generationStore = ReformDocuments.json(root, GenerationConfiguration.FILE, ProfileEditPlan.GENERATION);
            var shapeStore = ReformDocuments.json(root, ShapeConfiguration.FILE, ProfileEditPlan.SHAPES);
            try (var generation = editor(context, access, defaults(context, generationStore), generationStore)) {
                await(generation.edit(EntryPoint.GUI, "spacing", "400"));
                await(generation.edit(EntryPoint.GUI, "jitter", "0"));
                try (var batch = new DocumentBatch(context, access, new DocumentId("skyweave_compat", "profile_review"),
                        List.of(DocumentBatch.member(generation, DocumentBatch.Intent.COMMIT)), ProfileEditPlan::review, DocumentBatch.Limits.STANDARD)) {
                    var review = await(batch.review(EntryPoint.GUI));
                    assertFalse(review.valid());
                    assertEquals("profile.dependency", review.check().documents().get(ProfileEditPlan.GENERATION.id()).issues().getFirst().code());
                    assertFalse(Files.exists(shapePath));
                }
                authored(ShapeConfiguration.FILE);
                try (var shapes = editor(context, access, defaults(context, shapeStore), shapeStore);
                        var batch = new DocumentBatch(context, access, new DocumentId("skyweave_compat", "profile_review"),
                                List.of(DocumentBatch.member(generation, DocumentBatch.Intent.COMMIT), DocumentBatch.member(shapes, DocumentBatch.Intent.CHECK)),
                                ProfileEditPlan::review, DocumentBatch.Limits.STANDARD)) {
                    var review = await(batch.review(EntryPoint.GUI));
                    assertFalse(review.valid());
                    assertEquals("profile.pair", review.check().documents().get(ProfileEditPlan.GENERATION.id()).issues().getFirst().code());
                }
                assertEquals(original, Files.readString(generationPath));
                assertTrue(generation.view().dirty());
            }
        }
    }

    @Test void changedCheckOnlyShapeStopsGenerationAfterAnEarlierVerifiedWrite() throws Exception {
        Files.createDirectories(ConfigLocations.ownedDirectory(root));
        var generationPath = authored(GenerationConfiguration.FILE);
        var shapePath = authored(ShapeConfiguration.FILE);
        var original = Files.readString(shapePath);
        var foreignEdit = original + "\n";
        var mapPath = authored(MapZoomSettings.FILE);
        var map = new AfterSavedMap(mapPath, () -> {
            try {
                Files.writeString(shapePath, foreignEdit);
            } catch (java.io.IOException failure) {
                throw new UncheckedIOException(failure);
            }
        });
        try (var context = new OwnerContext(Runnable::run, Runnable::run, () -> {}, OwnerContext.Limits.STANDARD)) {
            var access = context.access(() -> true, () -> true);
            var generationStore = ReformDocuments.json(root, GenerationConfiguration.FILE, ProfileEditPlan.GENERATION);
            var shapeStore = ReformDocuments.json(root, ShapeConfiguration.FILE, ProfileEditPlan.SHAPES);
            try (var first = editor(context, access, new FileOwner<>(context, map, 8), map);
                    var checked = editor(context, access, defaults(context, shapeStore), shapeStore);
                    var next = editor(context, access, defaults(context, generationStore), generationStore)) {
                await(first.edit(EntryPoint.GUI, "zoomMaximum", "16"));
                await(next.edit(EntryPoint.GUI, "spacing", "2600"));
                var mapWrite = DocumentBatch.member(first, DocumentBatch.Intent.COMMIT);
                var shapeCheck = DocumentBatch.member(checked, DocumentBatch.Intent.CHECK);
                var generationWrite = DocumentBatch.member(next, DocumentBatch.Intent.COMMIT);
                try (var batch = new DocumentBatch(context, access, new DocumentId("skyweave_compat", "profile_review"),
                        List.of(mapWrite, shapeCheck, generationWrite),
                        ProfileEditPlan::review,
                        DocumentBatch.Limits.STANDARD)) {
                    var review = await(batch.review(EntryPoint.GUI));
                    assertTrue(review.valid());
                    var result = await(batch.commit(EntryPoint.GUI, review));
                    assertEquals(16, MapZoomSettings.parse(Files.readString(mapPath)).zoomMaximum());
                    assertEquals(foreignEdit, Files.readString(shapePath));
                    assertEquals(2400, ProfileJson.generation(Files.readString(generationPath)).spacing(),
                            "The second write ran after the checked shape bytes changed");
                    assertFalse(result.completed());
                    assertTrue(next.view().dirty());
                }
            }
        }
    }

    @Test void completeProfilesChooseSafeOrderedPrefixesAndStayStaged() throws Exception {
        Files.createDirectories(ConfigLocations.ownedDirectory(root));
        var generationPath = authored(GenerationConfiguration.FILE);
        var shapePath = authored(ShapeConfiguration.FILE);
        var runningGeneration = GenerationConfiguration.profile();
        var runningShapes = ShapeConfiguration.profile();
        try (var context = new OwnerContext(Runnable::run, Runnable::run, () -> {}, OwnerContext.Limits.STANDARD)) {
            var access = context.access(() -> true, () -> true);
            var generationStore = ReformDocuments.json(root, GenerationConfiguration.FILE, ProfileEditPlan.GENERATION);
            var shapeStore = ReformDocuments.json(root, ShapeConfiguration.FILE, ProfileEditPlan.SHAPES);
            try (var generation = editor(context, access, defaults(context, generationStore), generationStore);
                    var shapes = editor(context, access, defaults(context, shapeStore), shapeStore)) {
                await(generation.edit(EntryPoint.GUI, "spacing", "400"));
                await(generation.edit(EntryPoint.GUI, "jitter", "0"));
                await(shapes.edit(EntryPoint.GUI, "enabled", "false"));
                var gen = DocumentBatch.member(generation, DocumentBatch.Intent.COMMIT);
                var shape = DocumentBatch.member(shapes, DocumentBatch.Intent.COMMIT);
                committedPair(context, access, List.of(gen, shape), List.of(shape.document(), gen.document()));
                assertEquals(400, readGeneration(generationPath, shapePath).spacing());
                await(generation.edit(EntryPoint.GUI, "spacing", "2400"));
                await(generation.edit(EntryPoint.GUI, "jitter", "750"));
                await(shapes.edit(EntryPoint.GUI, "enabled", "true"));
                committedPair(context, access, List.of(shape, gen), List.of(gen.document(), shape.document()));
                assertEquals(2400, readGeneration(generationPath, shapePath).spacing());
                assertSame(runningGeneration, GenerationConfiguration.profile());
                assertSame(runningShapes, ShapeConfiguration.profile());
            }
        }
    }

    private static GenerationProfile readGeneration(Path generation, Path shapes) throws Exception {
        return ProfileJson.generation(Files.readString(generation)).validateAgainst(ShapeConfiguration.parse(Files.readString(shapes)));
    }

    private static void committedPair(OwnerContext context, OwnerContext.Access access,
            List<DocumentBatch.Member<?>> members, List<DocumentId> order) throws Exception {
        try (var batch = new DocumentBatch(context, access, new DocumentId("skyweave_compat", "profile_review"),
                members, ProfileEditPlan::review,
                DocumentBatch.Limits.STANDARD)) {
            var review = await(batch.review(EntryPoint.GUI));
            assertTrue(review.valid(), review.check().toString());
            assertEquals(order, review.check().plan().orElseThrow().writes());
            var result = await(batch.commit(EntryPoint.GUI, review));
            assertTrue(result.completed(), result.report().toString());
        }
    }

    private Path authored(String filename) throws Exception {
        var path = ConfigLocations.file(root, filename);
        Files.writeString(path, JsonSettings.find(filename).defaults());
        return path;
    }

    private static <T> FileOwner<T> defaults(OwnerContext context, TextStore<T> store) {
        return FileOwner.newWorldDefaults(context, store, 8, FileOwner.Initialization.DEFER_MISSING);
    }

    private static <T> DocumentActions<T> editor(OwnerContext context, OwnerContext.Access access,
            FileOwner<T> owner, TextStore<T> store) throws Exception {
        var session = owner.open(access);
        var actions = new DocumentActions<>(context, access, new EditorActor<>(context, session,
                store.definition(), store.valueLimits(), store.draftLimits(), 16));
        await(actions.load(EntryPoint.GUI));
        return actions;
    }

    private static <T> T await(OperationHandle<T> operation) throws Exception {
        return DocumentActions.result(operation).toCompletableFuture().get(5, TimeUnit.SECONDS);
    }

    /** A test-only external edit made after real Hoofprint persistence and readback. */
    private static final class AfterSavedMap extends TextStore<MapZoomSettings> {
        private final Runnable externalEdit;
        AfterSavedMap(Path path, Runnable externalEdit) {
            super(MapZoomSettings.definition(), new OwnedFile(StorageBinding.relative(path.getParent(),
                    path.getFileName().toString()), ConfigFile.LIMIT),
                    new JsonLimits(ConfigFile.LIMIT, 16, 8192, 4096, 128, 4096), DraftLimits.CONFIGURATION,
                    "json", "Hoofprint fixture");
            this.externalEdit = externalEdit;
        }
        @Override protected DraftTree decode(String text) {
            try {
                return DraftTree.fromParsed(definition.type(), JsonInput.read(text, jsonLimits), SourcePath.ROOT, draftLimits);
            } catch (JsonInputException invalid) {
                throw new IllegalArgumentException(invalid);
            }
        }
        @Override protected String encode(Read<MapZoomSettings> baseline, Candidate<MapZoomSettings> candidate) {
            return candidate.authoredJson() + "\n";
        }
        @Override protected void validateEncoded(String text) {
            MapZoomSettings.parse(text);
        }
        @Override public Write<MapZoomSettings> write(PreparedWrite<MapZoomSettings> prepared,
                Runnable currentLifetime) {
            var result = super.write(prepared, currentLifetime);
            if (result.persistence().state() == OwnedFile.Persistence.SAVED) externalEdit.run();
            return result;
        }
    }
}
