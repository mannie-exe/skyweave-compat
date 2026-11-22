package design.inherent.skyweavecompat.clientprobe;

import com.google.gson.JsonObject;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.anti_ad.mc.ipnext.config.Hotkeys;

/** Server-seeded items and actual native sorting packets, not a simulated inventory. */
final class ClientIpnInventoryProbe {
    private static final Map<String, Integer> EXPECTED = Map.of("minecraft:dirt", 13, "minecraft:cobblestone", 11);
    private final JsonObject result = new JsonObject();
    private int phase, waits;
    private java.util.List<Integer> originalKeys;
    private record ServerInventory(Map<String, Integer> items, long stacks, boolean carriedEmpty, int hotbarItems) {}
    private volatile ServerInventory serverInventory;
    private ClientIpnMouseGestures gestures;

    ClientIpnInventoryProbe(JsonObject report) { report.add("inventory", result); }

    boolean tick() throws ReflectiveOperationException {
        if (gestures != null) return gestures.tick();
        var mc = Minecraft.getInstance();
        if (++waits > 30) throw new IllegalStateException("Native inventory action did not settle: " + result);
        if (phase == 0) {
            var actor = mc.player.getUUID();
            mc.getSingleplayerServer().execute(() -> {
                var player = mc.getSingleplayerServer().getPlayerList().getPlayer(actor);
                player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
                player.getInventory().clearContent();
                player.getInventory().setItem(9, new ItemStack(Items.DIRT, 7));
                player.getInventory().setItem(10, new ItemStack(Items.COBBLESTONE, 11));
                player.getInventory().setItem(11, new ItemStack(Items.DIRT, 6));
                player.inventoryMenu.broadcastFullState();
            });
            phase = 1; return false;
        }
        if (phase == 1) {
            if (!items(mc.player.getInventory()).equals(EXPECTED)) return false;
            mc.gameMode.setLocalMode(net.minecraft.world.level.GameType.SURVIVAL);
            var inventory = new InventoryScreen(mc.player); mc.setScreen(inventory);
            EditBox search;
            if (net.neoforged.fml.ModList.get().isLoaded("emi")) {
                search = (EditBox)Class.forName("dev.emi.emi.screen.EmiScreenManager").getField("search").get(null);
            } else {
                var recipes = inventory.getRecipeBookComponent(); if (!recipes.isVisible()) recipes.toggleVisibility();
                var field = recipes.getClass().getDeclaredField("searchBox"); field.setAccessible(true); search = (EditBox)field.get(recipes);
            }
            search.setFocused(true);
            var slot = inventory.getMenu().getSlot(9);
            int x = inventory.getGuiLeft() + slot.x + 8, y = inventory.getGuiTop() + slot.y + 8;
            ClientIpnProbe.move(x, y);
            var graphics = new GuiGraphics(mc, mc.renderBuffers().bufferSource()); inventory.render(graphics, x, y, 0); graphics.flush();
            result.addProperty("slotRendered", inventory.getSlotUnderMouse() == slot);
            result.add("before", new com.google.gson.Gson().toJsonTree(items(mc.player.getInventory())));
            result.addProperty("beforeDirtStacks", dirtStacks(mc.player.getInventory()));
            var binding = Hotkeys.INSTANCE.getSORT_INVENTORY().getMainKeybind();
            originalKeys = java.util.List.copyOf(binding.getKeyCodes());
            binding.setKeyCodes(java.util.List.of(82, -98));
            int before = ClientIpnProbe.sorts; ClientIpnProbe.key(82, 1); ClientIpnProbe.mouseChord(); ClientIpnProbe.key(82, 0);
            result.addProperty("textLetterMouseSorts", ClientIpnProbe.sorts - before);
            binding.setKeyCodes(java.util.List.of(340, -98));
            before = ClientIpnProbe.sorts; ClientIpnProbe.key(340, 1); ClientIpnProbe.mouseChord(); ClientIpnProbe.key(340, 0);
            result.addProperty("modifierMouseSorts", ClientIpnProbe.sorts - before);
            binding.setKeyCodes(java.util.List.of(-98));
            before = ClientIpnProbe.sorts; ClientIpnProbe.mouseChord();
            result.addProperty("nativeSortCallbacks", ClientIpnProbe.sorts - before);
            result.addProperty("searchFocusRetained", search.isFocused());
            org.anti_ad.mc.ipnext.gui.inject.base.SortButtonWidget toolbar = null;
            for (Object value : org.anti_ad.mc.ipnext.gui.inject.ContainerScreenEventHandler.INSTANCE.getCurrentWidgets())
                if (value instanceof org.anti_ad.mc.ipnext.gui.inject.base.InsertableWidget widget && widget.getScreen() == inventory && widget.getVisible())
                    for (Object child : widget.getHintableList())
                        if (child instanceof org.anti_ad.mc.ipnext.gui.inject.base.SortButtonWidget button
                                && "inventoryprofiles.injected.ui.element.sort_button".equals(button.getId()) && button.getVisible() && button.getActive()
                                && button.getAbsoluteBounds().getWidth() > 0 && button.getAbsoluteBounds().getHeight() > 0) toolbar = button;
            result.addProperty("toolbarSortAvailable", toolbar != null);
            if (toolbar != null) {
                var bounds = toolbar.getAbsoluteBounds();
                result.addProperty("toolbarBounds", bounds.toString());
                ClientIpnProbe.move(bounds.getX() + bounds.getWidth() / 2.0, bounds.getY() + bounds.getHeight() / 2.0);
                before = ClientIpnProbe.sorts;
                var press = net.minecraft.client.MouseHandler.class.getDeclaredMethod("onPress", long.class, int.class, int.class, int.class);
                press.setAccessible(true); press.invoke(mc.mouseHandler, mc.getWindow().getWindow(), 0, 1, 0);
                press.invoke(mc.mouseHandler, mc.getWindow().getWindow(), 0, 0, 0);
                result.addProperty("nativeToolbarSorts", ClientIpnProbe.sorts - before);
            }
            phase = 2; return false;
        }
        if (phase == 2) {
            int expectedStacks = result.get("nativeSortCallbacks").getAsInt() == 0 ? 2 : 1;
            if (!mc.player.containerMenu.getCarried().isEmpty() || dirtStacks(mc.player.getInventory()) != expectedStacks) return false;
            result.add("after", new com.google.gson.Gson().toJsonTree(items(mc.player.getInventory())));
            result.addProperty("afterDirtStacks", dirtStacks(mc.player.getInventory()));
            if (!items(mc.player.getInventory()).equals(EXPECTED)) throw new AssertionError("Native sort changed item custody");
            Hotkeys.INSTANCE.getSORT_INVENTORY().getMainKeybind().setKeyCodes(originalKeys);
            captureServer();
            phase = 3; return false;
        }
        int expectedStacks = result.get("nativeSortCallbacks").getAsInt() == 0 ? 2 : 1;
        var actual = serverInventory;
        if (actual == null || !actual.items().equals(EXPECTED) || actual.stacks() != expectedStacks || !actual.carriedEmpty()) {
            captureServer(); return false;
        }
        result.add("serverAfter", new com.google.gson.Gson().toJsonTree(actual.items()));
        result.addProperty("serverDirtStacks", actual.stacks()); result.addProperty("serverCarriedEmpty", actual.carriedEmpty());
        if (phase == 3) {
            result.addProperty("beforeShiftHotbarItems", actual.hotbarItems());
            var inventory = (InventoryScreen) mc.screen; var slot = inventory.getMenu().getSlot(9);
            ClientIpnProbe.move(inventory.getGuiLeft() + slot.x + 8, inventory.getGuiTop() + slot.y + 8);
            ClientModifierProbe.controlledInput = true; ClientModifierProbe.heldKeys.add(340);
            try {
                var press = net.minecraft.client.MouseHandler.class.getDeclaredMethod("onPress", long.class, int.class, int.class, int.class);
                press.setAccessible(true);
                press.invoke(mc.mouseHandler, mc.getWindow().getWindow(), 0, 1, 1);
                press.invoke(mc.mouseHandler, mc.getWindow().getWindow(), 0, 0, 1);
            } finally { ClientModifierProbe.heldKeys.remove(340); ClientModifierProbe.controlledInput = false; }
            serverInventory = null; captureServer(); phase = 4; return false;
        }
        if (actual.hotbarItems() == 0 || !items(mc.player.getInventory()).equals(EXPECTED)
                || !mc.player.containerMenu.getCarried().isEmpty()) {
            captureServer(); return false;
        }
        result.addProperty("nativeShiftClickHotbarItems", actual.hotbarItems());
        result.add("afterShiftClick", new com.google.gson.Gson().toJsonTree(items(mc.player.getInventory())));
        if (net.neoforged.fml.ModList.get().isLoaded("mousetweaks")) {
            gestures = new ClientIpnMouseGestures(result); return false;
        }
        return true;
    }

    private void captureServer() {
        var server = Minecraft.getInstance().getSingleplayerServer();
        var actor = Minecraft.getInstance().player.getUUID();
        server.execute(() -> {
            var player = server.getPlayerList().getPlayer(actor);
            int hotbar = java.util.stream.IntStream.range(0, 9).map(index -> player.getInventory().getItem(index).getCount()).sum();
            serverInventory = new ServerInventory(items(player.getInventory()), dirtStacks(player.getInventory()), player.containerMenu.getCarried().isEmpty(), hotbar);
        });
    }

    private static Map<String, Integer> items(Inventory inventory) {
        var values = new java.util.TreeMap<String, Integer>();
        for (var item : inventory.items) if (!item.isEmpty()) values.merge(BuiltInRegistries.ITEM.getKey(item.getItem()).toString(), item.getCount(), Integer::sum);
        return Map.copyOf(values);
    }
    private static long dirtStacks(Inventory inventory) { return inventory.items.stream().filter(item -> item.is(Items.DIRT)).count(); }
}
