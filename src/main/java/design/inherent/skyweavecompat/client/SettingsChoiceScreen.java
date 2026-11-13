package design.inherent.skyweavecompat.client;

import java.util.List;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

/** A local draft chooser over native text input and list navigation. Selecting never saves a file. */
final class SettingsChoiceScreen extends Screen {
    record Choice(String key, Component label) {}
    private final Screen parent;
    private final List<Choice> choices;
    private final Consumer<String> selected;
    private EditBox search;
    private Choices list;
    private Button done, cancel;
    private String query = "", selectedKey, focus = "search";

    SettingsChoiceScreen(Screen parent, Component title, List<Choice> choices, String current, Consumer<String> selected) {
        super(title); this.parent = parent; this.choices = List.copyOf(choices); this.selectedKey = current; this.selected = selected;
        if (choices.stream().map(Choice::key).distinct().count() != choices.size()) throw new IllegalArgumentException("Duplicate choice identity");
    }
    @Override protected void init() {
        int searchY = 12 + font.lineHeight + 8;
        if (search == null) search = new EditBox(font, 16, searchY, width - 32, 20, ConfigurationOverview.text("search_choices"));
        search.setResponder(null); search.setPosition(16, searchY); SettingsControls.resize(search, width - 32);
        search.setMaxLength(256); if (!search.getValue().equals(query)) search.setValue(query); search.setHint(ConfigurationOverview.text("search_choices"));
        search.setResponder(value -> { if (!query.equals(value)) { query = value; refresh(); } }); addRenderableWidget(search);
        list = addRenderableWidget(new Choices(searchY + 26));
        done = addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> choose())
                .bounds(width / 2 - 124, height - 28, 120, 20).build());
        cancel = addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, button -> onClose())
                .bounds(width / 2 + 4, height - 28, 120, 20).build());
        refresh();
        setInitialFocus(switch (focus) { case "list" -> list; case "done" -> done; case "cancel" -> cancel; default -> search; });
    }
    private void refresh() {
        if (list == null || done == null) return;
        list.filter(); done.active = list.getSelected() != null;
    }
    private void choose() {
        var entry = list.getSelected();
        if (entry == null) return;
        if (choices.stream().noneMatch(choice -> choice.key().equals(entry.choice.key()))) throw new IllegalStateException("Detached choice");
        selected.accept(entry.choice.key()); minecraft.setScreen(parent);
    }
    @Override public void onClose() { minecraft.setScreen(parent); }
    @Override public void setFocused(GuiEventListener listener) {
        if (listener != null) {
            if (listener == search) focus = "search";
            else if (listener == list) focus = "list";
            else if (listener == done) focus = "done";
            else if (listener == cancel) focus = "cancel";
        }
        super.setFocused(listener);
    }
    @Override public boolean keyPressed(int key, int scanCode, int modifiers) {
        if ((key == 257 || key == 335 || key == 32 && getFocused() == list)
                && (getFocused() == search || getFocused() == list) && list.getSelected() != null) { choose(); return true; }
        return super.keyPressed(key, scanCode, modifiers);
    }
    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        graphics.drawScrollingString(font, title, 16, width - 16, 12, ChatFormatting.WHITE.getColor());
        super.render(graphics, mouseX, mouseY, partialTick);
        if (list.children().isEmpty()) graphics.drawCenteredString(font, ConfigurationOverview.text("no_choices"), width / 2,
                list.getY() + 12, ChatFormatting.GRAY.getColor());
    }
    private final class Choices extends ObjectSelectionList<Choices.Entry> {
        Choices(int top) { super(SettingsChoiceScreen.this.minecraft, SettingsChoiceScreen.this.width - 32,
                Math.max(20, SettingsChoiceScreen.this.height - top - 38), top, SettingsChoiceScreen.this.font.lineHeight + 12); setX(16); }
        @Override public int getRowWidth() { return getWidth() - 16; }
        void filter() {
            clearEntries();
            for (var choice : choices) if (design.inherent.skyweavecompat.configuration.SettingsSearch.matches(query, choice.label().getString(), choice.key()))
                addEntry(new Entry(choice));
            var current = children().stream().filter(entry -> entry.choice.key().equals(selectedKey)).findFirst().orElse(null);
            setSelected(current != null ? current : children().isEmpty() ? null : children().getFirst());
            if (getSelected() != null) ensureVisible(getSelected());
        }
        @Override public void setSelected(Entry entry) {
            super.setSelected(entry);
            if (entry != null) selectedKey = entry.choice.key();
            if (done != null) done.active = entry != null;
        }
        private final class Entry extends ObjectSelectionList.Entry<Entry> {
            final Choice choice;
            Entry(Choice choice) { this.choice = choice; }
            @Override public Component getNarration() { return title.copy().append(": ").append(choice.label()); }
            @Override public void render(GuiGraphics graphics, int index, int top, int left, int width, int height,
                    int mouseX, int mouseY, boolean hovered, float partialTick) {
                graphics.drawScrollingString(font, choice.label(), left + 6, left + width - 6, top + 6,
                        (getSelected() == this ? ChatFormatting.YELLOW : ChatFormatting.WHITE).getColor());
            }
            @Override public boolean mouseClicked(double x, double y, int button) {
                if (button != 0) return false;
                setSelected(this); choose(); return true;
            }
        }
    }
}
