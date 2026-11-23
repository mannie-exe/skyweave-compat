package design.inherent.skyweavecompat.clientprobe;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.ContainerEventHandler;
import net.minecraft.network.chat.Component;

/** Read-only witness of the library's measured rows; never computes an alternative layout. */
final class ClientLayoutProof {
    static JsonObject inspect(ContainerEventHandler root) {
        var panels = new JsonArray();
        collect(root, panels);
        var result = new JsonObject();
        result.add("panels", panels);
        var controls = root.children().stream().filter(AbstractWidget.class::isInstance)
                .map(AbstractWidget.class::cast).filter(widget -> widget.visible && !(widget instanceof ContainerEventHandler)).toList();
        var outside = new JsonArray();
        var overlap = new JsonArray();
        for (int i = 0; i < controls.size(); i++) {
            var a = controls.get(i);
            if (root instanceof net.minecraft.client.gui.screens.Screen screen
                    && (a.getX() < 0 || a.getY() < 0 || a.getRight() > screen.width || a.getBottom() > screen.height))
                outside.add(a.getMessage().getString());
            for (int j = i + 1; j < controls.size(); j++) {
                var b = controls.get(j);
                if (intersects(a.getX(), a.getY(), a.getWidth(), a.getHeight(), b.getX(), b.getY(), b.getWidth(), b.getHeight()))
                    overlap.add(a.getMessage().getString() + " / " + b.getMessage().getString());
            }
        }
        result.addProperty("screenControlCount", controls.size());
        result.add("screenControlIntersections", overlap);
        result.add("screenControlsOutsideBounds", outside);
        return result;
    }

    private static void collect(ContainerEventHandler parent, JsonArray panels) {
        for (var child : parent.children()) {
            if (!(child instanceof ContainerEventHandler container)) continue;
            if (child.getClass().getName().equals("design.inherent.reform.minecraft.client.FormPanel"))
                panels.add(panel(child));
            else collect(container, panels);
        }
    }

    private static JsonObject panel(Object panel) {
        var layouts = (List<?>) observed(panel, "layout");
        var positions = (List<?>) observed(panel, "positions");
        var outlines = (List<?>) observed(panel, "outlines");
        var result = new JsonObject();
        var rows = new JsonArray();
        var contentRows = new JsonArray();
        var intersections = new JsonArray();
        var splitSteps = new JsonArray();
        var focused = new JsonArray();
        var relationships = new java.util.LinkedHashMap<String, java.util.List<JsonObject>>();
        var font = Minecraft.getInstance().font;
        int fields = 0, stacked = 0;
        for (var layout : layouts) {
            var row = observed(layout, "row");
            boolean field = (boolean) observed(row, "field");
            if (field) fields++;
            boolean isStacked = (boolean) observed(layout, "stacked");
            if (field && isStacked) stacked++;
            int x = (int) observed(layout, "x"), y = (int) observed(layout, "y");
            int width = (int) observed(layout, "textWidth");
            int top = y + (int) observed(layout, "textY");
            var label = (Component) observed(row, "label");
            var help = (Component) observed(row, "summary");
            int bottom = top;
            if (!label.getString().isEmpty()) bottom += font.split(label, width).size() * font.lineHeight;
            if (!help.getString().isEmpty())
                bottom = top + (int) observed(layout, "summaryY") + font.split(help, width).size() * font.lineHeight;
            var controls = (List<?>) observed(row, "controls");
            if (field && controls.stream().filter(AbstractWidget.class::isInstance).map(AbstractWidget.class::cast).anyMatch(AbstractWidget::isFocused))
                focused.add((String) observed(row, "key"));
            var less = controls.stream().filter(AbstractWidget.class::isInstance).map(AbstractWidget.class::cast)
                    .filter(widget -> widget.getMessage().getString().equals("-")).findFirst();
            var more = controls.stream().filter(AbstractWidget.class::isInstance).map(AbstractWidget.class::cast)
                    .filter(widget -> widget.getMessage().getString().equals("+")).findFirst();
            if (less.isPresent() && more.isPresent()) {
                var minusAt = positions.stream().filter(at -> observed(at, "widget") == less.orElseThrow()).findFirst().orElseThrow();
                var plusAt = positions.stream().filter(at -> observed(at, "widget") == more.orElseThrow()).findFirst().orElseThrow();
                if (!observed(minusAt, "y").equals(observed(plusAt, "y"))) splitSteps.add(label.getString());
            }
            var evidence = new JsonObject();
            evidence.addProperty("key", (String) observed(row, "key"));
            evidence.addProperty("label", label.getString());
            evidence.addProperty("help", help.getString());
            evidence.addProperty("x", x); evidence.addProperty("y", y);
            evidence.addProperty("height", (int) observed(layout, "height"));
            evidence.addProperty("textWidth", width); evidence.addProperty("stacked", isStacked);
            evidence.addProperty("field", field);
            contentRows.add(evidence);
            if (field) rows.add(evidence);
            var relationship = (String) observed(row, "relationship");
            evidence.addProperty("relationship", relationship);
            if (field && relationship != null)
                relationships.computeIfAbsent(relationship, ignored -> new java.util.ArrayList<>()).add(evidence);
            for (var at : positions) {
                var widget = (AbstractWidget) observed(at, "widget");
                if (!controls.contains(widget)) continue;
                int ax = (int) observed(at, "x"), ay = (int) observed(at, "y");
                if (bottom > top && intersects(x, top, width, bottom - top, ax, ay, widget.getWidth(), widget.getHeight()))
                    intersections.add("Field text/control: " + label.getString());
            }
        }
        for (int i = 0; i < positions.size(); i++) {
            var first = positions.get(i);
            var a = (AbstractWidget) observed(first, "widget");
            for (int j = i + 1; j < positions.size(); j++) {
                var second = positions.get(j);
                var b = (AbstractWidget) observed(second, "widget");
                if (intersects((int) observed(first, "x"), (int) observed(first, "y"), a.getWidth(), a.getHeight(),
                        (int) observed(second, "x"), (int) observed(second, "y"), b.getWidth(), b.getHeight()))
                    intersections.add("Controls: " + a.getMessage().getString() + " / " + b.getMessage().getString());
            }
        }
        result.addProperty("fields", fields);
        result.addProperty("inlineFields", fields - stacked);
        result.addProperty("stackedFields", stacked);
        result.addProperty("groups", outlines.size());
        result.add("rows", rows);
        result.add("contentRows", contentRows);
        result.add("intersections", intersections);
        result.add("splitStepPairs", splitSteps);
        result.add("focusedFieldKeys", focused);
        var mixedRelationships = new JsonArray();
        relationships.forEach((id, members) -> {
            long xs = members.stream().map(row -> row.get("x").getAsInt()).distinct().count();
            long ys = members.stream().map(row -> row.get("y").getAsInt()).distinct().count();
            if (members.size() > 1 && ys != 1 && !(xs == 1 && ys == members.size())) mixedRelationships.add(id);
        });
        result.add("mixedRelatedRows", mixedRelationships);
        return result;
    }

    private static boolean intersects(int ax, int ay, int aw, int ah, int bx, int by, int bw, int bh) {
        return ax < bx + bw && bx < ax + aw && ay < by + bh && by < ay + ah;
    }

    private static Object observed(Object instance, String name) {
        try {
            var field = instance.getClass().getDeclaredField(name);
            field.setAccessible(true);
            return field.get(instance);
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Measured native layout is unavailable: " + name, failure);
        }
    }

    private ClientLayoutProof() {}
}
