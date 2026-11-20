package design.inherent.skyweavecompat.questlog.client;

import design.inherent.skyweavecompat.client.ClientResourceRevision;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.infernalstudios.questlog.Questlog;
import org.infernalstudios.questlog.client.gui.QuestlogGuiSet;
import org.infernalstudios.questlog.client.gui.components.ScrollableComponent;
import org.infernalstudios.questlog.client.gui.components.scrollable.Scrollable;
import org.lwjgl.glfw.GLFW;

/** Journal-local presentation only; the parent screen and its state survive this visit. */
public final class JournalSettingsScreen extends Screen {
    private final Screen parent;
    private final QuestlogGuiSet source;
    private QuestlogGuiSet skin;
    private JournalScrollPane pane;
    private int x, y, panelWidth, panelHeight;
    private Object language;
    private long resources;
    private double scroll;
    public JournalSettingsScreen(Screen parent, QuestlogGuiSet source) {
        super(JournalRows.text("settings")); this.parent = parent; this.source = source;
    }
    @Override protected void init() {
        language = net.minecraft.locale.Language.getInstance(); resources = ClientResourceRevision.revision();
        panelWidth = Math.max(160, Math.min(370, width - 24));
        panelHeight = Math.max(70, Math.min(330, height - 72));
        x = (width - panelWidth) / 2; y = Math.max(22, (height - panelHeight) / 2);
        skin = new QuestlogGuiSet(source.backgroundLoc, source.rightPanelLoc, source.peripheralLoc, panelWidth, panelWidth, panelHeight);
        pane = new JournalScrollPane(x + 18, y + 44, panelWidth - 36, panelHeight - 60, new SettingsContent(), skin, title);
        pane.setScrollAmount(scroll); addWidget(pane);
        var bookmark = new JournalButton(x + panelWidth - 88, y - 17, 88, title, skin, () -> {});
        bookmark.selected(true); bookmark.active = false; addRenderableWidget(bookmark);
        addRenderableWidget(new JournalButton(x, y + panelHeight + 3, panelWidth,
                Component.translatable("gui.back"), skin, this::onClose));
    }
    @Override public void resize(Minecraft client, int width, int height) { if (pane != null) scroll = pane.getScrollAmount(); super.resize(client, width, height); }
    @Override public void tick() {
        if (language != net.minecraft.locale.Language.getInstance() || resources != ClientResourceRevision.revision()) {
            scroll = pane.getScrollAmount(); rebuildWidgets();
        }
    }
    @Override public void onClose() { minecraft.setScreen(parent); }
    @Override public void render(GuiGraphics graphics, int mx, int my, float tick) {
        super.render(graphics, mx, my, tick); skin.detailBackgroundLeft.blit(graphics, x, y);
        graphics.drawString(font, title, x + 18, y + 17, Questlog.getConfig().colors.titleColor, false);
        pane.render(graphics, mx, my, tick);
    }
    private final class SettingsContent implements Scrollable, GuiEventListener, NarratableEntry {
        private ScrollableComponent owner;
        private final List<Row> rows = new ArrayList<>();
        private int extent, available = -1, selected;
        private boolean focused;
        private final class Row {
            final String key, group;
            final java.util.function.Supplier<java.util.concurrent.CompletionStage<Void>> action;
            int top, height, buttonY;
            List<net.minecraft.util.FormattedCharSequence> label, description;
            JournalButton button;
            Row(String key, String group, java.util.function.Supplier<java.util.concurrent.CompletionStage<Void>> action) { this.key = key; this.group = group; this.action = action; }
        }
        SettingsContent() {
            rows.add(new Row("images", "display", JournalPreferences::toggleImages));
            rows.add(new Row("symbols", null, JournalPreferences::toggleSymbols));
            rows.add(new Row("notifications", "notifications", JournalPreferences::cycleNotifications));
        }
        @Override public void setScrollableComponent(ScrollableComponent owner) { this.owner = owner; }
        private Component value(int index) {
            return index == 2 ? JournalPreferences.NOTIFICATIONS.get().getTranslatedName()
                    : Component.translatable((index == 0 ? JournalPreferences.ITEM_IMAGES.get() : JournalPreferences.CONDITION_SYMBOLS.get()) ? "options.on" : "options.off");
        }
        private void measure() {
            if (owner == null || available == owner.width - owner.getScrollbarWidth()) return;
            available = owner.width - owner.getScrollbarWidth(); int top = 0;
            for (int i = 0; i < rows.size(); i++) {
                Row row = rows.get(i); int index = i;
                row.top = top;
                row.label = font.split(JournalRows.text(row.key), available);
                row.description = font.split(JournalRows.text(row.key + "_description"), available);
                row.button = new JournalButton(0, 0, available, value(i), skin, () -> {
                    if (JournalPreferences.busy()) return;
                    try { row.action.get().whenCompleteAsync((ignored, failure) -> {
                        if (minecraft.screen != JournalSettingsScreen.this) {
                            if (failure != null) net.minecraft.client.gui.components.toasts.SystemToast.add(minecraft.getToasts(),
                                    net.minecraft.client.gui.components.toasts.SystemToast.SystemToastId.PERIODIC_NOTIFICATION,
                                    Component.translatable("skyweave_compat.settings.change_failed"),
                                    Component.literal(String.valueOf(failure.getMessage())));
                            return;
                        }
                        if (failure == null) { available = -1; measure(); ensureVisible(index); }
                        else failed(failure);
                    }, minecraft); }
                    catch (RuntimeException failure) { failed(failure); }
                });
                int group = row.group == null ? 0 : font.lineHeight + 10;
                row.buttonY = group + row.label.size() * font.lineHeight + 4;
                row.height = row.buttonY + row.button.getHeight() + 5 + row.description.size() * font.lineHeight + 16;
                top += row.height;
            }
            extent = top;
        }
        @Override public int getHeight() { measure(); return extent; }
        private void ensureVisible(int index) {
            Row row = rows.get(index);
            if (row.top < owner.getScrollAmount()) owner.setScrollAmount(row.top);
            else if (row.top + row.height > owner.getScrollAmount() + owner.height)
                owner.setScrollAmount(row.top + row.height - owner.height);
        }
        @Override public void render(GuiGraphics graphics, int mx, int my, float tick) {
            measure(); int x = (int)owner.getXOffset(), y = (int)owner.getYOffset();
            for (int i = 0; i < rows.size(); i++) {
                Row row = rows.get(i); int at = y + row.top;
                row.button.setPosition(x, at + row.buttonY);
                row.button.active = !JournalPreferences.busy();
                row.button.setFocused(focused && selected == i);
                if (row.top + row.height < owner.getScrollAmount() || row.top > owner.getScrollAmount() + owner.height) continue;
                if (row.group != null) {
                    graphics.drawString(font, JournalRows.text(row.group + "_group"), x, at, Questlog.getConfig().colors.titleColor, false);
                    at += font.lineHeight + 10;
                }
                for (var line : row.label) { graphics.drawString(font, line, x, at, Questlog.getConfig().colors.textColor, false); at += font.lineHeight; }
                row.button.render(graphics, x + mx, y + my, tick);
                at = y + row.top + row.buttonY + row.button.getHeight() + 5;
                for (var line : row.description) { graphics.drawString(font, line, x, at, Questlog.getConfig().colors.textColor, false); at += font.lineHeight; }
            }
        }
        private void failed(Throwable failure) {
            scroll = pane.getScrollAmount();
            minecraft.setScreen(new net.minecraft.client.gui.screens.AlertScreen(
                    () -> minecraft.setScreen(JournalSettingsScreen.this),
                    Component.translatable("skyweave_compat.settings.change_failed"),
                    Component.literal(String.valueOf(failure.getMessage()))));
        }
        @Override public boolean mouseClicked(double mx, double my, int button) {
            measure(); if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) return false;
            for (int i = 0; i < rows.size(); i++) {
                Row row = rows.get(i);
                if (mx >= 0 && mx < row.button.getWidth() && my >= row.top + row.buttonY && my < row.top + row.buttonY + row.button.getHeight()) {
                    selected = i; row.button.onPress(); return true;
                }
            }
            return false;
        }
        @Override public boolean keyPressed(int key, int scan, int modifiers) {
            if (!focused) return false;
            if (key == GLFW.GLFW_KEY_UP || key == GLFW.GLFW_KEY_DOWN) {
                selected = Math.clamp(selected + (key == GLFW.GLFW_KEY_UP ? -1 : 1), 0, rows.size() - 1); measure(); ensureVisible(selected); return true;
            }
            if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_SPACE) { rows.get(selected).button.onPress(); return true; }
            return false;
        }
        @Override public void setFocused(boolean value) { focused = value; }
        @Override public boolean isFocused() { return focused; }
        @Override public NarrationPriority narrationPriority() { return focused ? NarrationPriority.FOCUSED : NarrationPriority.NONE; }
        @Override public void updateNarration(NarrationElementOutput output) {
            Row row = rows.get(selected);
            output.add(NarratedElementType.TITLE, JournalRows.text(row.key).copy().append(": ").append(value(selected)).append(". ").append(JournalRows.text(row.key + "_description")));
        }
    }
}
