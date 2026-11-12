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

/** Native editors retained by field identity; layout rebuilds do not replace their text state. */
final class SettingsControls {
    private final Font font;
    private final Map<String, EditBox> inputs = new HashMap<>();
    SettingsControls(Font font) { this.font = font; }

    EditBox text(String id, Component label, Supplier<String> read, Consumer<String> edit,
            int maximumLength, Supplier<SettingIssue> issue, Runnable changed) {
        var input = inputs.computeIfAbsent(id, ignored -> new EditBox(font, 0, 0, 80, 20, label));
        String raw = read.get();
        input.setResponder(null);
        input.setWidth(80);
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
        var steppers = new ArrayList<Button>();
        var input = text(id, label, binding.read(), binding.edit(), definition.constraints().maximumLength(), binding::issue,
                () -> { updateSteps(steppers, definition, binding.read().get()); changed.run(); });
        var controls = new ArrayList<AbstractWidget>();
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
            updateSteps(steppers, definition, binding.read().get()); changed.run();
        }).bounds(0, 0, Math.max(42, font.width(ConfigurationOverview.text("reset")) + 12), 20)
                .createNarration(supplier -> label.copy().append(": ").append(supplier.get())).build();
        reset.setTooltip(Tooltip.create(ConfigurationOverview.text("default", definition.defaultText())));
        controls.add(reset);
        return List.copyOf(controls);
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
