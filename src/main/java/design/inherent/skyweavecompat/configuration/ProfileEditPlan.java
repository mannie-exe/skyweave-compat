package design.inherent.skyweavecompat.configuration;

import design.inherent.reform.definition.DocumentDefinition;
import design.inherent.reform.definition.DocumentId;
import design.inherent.reform.definition.FieldPath;
import design.inherent.reform.operation.DocumentBatch;
import design.inherent.reform.validation.Issue;
import design.inherent.reform.validation.ValidationReport;
import design.inherent.skyweavecompat.terrain.GenerationConfiguration;
import design.inherent.skyweavecompat.terrain.GenerationProfile;
import design.inherent.skyweavecompat.terrain.ShapeConfiguration;
import design.inherent.skyweavecompat.terrain.ShapeProfile;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** The declared generation/shape dependency, not a general file transaction or runtime mutation. */
public final class ProfileEditPlan {
    private static final List<String> PAIR = List.of(GenerationConfiguration.FILE, ShapeConfiguration.FILE);
    public static final DocumentDefinition<GenerationProfile> GENERATION =
            ReformDocuments.record(GenerationConfiguration.FILE, GenerationProfile.class, GenerationProfile.INITIAL);
    public static final DocumentDefinition<ShapeProfile> SHAPES =
            ReformDocuments.record(ShapeConfiguration.FILE, ShapeProfile.class, ShapeProfile.INITIAL);
    private static final Set<DocumentId> PROFILE_SOURCES = Set.of(GENERATION.id(), SHAPES.id());

    /** Both profiles must be in the reviewed source set, including a clean or hidden partner. */
    public static DocumentBatch.Check review(DocumentBatch.Values values) {
        var writes = values.documents().stream().filter(id -> values.intent(id) == DocumentBatch.Intent.COMMIT).toList();
        var dependent = writes.stream().filter(PROFILE_SOURCES::contains).toList();
        if (dependent.isEmpty()) return DocumentBatch.Check.VALID;
        var subject = dependent.getFirst();
        if (!values.documents().containsAll(PROFILE_SOURCES))
            return profileIssue(subject, "profile.dependency", "Review both generation and shape sources before saving");
        var beforeGeneration = values.baseline(GENERATION).orElse(null);
        var beforeShapes = values.baseline(SHAPES).orElse(null);
        var generation = values.candidate(GENERATION).orElse(null);
        var shapes = values.candidate(SHAPES).orElse(null);
        if (beforeGeneration == null || beforeShapes == null || generation == null || shapes == null)
            return profileIssue(subject, "profile.source", "Both profiles need complete source and proposed values");
        try {
            generation.validateAgainst(shapes);
        } catch (IllegalArgumentException invalid) {
            return profileIssue(subject, "profile.pair", invalid.getMessage());
        }
        if (dependent.size() == 2) {
            boolean generationFirst = dependent.getFirst().equals(GENERATION.id());
            boolean safeGenerationFirst = validPair(generation, beforeShapes);
            boolean safeShapesFirst = validPair(beforeGeneration, shapes);
            if (!safeGenerationFirst && !safeShapesFirst)
                return profileIssue(subject, "profile.order", "Generation and shape changes have no safe partial-save order");
            if (generationFirst ? !safeGenerationFirst : !safeShapesFirst) dependent = dependent.reversed();
        }
        var order = new ArrayList<DocumentId>();
        boolean added = false;
        for (var document : writes) {
            if (!PROFILE_SOURCES.contains(document)) order.add(document);
            else if (!added) {
                order.addAll(dependent);
                added = true;
            }
        }
        return DocumentBatch.Check.VALID.withPlan(new DocumentBatch.Plan(order, PROFILE_SOURCES));
    }

    private static DocumentBatch.Check profileIssue(DocumentId subject, String code, String message) {
        return new DocumentBatch.Check(Map.of(subject,
                new ValidationReport(List.of(Issue.error(code, FieldPath.ROOT, message)), true)), true);
    }

    private static boolean validPair(GenerationProfile generation, ShapeProfile shapes) {
        try {
            generation.validateAgainst(shapes);
            return true;
        } catch (IllegalArgumentException invalid) {
            return false;
        }
    }

    public static SettingsCommit.Prepared prepare(Path directory, List<SettingsCommit.Write> writes) throws IOException {
        writes = List.copyOf(writes);
        // Reject duplicate owners before deriving the shared read-set.
        var independent = SettingsCommit.prepare(writes);
        var dependent = writes.stream().filter(write -> PAIR.contains(write.filename())).toList();
        if (dependent.isEmpty()) return independent;
        var files = new LinkedHashMap<String, ConfigFile>();
        var expected = new LinkedHashMap<String, String>();
        for (String name : PAIR) {
            var file = new ConfigFile(directory, name);
            files.put(name, file); expected.put(name, file.read());
        }
        var target = new LinkedHashMap<>(expected);
        for (var write : dependent) {
            if (write.text() == null) throw new IllegalArgumentException("Profile writes require a frozen candidate");
            if (!expected.get(write.filename()).equals(write.text().baseline())) throw new ConfigFile.ChangedOnDisk();
            target.put(write.filename(), write.text().candidate());
        }
        validate(target);
        List<SettingsCommit.Write> order = dependent;
        if (!safePrefixes(expected, order)) {
            order = dependent.reversed();
            if (!safePrefixes(expected, order))
                throw new IllegalArgumentException("Generation and shape changes have no safe partial-save order");
        }
        var ordered = new ArrayList<SettingsCommit.Write>();
        boolean added = false;
        for (var write : writes) {
            if (!PAIR.contains(write.filename())) ordered.add(write);
            else if (!added) { ordered.addAll(order); added = true; }
        }
        var guarded = new ArrayList<SettingsCommit.Write>();
        for (var write : ordered) guarded.add(new SettingsCommit.Write(write.filename(), write.text(), () -> {
            for (var entry : files.entrySet()) entry.getValue().check(expected.get(entry.getKey()));
            write.check();
        }, () -> {
            write.save();
            if (PAIR.contains(write.filename())) {
                files.get(write.filename()).check(write.text().candidate());
                expected.put(write.filename(), write.text().candidate());
            }
        }, write::savedObservation, write::failureObservation));
        return SettingsCommit.prepare(guarded);
    }

    private static boolean safePrefixes(Map<String, String> before, List<SettingsCommit.Write> writes) {
        var prefix = new LinkedHashMap<>(before);
        for (var write : writes) {
            prefix.put(write.filename(), write.text().candidate());
            try { validate(prefix); } catch (IllegalArgumentException invalid) { return false; }
        }
        return true;
    }
    private static void validate(Map<String, String> values) {
        var shapes = ShapeConfiguration.parse(values.get(ShapeConfiguration.FILE));
        ProfileJson.generation(values.get(GenerationConfiguration.FILE)).validateAgainst(shapes);
    }
    private ProfileEditPlan() {}
}
