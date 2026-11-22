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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** The declared generation/shape dependency, not a general file transaction or runtime mutation. */
public final class ProfileEditPlan {
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
            var issue = SettingIssue.find(invalid);
            if (issue == null) return profileIssue(subject, "profile.pair", invalid.getMessage());
            var generationIssue = new Issue("profile.pair", Issue.Severity.ERROR, ReformDocuments.path(issue.field()),
                    issue.related().stream().map(ReformDocuments::path).toList(), Map.of(), issue.message());
            var shapeIssue = new Issue("profile.pair", Issue.Severity.ERROR, ReformDocuments.path("outline.maximumAspectRatio"),
                    List.of(ReformDocuments.path("enabled"), ReformDocuments.path("outline.aspectChance"),
                            ReformDocuments.path("fringe.enabled"), ReformDocuments.path("fringe.falloffDistance"),
                            ReformDocuments.path("fringe.disintegratingScale")), Map.of(), issue.message());
            return new DocumentBatch.Check(Map.of(GENERATION.id(), new ValidationReport(List.of(generationIssue), true),
                    SHAPES.id(), new ValidationReport(List.of(shapeIssue), true)), true);
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

    private ProfileEditPlan() {}
}
