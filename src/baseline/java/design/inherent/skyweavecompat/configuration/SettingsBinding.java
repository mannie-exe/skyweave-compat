package design.inherent.skyweavecompat.configuration;

import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** A form's view of an existing editor; grants no persistence or runtime authority. */
public record SettingsBinding(SettingId id, SettingDefinition definition, Supplier<String> read, Consumer<String> edit,
        Supplier<List<SettingIssue>> issues, BooleanSupplier modified, Supplier<SettingIssue> localIssue,
        Supplier<List<String>> readElements, java.util.function.Function<String, SettingIssue> elementValidation) {
    public SettingsBinding {
        java.util.Objects.requireNonNull(id); java.util.Objects.requireNonNull(definition);
        if (!id.field().equals(definition.key())) throw new IllegalArgumentException("Binding field identity differs from its schema");
    }
    public SettingIssue elementIssue(String text) { return elementValidation.apply(text); }
    public void reset() { edit.accept(definition.defaultText()); }
    public SettingIssue issue() {
        return localIssue.get();
    }
}
