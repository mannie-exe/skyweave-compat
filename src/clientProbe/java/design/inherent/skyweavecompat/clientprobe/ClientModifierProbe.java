package design.inherent.skyweavecompat.clientprobe;

import com.google.gson.JsonObject;
import com.mojang.blaze3d.platform.InputConstants;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.client.settings.KeyModifier;

/** Exact native caller observations, restricted to a marked disposable integrated client. */
public final class ClientModifierProbe {
    public static int perspectiveNotifications;
    public static int undoPackets, redoPackets;
    public static boolean controlledInput;
    public static final java.util.Set<Integer> heldKeys = new java.util.HashSet<>();
    private ClientModifierProbe() {}

    static void observe(Path root, Minecraft mc) throws Exception {
        if (!Files.isRegularFile(root.resolve("modifier-review-fixture.json")) || mc.player == null
                || mc.getSingleplayerServer() == null)
            throw new IllegalStateException("Expected a marked disposable integrated modifier fixture");
        var report = new JsonObject();
        report.addProperty("scope", "Native keyboard dispatch and actual Moves camera consumer; synthetic events without OS modifier injection");
        var type = Class.forName("punchymoves.client.emote.EmoteKeybindManager");
        var cameraField = type.getDeclaredField("cameraEditor"); cameraField.setAccessible(true);
        var camera = (KeyMapping) cameraField.get(null);
        if (camera == null) throw new IllegalStateException("Moves did not register its native camera binding");
        var key = camera.getKey(); var modifier = camera.getKeyModifier();
        var perspective = mc.options.keyTogglePerspective;
        var perspectiveKey = perspective.getKey(); var perspectiveModifier = perspective.getKeyModifier();
        var initialPerspective = mc.options.getCameraType();
        try {
            mc.setScreen(null);
            camera.setKeyModifierAndCode(KeyModifier.SHIFT, InputConstants.Type.KEYSYM.getOrCreate(75));
            KeyMapping.resetMapping();
            key(mc, 75);
            report.addProperty("wrongModifierOpenedCamera", mc.screen != null);
            report.addProperty("wrongModifierScreen", mc.screen == null ? "none" : mc.screen.getClass().getName());
            mc.setScreen(null);
            camera.setKeyModifierAndCode(KeyModifier.NONE, InputConstants.Type.KEYSYM.getOrCreate(75));
            KeyMapping.resetMapping();
            key(mc, 75);
            report.addProperty("plainOpenedCamera", mc.screen != null);
            report.addProperty("plainScreen", mc.screen == null ? "none" : mc.screen.getClass().getName());
            mc.setScreen(null);
            camera.setKeyModifierAndCode(modifier, key);
            perspective.setKeyModifierAndCode(KeyModifier.SHIFT, InputConstants.Type.KEYSYM.getOrCreate(302));
            KeyMapping.resetMapping();
            var controller = Class.forName("punchymoves.client.camera.ShoulderCameraController");
            var override = controller.getDeclaredField("vanillaPerspectiveOverride"); override.setAccessible(true);
            override.setBoolean(null, false);
            int notifications = perspectiveNotifications;
            key(mc, 302);
            nativeKeybinds(mc);
            report.addProperty("wrongModifierPerspectiveChanged", mc.options.getCameraType() != initialPerspective);
            report.addProperty("wrongModifierPerspectiveObserved", override.getBoolean(null));
            report.addProperty("wrongModifierPerspectiveNotifications", perspectiveNotifications - notifications);
            override.setBoolean(null, false);
            perspective.setKeyModifierAndCode(KeyModifier.NONE, InputConstants.Type.KEYSYM.getOrCreate(302));
            KeyMapping.resetMapping();
            notifications = perspectiveNotifications;
            key(mc, 302);
            nativeKeybinds(mc);
            report.addProperty("plainPerspectiveChanged", mc.options.getCameraType() != initialPerspective);
            report.addProperty("plainPerspectiveObserved", override.getBoolean(null));
            report.addProperty("plainPerspectiveNotifications", perspectiveNotifications - notifications);
            report.add("nativeAdmission", movesAdmission(mc, camera));
            report.add("activeCamera", movesActiveCamera(mc, camera, perspective));
            report.add("emotes", movesEmotes(mc));
        } finally {
            mc.setScreen(null);
            camera.setKeyModifierAndCode(modifier, key);
            perspective.setKeyModifierAndCode(perspectiveModifier, perspectiveKey);
            mc.options.setCameraType(initialPerspective);
            KeyMapping.resetMapping();
        }
        ProbeStateFiles.write(root.resolve("modifier-proof.json"), report.toString());
    }

    private static void key(Minecraft mc, int code) {
        mc.keyboardHandler.keyPress(mc.getWindow().getWindow(), code, 0, 1, 0);
        mc.keyboardHandler.keyPress(mc.getWindow().getWindow(), code, 0, 0, 0);
    }

    private static JsonObject movesAdmission(Minecraft mc, KeyMapping camera) throws Exception {
        var result = new JsonObject();
        var competitor = new KeyMapping("key.skyweave_probe.modifier_priority", 303, "key.categories.misc");
        competitor.setKeyConflictContext(net.neoforged.neoforge.client.settings.KeyConflictContext.IN_GAME);
        var originalContext = camera.getKeyConflictContext();
        var originalKey = camera.getKey(); var originalModifier = camera.getKeyModifier();
        controlledInput = true;
        try {
            for (var entry : java.util.Map.of(KeyModifier.SHIFT, new int[]{340,344}, KeyModifier.ALT, new int[]{342,346},
                    KeyModifier.CONTROL, Minecraft.ON_OSX ? new int[]{343,347} : new int[]{341,345}).entrySet()) {
                camera.setKeyModifierAndCode(entry.getKey(), InputConstants.Type.KEYSYM.getOrCreate(302)); KeyMapping.resetMapping();
                for (int side : entry.getValue()) {
                    mc.setScreen(null); event(mc, side, 1); event(mc, 302, 1);
                    result.addProperty(entry.getKey().name() + side, cameraScreen(mc));
                    event(mc, 302, 0); event(mc, side, 0);
                }
            }
            mc.setScreen(null);
            camera.setKeyModifierAndCode(KeyModifier.NONE, InputConstants.Type.KEYSYM.getOrCreate(302));
            competitor.setKeyModifierAndCode(KeyModifier.SHIFT, InputConstants.Type.KEYSYM.getOrCreate(302)); KeyMapping.resetMapping();
            event(mc, 340, 1); event(mc, 302, 1);
            result.addProperty("competingModifiedCameraOpened", cameraScreen(mc));
            result.addProperty("competingModifiedClickRetained", competitor.consumeClick());
            event(mc, 302, 0); event(mc, 340, 0);
            competitor.setKeyModifierAndCode(KeyModifier.NONE, InputConstants.UNKNOWN); KeyMapping.resetMapping();
            mc.setScreen(null); event(mc, 340, 1); event(mc, 302, 1);
            result.addProperty("extraModifierPlainFallbackOpened", cameraScreen(mc));
            event(mc, 302, 0); event(mc, 340, 0);
            camera.setKeyConflictContext(net.neoforged.neoforge.client.settings.KeyConflictContext.GUI); KeyMapping.resetMapping();
            mc.setScreen(null); event(mc, 302, 1);
            result.addProperty("inactiveContextCameraOpened", cameraScreen(mc)); event(mc, 302, 0);
            camera.setKeyConflictContext(originalContext); KeyMapping.resetMapping();
            var chat = new net.minecraft.client.gui.screens.ChatScreen(""); mc.setScreen(chat);
            event(mc, 302, 1); event(mc, 302, 0); chat.charTyped('p', 0);
            result.addProperty("chatRetained", mc.screen == chat);
            result.addProperty("chatTextRetained", chat.getFocused() instanceof net.minecraft.client.gui.components.EditBox text && text.getValue().equals("p"));
            mc.setScreen(null); event(mc, 302, 2);
            result.addProperty("repeatOpenedCamera", cameraScreen(mc)); event(mc, 302, 0);
        } finally {
            for (int held : java.util.Set.copyOf(heldKeys)) event(mc, held, 0);
            controlledInput = false; heldKeys.clear(); mc.setScreen(null);
            camera.setKeyConflictContext(originalContext);
            camera.setKeyModifierAndCode(originalModifier, originalKey);
            competitor.setKeyModifierAndCode(KeyModifier.NONE, InputConstants.UNKNOWN); KeyMapping.resetMapping();
            KeyMapping.releaseAll();
        }
        return result;
    }

    private static boolean cameraScreen(Minecraft mc) {
        return mc.screen != null && mc.screen.getClass().getName().equals("punchymoves.client.camera.ShoulderCameraScreen");
    }

    private static JsonObject movesActiveCamera(Minecraft mc, KeyMapping camera, KeyMapping perspective) throws Exception {
        var result = new JsonObject();
        var type = Class.forName("punchymoves.client.camera.ShoulderCameraController");
        var enable = type.getMethod("setEnabledFromEditor", boolean.class);
        var toggle = type.getMethod("toggleFreeCamera"); var active = type.getMethod("isFreeCameraActive");
        var key = camera.getKey(); var modifier = camera.getKeyModifier();
        try {
            mc.setScreen(null); camera.setKeyModifierAndCode(KeyModifier.NONE, InputConstants.UNKNOWN);
            perspective.setKeyModifierAndCode(KeyModifier.SHIFT, InputConstants.Type.KEYSYM.getOrCreate(302)); KeyMapping.resetMapping();
            KeyMapping.releaseAll();
            enable.invoke(null, true); toggle.invoke(null);
            result.addProperty("nativeFreeCameraStarted", (boolean) active.invoke(null));
            var before = mc.options.getCameraType(); key(mc, 302); nativeKeybinds(mc);
            result.addProperty("wrongModifierKeptFreeCamera", (boolean) active.invoke(null));
            result.addProperty("wrongModifierKeptPerspective", mc.options.getCameraType() == before);
            enable.invoke(null, true); if (!(boolean) active.invoke(null)) toggle.invoke(null);
            perspective.setKeyModifierAndCode(KeyModifier.NONE, InputConstants.Type.KEYSYM.getOrCreate(302)); KeyMapping.resetMapping();
            KeyMapping.releaseAll();
            int notifications = perspectiveNotifications; before = mc.options.getCameraType();
            key(mc, 302); nativeKeybinds(mc);
            result.addProperty("nativePerspectiveChanged", mc.options.getCameraType() != before);
            result.addProperty("nativePerspectiveStoppedFreeCamera", !(boolean) active.invoke(null));
            result.addProperty("nativePerspectiveNotifications", perspectiveNotifications - notifications);
            enable.invoke(null, true); toggle.invoke(null); notifications = perspectiveNotifications;
            type.getMethod("onVanillaPerspectiveChange").invoke(null);
            result.addProperty("publicObserverPreserved", !(boolean) active.invoke(null) && perspectiveNotifications - notifications == 1);
        } finally {
            enable.invoke(null, false); camera.setKeyModifierAndCode(modifier, key); KeyMapping.resetMapping();
        }
        return result;
    }

    private static JsonObject movesEmotes(Minecraft mc) throws Exception {
        var result = new JsonObject();
        result.addProperty("scope", "Actual public catalog consumer, keyboard/menu, and native emote trigger; authored fixture entry, no animation-rendering claim");
        var manager = Class.forName("punchymoves.client.emote.EmoteKeybindManager");
        var emoteType = Class.forName("punchymoves.client.animation.AnimationLibrary$Emote");
        var catalogType = Class.forName("punchymoves.client.animation.AnimationLibrary$EmoteCatalog");
        var catalogConstructor = catalogType.getConstructor(String.class, java.util.List.class);
        var reload = manager.getMethod("reload", catalogType);
        var entry = emoteType.getConstructor(String.class, String.class, String.class, String.class)
                .newInstance("skyweave_probe", "Fixture emote", "skyweave_probe", "F13");
        var catalog = catalogConstructor.newInstance("F14", java.util.List.of(entry));
        var empty = catalogConstructor.newInstance("F14", java.util.List.of());
        var activeField = manager.getDeclaredField("active"); activeField.setAccessible(true);
        var engine = Class.forName("punchymoves.client.PlayerAnimationEngine");
        var animations = engine.getDeclaredField("animations"); animations.setAccessible(true);
        var library = animations.get(null);
        Object original = library == null ? empty : library.getClass().getMethod("emotes").invoke(library);
        var trigger = engine.getDeclaredField("emoteTrigger"); trigger.setAccessible(true);
        var menuField = manager.getDeclaredField("menu"); menuField.setAccessible(true);
        reload.invoke(null, catalog);
        var active = (java.util.List<?>) activeField.get(null);
        var accessor = active.getFirst().getClass().getDeclaredMethod("mapping"); accessor.setAccessible(true);
        var binding = (KeyMapping) accessor.invoke(active.getFirst());
        var menu = (KeyMapping) menuField.get(null); var menuKey = menu.getKey(); var menuModifier = menu.getKeyModifier();
        var fallback = new KeyMapping("key.skyweave_probe.removed_emote", 302, "key.categories.misc");
        fallback.setKeyConflictContext(net.neoforged.neoforge.client.settings.KeyConflictContext.IN_GAME);
        controlledInput = true;
        try {
            binding.setKeyModifierAndCode(KeyModifier.SHIFT, InputConstants.Type.KEYSYM.getOrCreate(302));
            menu.setKeyModifierAndCode(KeyModifier.SHIFT, InputConstants.Type.KEYSYM.getOrCreate(303)); KeyMapping.resetMapping();
            mc.setScreen(null); int before = trigger.getInt(null); key(mc, 302);
            result.addProperty("wrongModifierTriggers", trigger.getInt(null) - before);
            before = trigger.getInt(null); event(mc, 344, 1); key(mc, 302); event(mc, 344, 0);
            result.addProperty("configuredTriggers", trigger.getInt(null) - before);
            key(mc, 303); result.addProperty("wrongModifierMenuOpened", mc.screen != null); mc.setScreen(null);
            event(mc, 340, 1); key(mc, 303);
            result.addProperty("configuredMenuOpened", mc.screen != null && mc.screen.getClass().getName().endsWith(".EmoteWheelScreen"));
            key(mc, 303); result.addProperty("secondPressMenuClosed", mc.screen == null); event(mc, 340, 0);
            mc.setScreen(null); reload.invoke(null, empty);
            result.addProperty("removedActiveCount", ((java.util.List<?>) activeField.get(null)).size());
            event(mc, 340, 1); key(mc, 302); event(mc, 340, 0);
            result.addProperty("removedModifiedBindingPreservedNativeFallback", fallback.consumeClick());
            reload.invoke(null, catalog);
            result.addProperty("restoredModifiedBinding", binding.getKeyModifier() == KeyModifier.SHIFT && binding.getKey().getValue() == 302);
        } finally {
            for (int held : java.util.Set.copyOf(heldKeys)) event(mc, held, 0);
            controlledInput = false; heldKeys.clear(); mc.setScreen(null);
            binding.setKeyModifierAndCode(KeyModifier.NONE, InputConstants.UNKNOWN);
            fallback.setKeyModifierAndCode(KeyModifier.NONE, InputConstants.UNKNOWN);
            menu.setKeyModifierAndCode(menuModifier, menuKey); reload.invoke(null, original); KeyMapping.resetMapping();
        }
        return result;
    }

    private static void nativeKeybinds(Minecraft mc) throws Exception {
        var method = Minecraft.class.getDeclaredMethod("handleKeybinds"); method.setAccessible(true);
        method.invoke(mc);
    }

    static void effortless(Path root, Minecraft mc) throws Exception {
        if (!Files.isRegularFile(root.resolve("modifier-review-fixture.json")) || mc.player == null
                || mc.getSingleplayerServer() == null)
            throw new IllegalStateException("Expected a marked integrated modifier fixture");
        var type = Class.forName("neoforge.nl.requios.effortlessbuilding.utilities.KeyBindings");
        var mapping = (KeyMapping) type.getField("openRadialMenu").get(null);
        var tick = Class.forName("neoforge.nl.requios.effortlessbuilding.NeoForgeClientSetup$GameEvents")
                .getMethod("onClientTick", net.neoforged.neoforge.client.event.ClientTickEvent.Post.class);
        var key = mapping.getKey(); var modifier = mapping.getKeyModifier();
        var result = new JsonObject();
        result.addProperty("scope", "Actual keyboard dispatcher, Effortless tick and radial lifecycle; fixture supplies physical key polling, not OS qualification");
        controlledInput = true;
        try {
            mc.setScreen(null);
            mapping.setKeyModifierAndCode(KeyModifier.SHIFT, InputConstants.Type.KEYSYM.getOrCreate(75)); KeyMapping.resetMapping();
            event(mc, 75, 1);
            tick.invoke(null, new net.neoforged.neoforge.client.event.ClientTickEvent.Post());
            result.addProperty("wrongModifierOpened", radial(mc));
            event(mc, 75, 0);
            if (radial(mc)) mc.screen.tick();
            mc.setScreen(null);
            mapping.setKeyModifierAndCode(KeyModifier.NONE, InputConstants.Type.KEYSYM.getOrCreate(75)); KeyMapping.resetMapping();
            event(mc, 75, 1);
            tick.invoke(null, new net.neoforged.neoforge.client.event.ClientTickEvent.Post());
            result.addProperty("plainOpened", radial(mc));
            if (radial(mc)) mc.screen.tick();
            result.addProperty("plainRetainedAcrossScreenReset", radial(mc));
            event(mc, 75, 0);
            if (radial(mc)) mc.screen.tick();
            result.addProperty("plainClosedAfterRelease", mc.screen == null);
            mc.setScreen(null);
            mapping.setKeyModifierAndCode(KeyModifier.SHIFT, InputConstants.Type.KEYSYM.getOrCreate(75)); KeyMapping.resetMapping();
            event(mc, 340, 1); event(mc, 75, 1);
            tick.invoke(null, new net.neoforged.neoforge.client.event.ClientTickEvent.Post());
            result.addProperty("configuredModifierOpened", radial(mc));
            event(mc, 340, 0);
            if (radial(mc)) mc.screen.tick();
            result.addProperty("modifierFirstReleaseClosed", mc.screen == null);
            event(mc, 75, 0);
            mc.setScreen(null);
            mapping.setKeyModifierAndCode(KeyModifier.NONE, InputConstants.Type.MOUSE.getOrCreate(4)); KeyMapping.resetMapping();
            mouse(mc, 4, 1); tick.invoke(null, new net.neoforged.neoforge.client.event.ClientTickEvent.Post());
            result.addProperty("mouseOpened", radial(mc));
            if (radial(mc)) mc.screen.tick();
            result.addProperty("mouseRetained", radial(mc));
            mouse(mc, 4, 0); if (radial(mc)) mc.screen.tick();
            result.addProperty("mouseReleasedClosed", mc.screen == null);
            mc.setScreen(null);
            mapping.setKeyModifierAndCode(KeyModifier.NONE, InputConstants.Type.SCANCODE.getOrCreate(65)); KeyMapping.resetMapping();
            mc.keyboardHandler.keyPress(mc.getWindow().getWindow(), -1, 65, 1, 0);
            tick.invoke(null, new net.neoforged.neoforge.client.event.ClientTickEvent.Post());
            result.addProperty("scancodeOpened", radial(mc));
            mc.keyboardHandler.keyPress(mc.getWindow().getWindow(), -1, 65, 0, 0);
            if (radial(mc)) mc.screen.tick(); result.addProperty("scancodeReleasedClosed", mc.screen == null);
            mc.setScreen(null);
            mapping.setKeyModifierAndCode(KeyModifier.NONE, InputConstants.Type.KEYSYM.getOrCreate(75)); KeyMapping.resetMapping();
            event(mc, 75, 1); tick.invoke(null, new net.neoforged.neoforge.client.event.ClientTickEvent.Post());
            mc.setWindowActive(false); if (radial(mc)) mc.screen.tick();
            result.addProperty("focusLossClosed", mc.screen == null);
            mc.setWindowActive(true); mc.setScreen(null); event(mc, 75, 2);
            tick.invoke(null, new net.neoforged.neoforge.client.event.ClientTickEvent.Post());
            result.addProperty("repeatReopenedAfterFocusLoss", radial(mc)); event(mc, 75, 0);
            mc.setScreen(null); event(mc, 75, 1); tick.invoke(null, new net.neoforged.neoforge.client.event.ClientTickEvent.Post());
            mc.setScreen(new net.minecraft.client.gui.screens.ChatScreen("")); event(mc, 75, 0); mc.screen.charTyped('k', 0);
            result.addProperty("otherScreenRetained", mc.screen instanceof net.minecraft.client.gui.screens.ChatScreen);
            mc.setScreen(null); tick.invoke(null, new net.neoforged.neoforge.client.event.ClientTickEvent.Post());
            result.addProperty("staleScreenHoldReopened", radial(mc));
            mc.setScreen(null); mapping.setKeyModifierAndCode(KeyModifier.NONE, InputConstants.UNKNOWN); KeyMapping.resetMapping();
            result.add("history", effortlessHistory(mc, type, tick));
        } finally {
            for (int held : java.util.Set.copyOf(heldKeys)) event(mc, held, 0);
            controlledInput = false; heldKeys.clear(); mc.setScreen(null);
            mapping.setKeyModifierAndCode(modifier, key); KeyMapping.resetMapping();
        }
        ProbeStateFiles.write(root.resolve("modifier-effortless-proof.json"), result.toString());
    }

    private static boolean radial(Minecraft mc) {
        return mc.screen != null && mc.screen.getClass().getName().equals("neoforge.nl.requios.effortlessbuilding.screen.RadialMenu");
    }

    private static void event(Minecraft mc, int key, int action) {
        if (action == 0) heldKeys.remove(key); else heldKeys.add(key);
        mc.keyboardHandler.keyPress(mc.getWindow().getWindow(), key, 0, action, 0);
    }

    private static void mouse(Minecraft mc, int button, int action) throws Exception {
        var method = net.minecraft.client.MouseHandler.class.getDeclaredMethod("onPress", long.class, int.class, int.class, int.class);
        method.setAccessible(true); method.invoke(mc.mouseHandler, mc.getWindow().getWindow(), button, action, 0);
    }

    private static JsonObject effortlessHistory(Minecraft mc, Class<?> type, java.lang.reflect.Method tick) throws Exception {
        var undo = (KeyMapping) type.getField("undo").get(null);
        var redo = (KeyMapping) type.getField("redo").get(null);
        var undoKey = undo.getKey(); var undoModifier = undo.getKeyModifier();
        var redoKey = redo.getKey(); var redoModifier = redo.getKeyModifier();
        var result = new JsonObject();
        try {
            undo.setKeyModifierAndCode(KeyModifier.SHIFT, InputConstants.Type.KEYSYM.getOrCreate(302));
            redo.setKeyModifierAndCode(KeyModifier.ALT, InputConstants.Type.KEYSYM.getOrCreate(303)); KeyMapping.resetMapping();
            int before = undoPackets;
            event(mc, 302, 1); tick.invoke(null, new net.neoforged.neoforge.client.event.ClientTickEvent.Post()); event(mc, 302, 0);
            result.addProperty("wrongModifierUndo", undoPackets - before);
            before = undoPackets;
            event(mc, 340, 1); event(mc, 302, 1); tick.invoke(null, new net.neoforged.neoforge.client.event.ClientTickEvent.Post());
            event(mc, 302, 0); event(mc, 340, 0); result.addProperty("configuredShiftUndo", undoPackets - before);
            before = redoPackets;
            event(mc, 346, 1); event(mc, 303, 1); tick.invoke(null, new net.neoforged.neoforge.client.event.ClientTickEvent.Post());
            event(mc, 303, 0); event(mc, 346, 0); result.addProperty("configuredAltRedo", redoPackets - before);
            undo.setKeyModifierAndCode(KeyModifier.CONTROL, InputConstants.Type.KEYSYM.getOrCreate(302)); KeyMapping.resetMapping();
            int control = Minecraft.ON_OSX ? 343 : 341;
            before = undoPackets;
            event(mc, control, 1); event(mc, 302, 1); tick.invoke(null, new net.neoforged.neoforge.client.event.ClientTickEvent.Post());
            event(mc, 302, 0); event(mc, control, 0); result.addProperty("platformControlUndo", undoPackets - before);
            undo.setKeyModifierAndCode(KeyModifier.NONE, InputConstants.Type.KEYSYM.getOrCreate(302)); KeyMapping.resetMapping();
            before = undoPackets;
            event(mc, 302, 1); tick.invoke(null, new net.neoforged.neoforge.client.event.ClientTickEvent.Post()); event(mc, 302, 0);
            result.addProperty("plainWithoutPhysicalControlUndo", undoPackets - before);
            before = undoPackets;
            event(mc, 345, 1); event(mc, 302, 1); tick.invoke(null, new net.neoforged.neoforge.client.event.ClientTickEvent.Post());
            event(mc, 302, 0); event(mc, 345, 0); result.addProperty("plainPhysicalControlUndo", undoPackets - before);
        } finally {
            undo.setKeyModifierAndCode(undoModifier, undoKey); redo.setKeyModifierAndCode(redoModifier, redoKey); KeyMapping.resetMapping();
        }
        return result;
    }

    static void persistence(Path root, Minecraft mc, boolean save) throws Exception {
        if (!Files.isRegularFile(root.resolve("modifier-review-fixture.json")))
            throw new IllegalStateException("Expected a disposable modifier fixture");
        var type = Class.forName("punchymoves.client.emote.EmoteKeybindManager");
        var result = new JsonObject();
        for (var mapping : mc.options.keyMappings) if (mapping.getName().equals("key.skyweave_compat.wayfinder")) {
            if (save) mapping.setKeyModifierAndCode(KeyModifier.NONE, InputConstants.Type.KEYSYM.getOrCreate(297));
            var state = new JsonObject(); state.addProperty("key", mapping.getKey().getName());
            state.addProperty("defaultKey", mapping.getDefaultKey().getName()); result.add("wayfinder", state);
        }
        for (String name : java.util.List.of("cameraEditor", "cameraToggle", "freeCamera")) {
            var field = type.getDeclaredField(name); field.setAccessible(true);
            var mapping = (KeyMapping) field.get(null);
            if (save) {
                var modifier = name.equals("cameraEditor") ? KeyModifier.SHIFT : name.equals("cameraToggle") ? KeyModifier.ALT : KeyModifier.NONE;
                var key = name.equals("freeCamera") ? InputConstants.UNKNOWN : InputConstants.Type.KEYSYM.getOrCreate(name.equals("cameraEditor") ? 302 : 303);
                mapping.setKeyModifierAndCode(modifier, key);
            }
            var state = new JsonObject(); state.addProperty("key", mapping.getKey().getName());
            state.addProperty("modifier", mapping.getKeyModifier().name()); state.addProperty("defaultKey", mapping.getDefaultKey().getName());
            result.add(name, state);
        }
        if (save) { KeyMapping.resetMapping(); mc.options.save(); }
        var saved = new com.google.gson.JsonArray();
        for (String line : Files.readAllLines(root.resolve("options.txt")))
            if (line.startsWith("key_key.punchy_moves.")) saved.add(line);
        result.add("nativeOptions", saved);
        ProbeStateFiles.write(root.resolve("modifier-persistence-proof.json"), result.toString());
    }
}
