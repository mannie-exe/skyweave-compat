package design.inherent.skyweavecompat.client;

import design.inherent.skyweavecompat.presentation.FooterLayout;
import java.util.ArrayList;
import java.util.TreeMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.LogoRenderer;
import net.minecraft.client.gui.components.PlainTextButton;
import net.minecraft.client.gui.screens.TitleScreen;
import net.neoforged.neoforge.internal.BrandingControl;

/** Reserve native footer space once on title initialization/resize; leave copyright in place. */
public final class MainMenuLayout {
    private MainMenuLayout() {}

    public static void reserveFooter(TitleScreen screen) {
        int[] count = {0};
        BrandingControl.forEachLine(true, false, (index, text) -> count[0]++);
        if (count[0] == 0) return;
        int footerTop = screen.height - 10 - (count[0] - 1) * (Minecraft.getInstance().font.lineHeight + 1);
        // The native edition graphic ends seven pixels below the 44-pixel logo.
        int logoBottom = LogoRenderer.DEFAULT_HEIGHT_OFFSET + LogoRenderer.LOGO_HEIGHT + 7;
        var rows = new TreeMap<Integer, ArrayList<AbstractWidget>>();
        for (var child : screen.children()) {
            if (child instanceof AbstractWidget widget && widget.visible && widget.getWidth() > 0
                    && widget.getHeight() > 0 && widget.getY() >= logoBottom + 2 && !(widget instanceof PlainTextButton))
                rows.computeIfAbsent(widget.getY(), ignored -> new ArrayList<>()).add(widget);
        }
        var groups = new ArrayList<>(rows.values());
        var geometry = groups.stream().map(widgets -> new FooterLayout.Row(widgets.getFirst().getY(),
                widgets.stream().mapToInt(AbstractWidget::getHeight).max().orElseThrow())).toList();
        FooterLayout.reserve(geometry, logoBottom + 2, footerTop - 2).ifPresent(positions -> {
            for (int i = 0; i < groups.size(); i++) {
                int y = positions.get(i);
                for (var widget : groups.get(i)) widget.setY(y);
            }
        });
    }
}
