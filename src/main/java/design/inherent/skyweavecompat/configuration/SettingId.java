package design.inherent.skyweavecompat.configuration;

import java.util.Objects;

/** Authored document identity and schema key, independent of files, labels, and page routes. */
public record SettingId(String document, String field) {
    public SettingId {
        validateDocument(document); Objects.requireNonNull(field);
        if (field.isBlank())
            throw new IllegalArgumentException("Expected a document ID and field key");
    }
    static void validateDocument(String document) {
        if (!Objects.requireNonNull(document).matches("[a-z0-9_.-]+:[a-z0-9_./-]+"))
            throw new IllegalArgumentException("Expected a namespaced document ID");
    }
    public String anchor() { return "field/" + document + "/" + field; }
}
