package design.inherent.skyweavecompat.clientprobe;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;

/** Native mouse callbacks, actual Mouse Tweaks writes, and real container packets in the owned world. */
final class ClientIpnMouseGestures {
    private static final Map<String, Integer> EXPECTED = Map.of("minecraft:dirt", 13, "minecraft:cobblestone", 11);
    private record Custody(Map<String, Integer> player, Map<String, Integer> chest, Map<String, Integer> carried) {
        Map<String, Integer> total() {
            var result = new java.util.TreeMap<String, Integer>();
            for (var part : java.util.List.of(player, chest, carried)) part.forEach((key, value) -> result.merge(key, value, Integer::sum));
            return Map.copyOf(result);
        }
    }
    private final JsonObject result = new JsonObject();
    private final BlockPos position;
    private int phase, ticks, wheelBefore, dragBefore;
    private volatile boolean pending;
    private volatile Custody server;
    private volatile String failure;

    ClientIpnMouseGestures(JsonObject parent) {
        parent.add("mouseTweaks", result);
        result.addProperty("scope", "Native mouse callbacks and actual Mouse Tweaks/container transport; synthetic coordinates, not OS input");
        position = Minecraft.getInstance().player.blockPosition().offset(2, 0, 0);
    }

    boolean tick() throws ReflectiveOperationException {
        var mc = Minecraft.getInstance();
        if (failure != null || ++ticks > 120) throw new IllegalStateException("Mouse gesture fixture phase" + phase + ": " + failure + " " + result);
        if (phase == 0) {
            var actor = mc.player.getUUID();
            mc.getSingleplayerServer().execute(() -> {
                try {
                    var player = mc.getSingleplayerServer().getPlayerList().getPlayer(actor);
                    player.serverLevel().setBlockAndUpdate(position, Blocks.CHEST.defaultBlockState());
                    player.openMenu((ChestBlockEntity) player.serverLevel().getBlockEntity(position));
                } catch (Throwable error) { failure = error.toString(); }
            });
            phase = 1; return false;
        }
        if (!(mc.screen instanceof ContainerScreen screen) || !(mc.player.containerMenu instanceof ChestMenu menu)) return false;
        capture();
        var current = server;
        if (current == null) return false;
        var client = client(menu);
        if (!current.total().equals(EXPECTED) || !client.total().equals(EXPECTED)) return false;
        if (phase == 1) {
            if (!current.chest().isEmpty() || !current.carried().isEmpty()) throw new IllegalStateException("Expected an empty fixture chest/cursor");
            if (net.neoforged.fml.ModList.get().isLoaded("emi")) {
                var search = (net.minecraft.client.gui.components.EditBox) Class.forName("dev.emi.emi.screen.EmiScreenManager").getField("search").get(null);
                search.setFocused(true);
                result.addProperty("searchInitiallyFocused", search.isFocused());
            }
            wheelBefore = ClientIpnProbe.mouseTweaksWheelCalls; dragBefore = ClientIpnProbe.mouseTweaksDragClicks;
            move(screen, dirt(menu)); wheel(mc, -1); advance(2); return false;
        }
        if (phase == 2) {
            if (current.chest().getOrDefault("minecraft:dirt", 0) == 0 || client.chest().isEmpty()) return false;
            record("wheelOut", current); move(screen, dirt(menu)); wheel(mc, 1); advance(3); return false;
        }
        if (phase == 3) {
            if (!current.chest().isEmpty() || !client.chest().isEmpty()) return false;
            record("wheelBack", current); move(screen, dirt(menu)); click(mc, 0); advance(4); return false;
        }
        if (phase == 4) {
            if (current.carried().getOrDefault("minecraft:dirt", 0) != 13 || client.carried().getOrDefault("minecraft:dirt", 0) != 13) return false;
            move(screen, menu.getSlot(0)); press(mc, 1, 1); advance(5); return false;
        }
        if (phase == 5) {
            move(screen, menu.getSlot(1)); advance(6); return false;
        }
        if (phase == 6) {
            if (menu.getSlot(0).getItem().isEmpty() || menu.getSlot(1).getItem().isEmpty()
                    || current.chest().getOrDefault("minecraft:dirt", 0) < 2) return false;
            press(mc, 1, 0); move(screen, menu.getSlot(2)); click(mc, 0); advance(7); return false;
        }
        if (phase == 7) {
            if (!current.carried().isEmpty() || current.chest().getOrDefault("minecraft:dirt", 0) != 13 || !client.carried().isEmpty()) return false;
            record("drag", current);
            ClientModifierProbe.controlledInput = true; ClientModifierProbe.heldKeys.add(340);
            try { for (int index = 0; index < 3; index++) { move(screen, menu.getSlot(index)); click(mc, 0); } }
            finally { ClientModifierProbe.heldKeys.remove(340); ClientModifierProbe.controlledInput = false; }
            advance(8); return false;
        }
        if (!current.player().equals(EXPECTED) || !current.chest().isEmpty() || !current.carried().isEmpty()
                || !client.player().equals(EXPECTED) || !client.carried().isEmpty()) return false;
        record("restored", current);
        result.addProperty("nativeWheelCalls", ClientIpnProbe.mouseTweaksWheelCalls - wheelBefore);
        result.addProperty("nativeRmbDragClicks", ClientIpnProbe.mouseTweaksDragClicks - dragBefore);
        mc.player.closeContainer(); mc.setScreen(null);
        return true;
    }

    private void advance(int next) { phase = next; server = null; }
    private void record(String name, Custody value) { result.add(name, new Gson().toJsonTree(value)); }
    private static Slot dirt(ChestMenu menu) {
        return menu.slots.stream().filter(slot -> slot.container == Minecraft.getInstance().player.getInventory() && slot.getItem().is(Items.DIRT))
                .findFirst().orElseThrow();
    }
    private static Map<String, Integer> items(net.minecraft.world.Container inventory) {
        var values = new java.util.TreeMap<String, Integer>();
        for (int i = 0; i < inventory.getContainerSize(); i++) add(values, inventory.getItem(i));
        return Map.copyOf(values);
    }
    private static void add(Map<String, Integer> values, ItemStack stack) {
        if (!stack.isEmpty()) values.merge(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(), stack.getCount(), Integer::sum);
    }
    private static Map<String, Integer> carried(ItemStack stack) { var values = new java.util.TreeMap<String, Integer>(); add(values, stack); return Map.copyOf(values); }
    private static Custody client(ChestMenu menu) {
        return new Custody(items(Minecraft.getInstance().player.getInventory()), items(menu.getContainer()), carried(menu.getCarried()));
    }
    private void capture() {
        if (pending) return;
        pending = true; var mc = Minecraft.getInstance(); var actor = mc.player.getUUID();
        mc.getSingleplayerServer().execute(() -> {
            try {
                var player = mc.getSingleplayerServer().getPlayerList().getPlayer(actor);
                server = new Custody(items(player.getInventory()), items((ChestBlockEntity) player.serverLevel().getBlockEntity(position)), carried(player.containerMenu.getCarried()));
            } catch (Throwable error) { failure = error.toString(); }
            finally { pending = false; }
        });
    }
    private static void move(ContainerScreen screen, Slot slot) throws ReflectiveOperationException {
        var mc = Minecraft.getInstance(); int x = screen.getGuiLeft() + slot.x + 8, y = screen.getGuiTop() + slot.y + 8;
        ClientIpnProbe.move(x, y); var graphics = new GuiGraphics(mc, mc.renderBuffers().bufferSource()); screen.render(graphics, x, y, 0); graphics.flush();
    }
    private static void click(Minecraft mc, int button) throws ReflectiveOperationException { press(mc, button, 1); press(mc, button, 0); }
    private static void press(Minecraft mc, int button, int phase) throws ReflectiveOperationException {
        var method = net.minecraft.client.MouseHandler.class.getDeclaredMethod("onPress", long.class, int.class, int.class, int.class);
        method.setAccessible(true); method.invoke(mc.mouseHandler, mc.getWindow().getWindow(), button, phase, ClientModifierProbe.heldKeys.contains(340) ? 1 : 0);
    }
    private static void wheel(Minecraft mc, double amount) throws ReflectiveOperationException {
        var method = net.minecraft.client.MouseHandler.class.getDeclaredMethod("onScroll", long.class, double.class, double.class);
        method.setAccessible(true); method.invoke(mc.mouseHandler, mc.getWindow().getWindow(), 0d, amount);
    }
}
