package design.inherent.skyweavecompat.clientprobe;

import com.google.gson.JsonObject;
import com.mojang.blaze3d.platform.InputConstants;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.settings.KeyModifier;

/** Real table, native extended menu/packet, and actual editor input in an owned world. */
final class ClientPowerGridInputProbe {
    private static BlockPos table;
    private ClientPowerGridInputProbe() {}

    private static void require(Path root, Minecraft mc) {
        if (!Files.isRegularFile(root.resolve("modifier-review-fixture.json")) || mc.player == null || mc.getSingleplayerServer() == null)
            throw new IllegalStateException("Expected an owned integrated Power Grid fixture");
    }

    static void setup(Path root, Minecraft mc) {
        require(root, mc);
        var server = mc.getSingleplayerServer(); var actor = mc.player.getUUID();
        table = mc.player.blockPosition().offset(1, 0, 1);
        server.execute(() -> {
            var result = new JsonObject();
            try {
                var player = server.getPlayerList().getPlayer(actor); var level = player.serverLevel();
                var id = ResourceLocation.parse("powergrid:circuit_design_table");
                var block = BuiltInRegistries.BLOCK.getOptional(id).orElseThrow();
                level.setBlockAndUpdate(table, block.defaultBlockState());
                var placed = level.getBlockEntity(table);
                if (placed == null) throw new IllegalStateException("Native table block entity did not appear");
                result.addProperty("blockEntity", placed.getClass().getName());
            } catch (Throwable failure) { result.addProperty("error", failure.toString()); }
            mc.execute(() -> write(root.resolve("powergrid-setup-proof.json"), result));
        });
    }

    static boolean ready(Minecraft mc) {
        return table != null && mc.level != null && mc.level.getBlockEntity(table) != null;
    }

    @SuppressWarnings("unchecked")
    private static net.minecraft.core.component.DataComponentType<Object> connection() throws Exception {
        var supplier = Class.forName("org.patryk3211.powergrid.collections.ModdedDataComponents").getField("CONNECTION_DATA").get(null);
        return (net.minecraft.core.component.DataComponentType<Object>) Class.forName("dev.architectury.registry.registries.RegistrySupplier")
                .getMethod("get").invoke(supplier);
    }

    static void seedWire(Path root, Minecraft mc) throws Exception {
        require(root, mc); mc.player.closeContainer(); mc.setScreen(null);
        var stack = new net.minecraft.world.item.ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse("powergrid:wire")));
        stack.set(connection(), Class.forName("org.patryk3211.powergrid.electricity.wire.WireConnection").getField("EMPTY").get(null));
        var actor = mc.player.getUUID();
        mc.getSingleplayerServer().execute(() -> {
            var player = mc.getSingleplayerServer().getPlayerList().getPlayer(actor);
            player.setItemInHand(InteractionHand.MAIN_HAND, stack); player.containerMenu.broadcastChanges();
        });
    }

    static boolean wireReady(Minecraft mc) {
        if (mc.player == null) return false;
        try {
            var stack = mc.player.getMainHandItem();
            return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString().equals("powergrid:wire") && stack.has(connection());
        } catch (ReflectiveOperationException failure) { return false; }
        catch (Exception failure) { throw new IllegalStateException(failure); }
    }

    static void wire(Path root, Minecraft mc) throws Exception {
        require(root, mc);
        if (!wireReady(mc) || mc.screen != null) throw new IllegalStateException("Native wire/component has not synchronized");
        var type = Class.forName("org.patryk3211.powergrid.collections.ModdedKeys");
        var action = type.getField("ALTERNATE_WIRE_PLACEMENT").get(null);
        var mapping = (KeyMapping) type.getField("keybind").get(action);
        var originalKey = mapping.getKey(); var originalModifier = mapping.getKeyModifier();
        var status = Class.forName("org.patryk3211.powergrid.electricity.wire.ClientWireInteractions")
                .getDeclaredField("alternatePlacementStatus"); status.setAccessible(true);
        var result = new JsonObject(); ClientModifierProbe.controlledInput = true;
        try {
            mapping.setKeyModifierAndCode(KeyModifier.SHIFT, InputConstants.Type.KEYSYM.getOrCreate(302)); KeyMapping.resetMapping();
            key(mc, 302, 1); result.addProperty("wrongModifierActive", status.getBoolean(null)); key(mc, 302, 0);
            key(mc, 340, 1); key(mc, 302, 1); result.addProperty("configuredActive", status.getBoolean(null));
            key(mc, 340, 0); result.addProperty("modifierReleaseActive", status.getBoolean(null)); key(mc, 302, 0);
            mapping.setKeyModifierAndCode(KeyModifier.NONE, InputConstants.Type.KEYSYM.getOrCreate(302)); KeyMapping.resetMapping();
            key(mc, 302, 1); result.addProperty("plainActive", status.getBoolean(null)); key(mc, 302, 0);
            result.addProperty("plainReleaseActive", status.getBoolean(null));
            key(mc, 302, 1); mc.setWindowActive(false); result.addProperty("focusLossActive", status.getBoolean(null));
            mc.setWindowActive(true); key(mc, 302, 0);
            key(mc, 302, 1); mc.setScreen(new net.minecraft.client.gui.screens.ChatScreen(""));
            result.addProperty("screenActive", status.getBoolean(null)); key(mc, 302, 0); mc.setScreen(null);
            key(mc, 302, 2); result.addProperty("repeatActive", status.getBoolean(null)); key(mc, 302, 0);
            mapping.setKeyModifierAndCode(KeyModifier.SHIFT, InputConstants.Type.MOUSE.getOrCreate(4)); KeyMapping.resetMapping();
            mouse(mc, 4, 1); result.addProperty("wrongMouseActive", status.getBoolean(null)); mouse(mc, 4, 0);
            key(mc, 344, 1); mouse(mc, 4, 1); result.addProperty("modifiedMouseActive", status.getBoolean(null));
            key(mc, 344, 0); result.addProperty("mouseModifierReleaseActive", status.getBoolean(null)); mouse(mc, 4, 0);
            result.addProperty("finalActive", status.getBoolean(null));
        } finally {
            for (int held : java.util.Set.copyOf(ClientModifierProbe.heldKeys)) key(mc, held, 0);
            ClientModifierProbe.controlledInput = false; ClientModifierProbe.heldKeys.clear();
            mapping.setKeyModifierAndCode(originalModifier, originalKey); KeyMapping.resetMapping();
        }
        write(root.resolve("powergrid-wire-proof.json"), result);
    }

    static void held(Path root, Minecraft mc, boolean down) throws Exception {
        require(root, mc);
        if (!wireReady(mc) || mc.screen != null) throw new IllegalStateException("Expected the synchronized native wire in game");
        var type = Class.forName("org.patryk3211.powergrid.collections.ModdedKeys");
        var mapping = (KeyMapping) type.getField("keybind").get(type.getField("ALTERNATE_WIRE_PLACEMENT").get(null));
        if (mapping.getKey().getValue() != 341 || mapping.getKeyModifier() != KeyModifier.NONE)
            throw new IllegalStateException("Expected the unchanged native default alternate-wire binding");
        ClientModifierProbe.controlledInput = true;
        key(mc, 341, down ? 1 : 0);
        if (!down) ClientModifierProbe.controlledInput = false;
    }

    static void serverState(Path root, Minecraft mc) {
        require(root, mc); var actor = mc.player.getUUID();
        mc.getSingleplayerServer().execute(() -> {
            var result = new JsonObject();
            try {
                var player = mc.getSingleplayerServer().getPlayerList().getPlayer(actor);
                result.addProperty("serverActive", (boolean) Class.forName("org.patryk3211.powergrid.electricity.wire.IAlternatePlacementExtension")
                        .getMethod("powerGrid$alternatePlacement").invoke(player));
            } catch (ReflectiveOperationException failure) { result.addProperty("error", failure.toString()); }
            mc.execute(() -> {
                try {
                    var field = Class.forName("org.patryk3211.powergrid.electricity.wire.ClientWireInteractions").getDeclaredField("alternatePlacementStatus");
                    field.setAccessible(true); result.addProperty("clientActive", field.getBoolean(null));
                } catch (ReflectiveOperationException failure) { result.addProperty("error", failure.toString()); }
                write(root.resolve("powergrid-server-proof.json"), result);
            });
        });
    }

    private static void mouse(Minecraft mc, int button, int phase) throws Exception {
        var method = net.minecraft.client.MouseHandler.class.getDeclaredMethod("onPress", long.class, int.class, int.class, int.class);
        method.setAccessible(true); method.invoke(mc.mouseHandler, mc.getWindow().getWindow(), button, phase, 0);
    }

    static void open(Path root, Minecraft mc) {
        require(root, mc);
        if (!ready(mc)) throw new IllegalStateException("Client table has not synchronized");
        var actor = mc.player.getUUID();
        mc.getSingleplayerServer().execute(() -> {
            var result = new JsonObject();
            try {
                var player = mc.getSingleplayerServer().getPlayerList().getPlayer(actor);
                var used = player.gameMode.useItemOn(player, player.serverLevel(), player.getMainHandItem(), InteractionHand.MAIN_HAND,
                        new BlockHitResult(Vec3.atCenterOf(table), Direction.UP, table, false));
                result.addProperty("interaction", used.toString());
                result.addProperty("serverMenu", player.containerMenu.getClass().getName());
            } catch (Throwable failure) { result.addProperty("error", failure.toString()); }
            mc.execute(() -> write(root.resolve("powergrid-open-proof.json"), result));
        });
    }

    static void editor(Path root, Minecraft mc) throws Exception {
        require(root, mc);
        var blockEntity = mc.level.getBlockEntity(table);
        if (blockEntity == null) throw new IllegalStateException("Client table has not synchronized");
        var packetType = Class.forName("org.patryk3211.powergrid.network.packets.ChangeScreenC2SPacket");
        var packet = packetType.getConstructor(Class.forName("com.simibubi.create.foundation.blockEntity.SmartBlockEntity"), int.class)
                .newInstance(blockEntity, 1);
        Class.forName("org.patryk3211.powergrid.collections.ModdedPackets")
                .getMethod("sendToServer", Class.forName("org.patryk3211.powergrid.network.C2SPacket")).invoke(null, packet);
    }

    static void observe(Path root, Minecraft mc) throws Exception {
        require(root, mc);
        var screen = mc.screen;
        if (!screen.getClass().getName().equals("org.patryk3211.powergrid.circuits.editor.CircuitDesignTableEditScreen"))
            throw new IllegalStateException("Expected the actual synchronized native circuit editor");
        var enumType = Class.forName("org.patryk3211.powergrid.collections.ModdedKeys");
        var action = enumType.getField("SWITCH_LAYER").get(null);
        var mapping = (KeyMapping) enumType.getField("keybind").get(action);
        var originalKey = mapping.getKey(); var originalModifier = mapping.getKeyModifier();
        var layer = screen.getClass().getDeclaredField("backLayer"); layer.setAccessible(true);
        var nameField = screen.getClass().getDeclaredField("nameField"); nameField.setAccessible(true);
        var text = (net.minecraft.client.gui.components.EditBox) nameField.get(screen);
        var originalText = text.getValue();
        var result = new JsonObject();
        result.addProperty("scope", "Native synchronized table/editor and native keyboard input; fixture supplies physical polling, not OS qualification");
        ClientModifierProbe.controlledInput = true;
        try {
            text.setFocused(false); screen.setFocused(null);
            mapping.setKeyModifierAndCode(KeyModifier.SHIFT, InputConstants.Type.KEYSYM.getOrCreate(302)); KeyMapping.resetMapping();
            boolean before = layer.getBoolean(screen);
            key(mc, 302, 1); key(mc, 302, 0);
            result.addProperty("wrongModifierFlipped", before != layer.getBoolean(screen));
            before = layer.getBoolean(screen);
            key(mc, 340, 1); key(mc, 302, 1); key(mc, 302, 0); key(mc, 340, 0);
            result.addProperty("configuredModifierFlipped", before != layer.getBoolean(screen));
            mapping.setKeyModifierAndCode(KeyModifier.NONE, InputConstants.Type.KEYSYM.getOrCreate(302)); KeyMapping.resetMapping();
            before = layer.getBoolean(screen);
            key(mc, 302, 1); key(mc, 302, 0);
            result.addProperty("plainFlipped", before != layer.getBoolean(screen));
            text.setFocused(true); screen.setFocused(text);
            text.moveCursorToEnd(false);
            before = layer.getBoolean(screen);
            key(mc, 302, 1); key(mc, 302, 0); screen.charTyped('p', 0);
            result.addProperty("focusedTextFlipped", before != layer.getBoolean(screen));
            result.addProperty("typedTextRetained", text.getValue().equals(originalText + "p"));
            result.addProperty("screenRetained", mc.screen == screen);
        } finally {
            for (int held : java.util.Set.copyOf(ClientModifierProbe.heldKeys)) key(mc, held, 0);
            ClientModifierProbe.controlledInput = false; ClientModifierProbe.heldKeys.clear();
            text.setValue(originalText); mapping.setKeyModifierAndCode(originalModifier, originalKey); KeyMapping.resetMapping();
        }
        write(root.resolve("powergrid-input-proof.json"), result);
    }

    private static void key(Minecraft mc, int key, int action) {
        if (action == 0) ClientModifierProbe.heldKeys.remove(key); else ClientModifierProbe.heldKeys.add(key);
        mc.keyboardHandler.keyPress(mc.getWindow().getWindow(), key, 0, action, 0);
    }

    private static void write(Path path, JsonObject result) {
        try { ProbeStateFiles.write(path, result.toString()); }
        catch (java.io.IOException failure) { throw new java.io.UncheckedIOException(failure); }
    }
}
