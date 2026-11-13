package design.inherent.skyweavecompat.client;

import design.inherent.skyweavecompat.configuration.SettingSlider;
import design.inherent.skyweavecompat.configuration.SettingsBinding;
import java.util.function.Consumer;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/** Native pointer/focus behavior, with schema-sized keyboard steps and one shared raw draft. */
final class ProbabilitySlider extends AbstractSliderButton {
    private SettingsBinding binding;
    private SettingSlider scale;
    private Component label;
    private Consumer<String> edit;
    private int keyboardDirection;

    ProbabilitySlider(Component label) { super(0, 0, 90, 20, label, 0); }
    void bind(Component label, SettingsBinding binding, Consumer<String> edit) {
        this.label = label; this.binding = binding; this.scale = new SettingSlider(binding.definition()); this.edit = edit;
        refresh();
    }
    void refresh() {
        try { value = scale.fraction(binding.read().get()); active = true; }
        catch (IllegalArgumentException invalid) { active = false; }
        updateMessage();
    }
    @Override protected void updateMessage() {
        if (binding == null) return;
        try {
            double probability = ((Number) binding.definition().parse(binding.read().get())).doubleValue();
            setMessage(ConfigurationOverview.text("probability_value", Math.round(probability * 100)));
        } catch (IllegalArgumentException invalid) { setMessage(Component.literal(binding.read().get())); }
    }
    @Override protected MutableComponent createNarrationMessage() {
        return label.copy().append(": ").append(binding.read().get()).append(". ")
                .append(ConfigurationOverview.text("bounds", binding.definition().minimum() + " .. " + binding.definition().maximum()));
    }
    @Override public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (!active) return false;
        keyboardDirection = key == 263 ? -1 : key == 262 ? 1 : 0;
        try { return super.keyPressed(key, scanCode, modifiers); }
        finally { keyboardDirection = 0; }
    }
    @Override protected void applyValue() {
        if (!active) { refresh(); return; }
        edit.accept(keyboardDirection == 0 ? scale.text(value) : binding.definition().adjust(binding.read().get(), keyboardDirection));
        refresh();
    }
}
