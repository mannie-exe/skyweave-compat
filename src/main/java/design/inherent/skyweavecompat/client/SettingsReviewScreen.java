package design.inherent.skyweavecompat.client;

import design.inherent.skyweavecompat.configuration.SettingId;
import design.inherent.skyweavecompat.configuration.SectionNavigation;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

/** Immutable review of the current draft, with no persistence or runtime authority of its own. */
final class SettingsReviewScreen extends Screen {
    record Change(SettingId id, String filename, Component label, String saved, String draft, Component requirements, Component effective) {}
    private final Screen parent;
    private final List<Change> changes;
    private final Component context;
    private final Consumer<SettingId> edit;
    private final Runnable save;
    private SettingsPanel panel;
    private SectionNavigation.Bookmark position;
    private String focus;

    SettingsReviewScreen(Screen parent, List<Change> changes, Component context, Consumer<SettingId> edit, Runnable save) {
        super(ConfigurationOverview.text("review")); this.parent = parent; this.changes = List.copyOf(changes);
        this.context = context; this.edit = edit; this.save = save;
    }
    @Override protected void init() {
        int top = 12 + font.lineHeight + 6 + font.split(context, width - 24).size() * font.lineHeight + 6;
        var help = ConfigurationOverview.text("review_help");
        top += font.split(help, width - 24).size() * font.lineHeight + 8;
        panel = new SettingsPanel(minecraft, 10, top, width - 20, Math.max(20,height - top - 38), false);
        String document = "";
        for (var change : changes) {
            if (!document.equals(change.id().document())) { panel.section(change.id().document(), Component.literal(change.filename())); document = change.id().document(); }
            var values = ConfigurationOverview.text("review_values", preview(change.saved()), preview(change.draft()))
                    .append("\n").append(change.requirements()).append("\n").append(change.effective());
            var button = Button.builder(ConfigurationOverview.text("edit"), ignored -> edit.accept(change.id()))
                    .bounds(0,0,Math.max(50,font.width(ConfigurationOverview.text("edit"))+12),20)
                    .createNarration(supplier -> change.label().copy().append(": ").append(supplier.get())).build();
            panel.anchorRow(change.id().anchor(),change.label(),values,button);
        }
        panel.finish(0); panel.restore(position); addRenderableWidget(panel); panel.restoreFocus(focus);
        addRenderableWidget(Button.builder(CommonComponents.GUI_BACK, ignored -> onClose()).bounds(10,height-28,90,20).build());
        addRenderableWidget(Button.builder(ConfigurationOverview.text("save"), ignored -> save.run()).bounds(width-130,height-28,120,20).build());
    }
    private Component preview(String value) {
        return value.length() <= 512 ? Component.literal(value)
                : ConfigurationOverview.text("review_truncated", value.substring(0,512), value.length());
    }
    @Override public void resize(Minecraft client, int width, int height) {
        if (panel != null) { position = panel.bookmark(); focus = panel.focusKey(); }
        super.resize(client,width,height);
    }
    @Override public void onClose() { minecraft.setScreen(parent); }
    @Override public void render(GuiGraphics graphics, int mx, int my, float tick) {
        renderBackground(graphics,mx,my,tick); super.render(graphics,mx,my,tick);
        graphics.drawString(font,title,12,10,ChatFormatting.WHITE.getColor());
        int y = 12 + font.lineHeight + 6;
        graphics.drawWordWrap(font,context,12,y,width-24,ChatFormatting.GRAY.getColor());
        y += font.split(context,width-24).size()*font.lineHeight+6;
        graphics.drawWordWrap(font,ConfigurationOverview.text("review_help"),12,y,width-24,ChatFormatting.GRAY.getColor());
    }
}
