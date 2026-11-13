package design.inherent.skyweavecompat.client;

import design.inherent.skyweavecompat.configuration.ConfigFile;
import design.inherent.skyweavecompat.configuration.SettingDefinition;
import design.inherent.skyweavecompat.configuration.SettingIssue;
import design.inherent.skyweavecompat.configuration.SettingsBinding;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import design.inherent.skyweavecompat.configuration.SettingId;
import design.inherent.skyweavecompat.configuration.TextCollection;

/** Native editors retained by field identity; layout rebuilds do not replace their text state. */
final class SettingsControls {
    private final Font font;
    private final Map<String, EditBox> inputs = new HashMap<>();
    private final Map<SettingId, TextCollection> collections = new HashMap<>();
    private final Map<SettingId, ProbabilitySlider> sliders = new HashMap<>();
    private final Map<SettingId, SettingsBinding> fields = new HashMap<>();
    private final Map<String, List<SettingIssue>> documentIssues = new HashMap<>();
    void feedback(String document, List<SettingIssue> issues) {
        documentIssues.put(document, List.copyOf(issues)); refreshIssues(document);
    }
    boolean hasIssue(SettingId id) {
        return documentIssues.getOrDefault(id.document(), List.of()).stream().anyMatch(issue -> issue.affects(id.field()));
    }
    boolean documentIssue(String document) {
        return documentIssues.getOrDefault(document, List.of()).stream().anyMatch(issue -> issue.field().isEmpty());
    }
    private void edited(String document) { documentIssues.remove(document); refreshIssues(document); }
    private void refreshIssues(String document) {
        fields.forEach((id, binding) -> {
            if (id.document().equals(document) && inputs.get(id.anchor()) != null)
                decorate(inputs.get(id.anchor()), fieldIssue(binding));
        });
    }
    private SettingIssue fieldIssue(SettingsBinding binding) {
        var local = binding.issue();
        return local != null ? local : documentIssues.getOrDefault(binding.id().document(), List.of()).stream()
                .filter(issue -> issue.affects(binding.id().field())).findFirst().orElse(null);
    }
    AbstractWidget input(SettingId id) {
        var collection = collections.get(id);
        if (collection != null) {
            if (collection.error() != null) return inputs.get(id.anchor() + "/raw");
            for (var row : collection.rows()) if (collection.issue(row.id()) != null)
                return inputs.get(collection.anchor(row.id()));
        }
        return inputs.get(id.anchor());
    }
    SettingsControls(Font font) { this.font = font; }

    EditBox text(String id, Component label, Supplier<String> read, Consumer<String> edit,
            int maximumLength, Supplier<SettingIssue> issue, Runnable changed) {
        return text(id, label, read, edit, maximumLength, issue, changed, 80);
    }
    private EditBox text(String id, Component label, Supplier<String> read, Consumer<String> edit,
            int maximumLength, Supplier<SettingIssue> issue, Runnable changed, int width) {
        var input = inputs.computeIfAbsent(id, ignored -> new EditBox(font, 0, 0, width, 20, label));
        String raw = read.get();
        input.setResponder(null);
        resize(input, width);
        input.setMaxLength(Math.max(raw.length(), maximumLength < 0 ? ConfigFile.LIMIT : maximumLength));
        if (!raw.equals(input.getValue())) input.setValue(raw);
        input.setMessage(label);
        input.setResponder(value -> {
            // EditBox also calls its responder when only the selection/caret changes.
            if (read.get().equals(value)) return;
            edit.accept(value); decorate(input, issue.get()); changed.run();
        });
        decorate(input, issue.get());
        return input;
    }
    List<AbstractWidget> field(Component label, SettingsBinding binding, Runnable changed) {
        String id = binding.id().anchor();
        var definition = binding.definition();
        fields.put(binding.id(), binding);
        var steppers = new ArrayList<Button>();
        ProbabilitySlider slider = design.inherent.skyweavecompat.configuration.SettingSlider.supports(definition)
                ? sliders.computeIfAbsent(binding.id(), ignored -> new ProbabilitySlider(label)) : null;
        var input = text(id, label, binding.read(), binding.edit(), definition.constraints().maximumLength(), () -> fieldIssue(binding),
                () -> { edited(binding.id().document()); updateSteps(steppers, definition, binding.read().get()); if (slider != null) slider.refresh(); changed.run(); });
        var controls = new ArrayList<AbstractWidget>();
        if (slider != null) {
            slider.bind(label, binding, input::setValue);
            slider.setTooltip(Tooltip.create(label.copy().append(": ").append(ConfigurationOverview.text("unit." + definition.unit()))));
            controls.add(slider);
        }
        controls.add(input);
        if (definition.kind() == SettingDefinition.Kind.INTEGER || definition.kind() == SettingDefinition.Kind.LONG
                || definition.kind() == SettingDefinition.Kind.DECIMAL) {
            for (int direction : new int[] {-1, 1}) {
                var button = Button.builder(Component.literal(direction < 0 ? "-" : "+"), ignored -> {
                    try { input.setValue(definition.adjust(binding.read().get(), direction)); }
                    catch (IllegalArgumentException invalid) { decorate(input, binding.issue()); }
                }).bounds(0, 0, 18, 20).createNarration(supplier -> label.copy().append(": ")
                        .append(ConfigurationOverview.text(direction < 0 ? "decrease" : "increase"))).build();
                button.setTooltip(Tooltip.create(ConfigurationOverview.text(direction < 0 ? "decrease" : "increase")
                        .append(" ").append(definition.step().toPlainString())
                        .append(definition.unit().isEmpty() ? Component.empty() : Component.literal(" ").append(ConfigurationOverview.text("unit." + definition.unit())))));
                steppers.add(button); controls.add(button);
            }
            updateSteps(steppers, definition, binding.read().get());
        }
        var reset = Button.builder(ConfigurationOverview.text("reset"), ignored -> {
            binding.reset(); input.setValue(binding.read().get()); decorate(input, binding.issue());
            updateSteps(steppers, definition, binding.read().get()); if (slider != null) slider.refresh(); changed.run();
        }).bounds(0, 0, Math.max(42, font.width(ConfigurationOverview.text("reset")) + 12), 20)
                .createNarration(supplier -> label.copy().append(": ").append(supplier.get())).build();
        reset.setTooltip(Tooltip.create(ConfigurationOverview.text("default", definition.defaultText())));
        controls.add(reset);
        return List.copyOf(controls);
    }
    record CollectionRow(String id, List<AbstractWidget> widgets) {}
    record CollectionControls(List<CollectionRow> rows, List<AbstractWidget> actions) {}
    CollectionControls collection(Component label, SettingsBinding binding, int width,
            Consumer<String> rebuildFocus, Runnable changed) {
        var data = collections.computeIfAbsent(binding.id(), ignored -> new TextCollection(binding));
        data.bind(binding);
        String anchor = binding.id().anchor();
        var retained = data.rows().stream().map(row -> data.anchor(row.id())).collect(java.util.stream.Collectors.toSet());
        inputs.keySet().removeIf(id -> id.startsWith(anchor + "/row/") && !retained.contains(id));
        int removeWidth = Math.max(55, font.width(ConfigurationOverview.text("remove")) + 12);
        int inputWidth = Math.max(40, width - removeWidth - (data.ordered() ? 36 : 0) - 18);
        var rows = new ArrayList<CollectionRow>();
        if (data.error() != null) {
            var input = text(anchor + "/raw", label, binding.read(), binding.edit(), -1, binding::issue, changed, Math.max(40, width));
            rows.add(new CollectionRow(anchor + "/raw", List.of(input)));
        } else {
            int ordinal = 0;
            for (var row : data.rows()) {
                var rowLabel = ConfigurationOverview.text("collection_entry", label, ++ordinal);
                var input = text(data.anchor(row.id()), rowLabel, () -> data.value(row.id()), value -> {
                    data.set(row.id(), value);
                    // Duplicate validation must refresh the other implicated rows as well.
                    for (var current : data.rows()) {
                        var widget = inputs.get(data.anchor(current.id()));
                        if (widget != null) decorate(widget, data.issue(current.id()));
                    }
                }, binding.definition().constraints().maximumLength(), () -> data.issue(row.id()), changed, inputWidth);
                var widgets = new ArrayList<AbstractWidget>(); widgets.add(input);
                if (data.ordered()) for (int direction : new int[] {-1, 1}) {
                    var title = ConfigurationOverview.text(direction < 0 ? "move_up" : "move_down");
                    var move = Button.builder(Component.literal(direction < 0 ? "↑" : "↓"), ignored -> {
                        data.move(row.id(), direction); rebuildFocus.accept(data.anchor(row.id()) + "/0");
                    }).bounds(0, 0, 18, 20).createNarration(supplier -> rowLabel.copy().append(": ").append(title)).build();
                    move.setTooltip(Tooltip.create(title)); move.active = data.canMove(row.id(), direction); widgets.add(move);
                }
                widgets.add(Button.builder(ConfigurationOverview.text("remove"), ignored -> {
                    Long next = data.remove(row.id());
                    rebuildFocus.accept(next == null ? anchor + "/actions/0" : data.anchor(next) + "/0");
                }).bounds(0, 0, removeWidth, 20).createNarration(supplier -> rowLabel.copy().append(": ")
                        .append(ConfigurationOverview.text("remove"))).build());
                rows.add(new CollectionRow(data.anchor(row.id()), List.copyOf(widgets)));
            }
        }
        var add = Button.builder(ConfigurationOverview.text("add"), ignored -> {
            long row = data.add(); rebuildFocus.accept(data.anchor(row) + "/0");
        }).bounds(0, 0, Math.max(65, font.width(ConfigurationOverview.text("add")) + 12), 20)
                .createNarration(supplier -> label.copy().append(": ").append(supplier.get())).build();
        add.active = data.canAdd();
        var reset = Button.builder(ConfigurationOverview.text("reset"), ignored -> {
            binding.reset(); rebuildFocus.accept(anchor + "/actions/1");
        }).bounds(0, 0, Math.max(42, font.width(ConfigurationOverview.text("reset")) + 12), 20)
                .createNarration(supplier -> label.copy().append(": ").append(supplier.get())).build();
        reset.setTooltip(Tooltip.create(ConfigurationOverview.text("default", binding.definition().defaultText())));
        return new CollectionControls(List.copyOf(rows), List.of(add, reset));
    }
    /** Reflow native horizontal text without changing the caret, selection, or draft. */
    static void resize(AbstractWidget widget, int width) {
        if (widget.getWidth() == width) return;
        widget.setWidth(width);
        if (widget instanceof EditBox input) {
            int cursor = input.getCursorPosition();
            input.setCursorPosition(0); input.setCursorPosition(cursor);
        }
    }
    private static void updateSteps(List<Button> buttons, SettingDefinition definition, String value) {
        for (int i = 0; i < buttons.size(); i++) {
            try { buttons.get(i).active = !definition.adjust(value, i == 0 ? -1 : 1).equals(value); }
            catch (IllegalArgumentException invalid) { buttons.get(i).active = false; }
        }
    }
    private static void decorate(EditBox input, SettingIssue issue) {
        input.setTextColor(issue == null ? SettingsPanel.TITLE_TEXT : ChatFormatting.RED.getColor());
        input.setTooltip(issue == null ? null : Tooltip.create(Component.literal(issue.message())));
    }
}
