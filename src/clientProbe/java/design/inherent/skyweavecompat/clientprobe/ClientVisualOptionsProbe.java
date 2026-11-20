package design.inherent.skyweavecompat.clientprobe;

import com.google.gson.*;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.*;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Disposable native resource-option and workstation witness; never a product dependency. */
final class ClientVisualOptionsProbe {
    private final JsonObject report = new JsonObject();
    private final JsonArray profiles = new JsonArray();
    private final Path root = FMLPaths.GAMEDIR.get().toAbsolutePath().normalize();
    private boolean opened, finished, busy;
    private volatile boolean placed;
    private volatile Throwable serverFailure;
    private int ticks, wait, profile, view;
    private Object cacheKey, branch;
    private Class<?> cache, branchType;
    private JsonObject schema;
    private final Map<String, Object> defaults = new LinkedHashMap<>();
    private final boolean menuOnly = Boolean.getBoolean("skyweave_compat.probe.visualOptions.menuOnly");
    private final boolean torchUi = Boolean.getBoolean("skyweave_compat.probe.visualOptions.torchUi");
    private final boolean accessories = Boolean.getBoolean("skyweave_compat.probe.visualOptions.accessories");
    private final List<int[]> accessoryQuads = new ArrayList<>();
    private final List<Map<String, int[]>> deliveredQuads = new ArrayList<>();
    private volatile JsonObject workstationDelivery;
    private int torchStep;
    private boolean torchCategoryReady;
    private String originalPlaced, originalHeld;

    ClientVisualOptionsProbe() {
        if (!Files.isRegularFile(root.resolve("rendering-review-fixture.json")))
            throw new IllegalStateException("Missing owned visual fixture marker");
        report.add("profiles", profiles);
        NeoForge.EVENT_BUS.addListener(this::clientTick);
        NeoForge.EVENT_BUS.addListener(this::serverTick);
    }

    private void clientTick(ClientTickEvent.Post event) {
        var mc = Minecraft.getInstance();
        if (finished) return;
        try {
            if (++ticks > 5000) throw new AssertionError("Visual fixture tick budget exhausted");
            if (serverFailure != null) throw new AssertionError("Server scene failed", serverFailure);
            if (!opened && mc.screen instanceof TitleScreen && mc.getOverlay() == null) {
                opened = true;
                if (menuOnly) { initialize(mc); showOptions(mc); profile = 7; placed = true; wait = 0; return; }
                mc.createWorldOpenFlows().createFreshLevel("visual-options-review",
                        new LevelSettings("Visual options review", GameType.CREATIVE, false, Difficulty.NORMAL,
                                true, new GameRules(), WorldDataConfiguration.DEFAULT), new WorldOptions(42, false, false),
                        registries -> registries.registryOrThrow(Registries.WORLD_PRESET)
                                .get(ResourceLocation.withDefaultNamespace("flat")).createWorldDimensions(), mc.screen);
            }
            if (!placed || (!menuOnly && (mc.level == null || mc.player == null)) || mc.getOverlay() != null || busy) return;
            if (mc.screen != null && mc.screen.getClass().getName().equals("punchymoves.client.tutorial.TutorialScreen")) {
                Class.forName("punchymoves.client.tutorial.TutorialManager").getMethod("dismiss").invoke(null);
                mc.setScreen(null); wait = 0; return;
            }
            if (++wait < 60) return;
            if (branch == null) initialize(mc);
            if (torchUi) { torchUiTick(mc); return; }
            if (accessories) { accessoriesTick(mc); return; }
            if (profile == 0) { apply(mc, Map.of(), "defaults"); return; }
            if (profile <= 6) {
                if (view == 0) { capture(mc, "wide"); camera(mc, 4, 100.4, 3.5, 20); view++; return; }
                if (view == 1) { capture(mc, "stations"); camera(mc, 17.5, 102, 3.3, -5); view++; return; }
                if (view == 2) {
                    capture(mc, "chains");
                    if (profile == 5) { camera(mc, 0.5, 100, 5, 0); view++; return; }
                } else capture(mc, "mobs");
                view = 0; camera(mc, 10.5, 102, 10.5, 15);
                if (profile == 1) apply(mc, Map.of("lighting.redstonePlaced", "Refined"), "refined-world");
                else if (profile == 2) apply(mc, Map.of("lighting.redstoneHeld", "Nautilus", "workstations.crafting", "Classic3D"), "nautilus-hand");
                else if (profile == 3) {
                    var off = new LinkedHashMap<String, Object>();
                    defaults.forEach((key, value) -> { if (value instanceof Boolean) off.put(key, false); });
                    off.put("lighting.redstonePlaced", "Vanilla"); off.put("lighting.redstoneHeld", "Vanilla");
                    off.put("workstations.crafting", "Vanilla"); apply(mc, off, "native-controls");
                }
                else if (profile == 4) {
                    var selected = new ArrayList<>(mc.options.resourcePacks);
                    selected.removeIf(id -> id.equals("file/FreshAnimations_v1.11.2_1.20+.zip")
                            || id.equals("file/FA+Details-v2.4.zip"));
                    int before = selected.indexOf("file/FA+Objects-v2.1.2.zip");
                    if (before < 0) throw new AssertionError("Missing selected Fresh Objects control");
                    selected.add(before, "file/FreshAnimations_v1.11.2_1.20+.zip");
                    selected.add(before + 1, "file/FA+Details-v2.4.zip");
                    mc.options.resourcePacks.clear(); mc.options.resourcePacks.addAll(selected);
                    mc.getResourcePackRepository().setSelected(selected);
                    apply(mc, Map.of(), "fresh-base-details");
                } else if (profile == 5) apply(mc, Map.of("workstations.tabletopProps", true), "nautilus-accessories");
                else { showOptions(mc); profile++; wait = 0; }
                return;
            }
            if (profile == 7) {
                Screenshot.grab(mc.gameDirectory, "visual-options-menu.png", mc.getMainRenderTarget(), ignored -> {});
                press(mc, "Lighting"); profile++; wait = 0; return;
            }
            Screenshot.grab(mc.gameDirectory, "visual-options-lighting.png", mc.getMainRenderTarget(), ignored -> {});
            finish(mc, null);
        } catch (Throwable failure) { finish(mc, failure); }
    }

    private void initialize(Minecraft mc) throws Exception {
        cache = Class.forName("io.gitlab.jfronny.respackopts.util.MetaCache");
        branchType = Class.forName("io.gitlab.jfronny.respackopts.model.tree.ConfigBranch");
        cacheKey = cache.getMethod("getKeyByPackId", String.class)
                .invoke(null, "file/skyweave-visual-patches-0.1.0.zip");
        if (cacheKey == null) {
            var method = Arrays.stream(cache.getMethods()).filter(m -> m.getName().equals("forEach")).findFirst().orElseThrow();
            var consumer = Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{method.getParameterTypes()[0]},
                    (proxy, call, args) -> {
                        if (args != null && args.length == 2 && args[0] != null) {
                            String id = (String) cache.getMethod("getId", args[0].getClass()).invoke(null, args[0]);
                            if (id.equals("skyweaveVisuals")) cacheKey = args[0];
                        }
                        return null;
                    });
            method.invoke(null, consumer);
        }
        if (cacheKey == null) throw new AssertionError("Native Respackopts did not discover Visual Patches");
        branch = cache.getMethod("getBranch", cacheKey.getClass()).invoke(null, cacheKey);
        try (var zip = new java.util.zip.ZipFile(root.resolve("resourcepacks/skyweave-visual-patches-0.1.0.zip").toFile())) {
            schema = JsonParser.parseReader(new java.io.InputStreamReader(zip.getInputStream(zip.getEntry("respackopts.json5")))).getAsJsonObject();
        }
        collect(schema.getAsJsonObject("conf"), "");
        report.addProperty("nativeDefinitions", defaults.size());
        for (var entry : defaults.entrySet()) {
            var value = leaf(entry.getKey()).getClass().getMethod("getValue").invoke(leaf(entry.getKey()));
            if (!Objects.equals(value, entry.getValue())) throw new AssertionError("Native default differs: " + entry.getKey());
        }
        if (!menuOnly) {
            String shader = System.getProperty("skyweave_compat.probe.visualOptions.shader", "off");
            ClientHandEffectsProbe.shaders(shader); report.addProperty("shader", shader);
        }
    }

    private void collect(JsonObject node, String prefix) {
        for (var e : node.entrySet()) {
            var value = e.getValue().getAsJsonObject(); String key = prefix + e.getKey();
            if (value.has("type")) defaults.put(key, value.get("default").getAsJsonPrimitive().isBoolean()
                    ? value.get("default").getAsBoolean() : value.get("default").getAsString());
            else collect(value, key + ".");
        }
    }

    private void accessoriesTick(Minecraft mc) throws Exception {
        var combinations = List.of(Map.<String, Object>of(), Map.<String, Object>of("workstations.tabletopProps", false),
                Map.<String, Object>of("workstations.tabletopProps", false, "workstations.tabletopPencil", true),
                Map.<String, Object>of("workstations.tabletopPencil", true), Map.<String, Object>of());
        var names = List.of("default-file", "neither", "pencil-only", "both", "restored-file");
        if (profile == 0) {
            mc.getSingleplayerServer().execute(() -> mc.getSingleplayerServer().getPlayerList().getPlayers().getFirst()
                    .connection.teleport(11, 101, .5, 0, 25));
            apply(mc, combinations.getFirst(), names.getFirst()); return;
        }
        int index = profile - 1;
        cache.getMethod("load", cacheKey.getClass()).invoke(null, cacheKey);
        branch = cache.getMethod("getBranch", cacheKey.getClass()).invoke(null, cacheKey);
        for (var entry : Map.of("workstations.tabletopProps", index != 1 && index != 2,
                "workstations.tabletopPencil", index == 2 || index == 3).entrySet())
            if (!Objects.equals(leaf(entry.getKey()).getClass().getMethod("getValue").invoke(leaf(entry.getKey())), entry.getValue()))
                throw new AssertionError("Saved accessory option differs: " + entry);
        var state = Blocks.CRAFTING_TABLE.defaultBlockState();
        var model = mc.getBlockRenderer().getBlockModel(state);
        int[] counts = new int[64];
        for (int i = 0; i < counts.length; i++) {
            long seed = state.getSeed(new BlockPos(i * 37 - 1000, 64 + i % 5, i * -29 + 800));
            counts[i] = model.getQuads(state, null, net.minecraft.util.RandomSource.create(seed),
                    net.neoforged.neoforge.client.model.data.ModelData.EMPTY, null).size();
            for (var face : net.minecraft.core.Direction.values()) counts[i] += model.getQuads(state, face,
                    net.minecraft.util.RandomSource.create(seed), net.neoforged.neoforge.client.model.data.ModelData.EMPTY, null).size();
        }
        accessoryQuads.add(counts);
        var row = profiles.get(index).getAsJsonObject(); row.add("quadsAt64Seeds", new Gson().toJsonTree(counts));
        row.addProperty("nativeSavedReload", true);
        if (index == 0) report.add("delivery", workstationDelivery);
        var deliveries = new LinkedHashMap<String, int[]>();
        for (var pos : List.of(new BlockPos(2, 100, 0), new BlockPos(10, 100, 3), new BlockPos(12, 100, 3))) {
            var actual = mc.level.getBlockState(pos);
            if (!actual.is(Blocks.CRAFTING_TABLE) && !BuiltInRegistries.BLOCK.getKey(actual.getBlock()).toString().contains("crafting_table"))
                throw new AssertionError("Missing delivered workstation " + pos + ": " + actual);
            var actualModel = mc.getBlockRenderer().getBlockModel(actual);
            int[] delivered = new int[64];
            for (int i = 0; i < delivered.length; i++) {
                long seed = actual.getSeed(new BlockPos(i * 37 - 1000, 64 + i % 5, i * -29 + 800));
                delivered[i] = actualModel.getQuads(actual, null, net.minecraft.util.RandomSource.create(seed),
                        net.neoforged.neoforge.client.model.data.ModelData.EMPTY, null).size();
                for (var face : net.minecraft.core.Direction.values()) delivered[i] += actualModel.getQuads(actual, face,
                        net.minecraft.util.RandomSource.create(seed), net.neoforged.neoforge.client.model.data.ModelData.EMPTY, null).size();
            }
            deliveries.put(pos.toShortString(), delivered);
        }
        deliveredQuads.add(deliveries); row.add("deliveredModelQuads", new Gson().toJsonTree(deliveries));
        capture(mc, "accessories");
        if (profile < combinations.size()) { apply(mc, combinations.get(profile), names.get(profile)); return; }
        int fileDelta = counts[0] - accessoryQuads.get(1)[0];
        int pencilDelta = accessoryQuads.get(2)[0] - accessoryQuads.get(1)[0];
        if (fileDelta <= 0 || pencilDelta <= 0) throw new AssertionError("Accessory geometry missing");
        for (int i = 0; i < counts.length; i++) {
            int base = accessoryQuads.get(1)[i];
            if (accessoryQuads.get(0)[i] != base + fileDelta || accessoryQuads.get(2)[i] != base + pencilDelta
                    || accessoryQuads.get(3)[i] != base + fileDelta + pencilDelta || counts[i] != accessoryQuads.get(0)[i])
                throw new AssertionError("Coordinate-dependent accessory presence at seed " + i);
            for (String key : deliveries.keySet()) {
                int baseDelivered = deliveredQuads.get(1).get(key)[i];
                if (deliveredQuads.get(0).get(key)[i] != baseDelivered + fileDelta
                        || deliveredQuads.get(2).get(key)[i] != baseDelivered + pencilDelta
                        || deliveredQuads.get(3).get(key)[i] != baseDelivered + fileDelta + pencilDelta
                        || deliveries.get(key)[i] != deliveredQuads.get(0).get(key)[i])
                    throw new AssertionError("Delivered block model does not honor accessories: " + key + " seed " + i);
            }
        }
        report.addProperty("constantFileQuads", fileDelta); report.addProperty("constantPencilQuads", pencilDelta);
        // Write a genuine old-shaped native preference, without the newly introduced leaf.
        Object legacy = branchType.getMethod("clone").invoke(branch);
        Object stations = branchType.getMethod("get", String.class).invoke(legacy, "workstations");
        Object file = branchType.getMethod("get", String.class).invoke(stations, "tabletopProps");
        file.getClass().getMethod("setValue", Object.class).invoke(file, false);
        var oldValues = new LinkedHashMap<>((Map<?, ?>) branchType.getMethod("getValue").invoke(stations));
        oldValues.remove("tabletopPencil");
        branchType.getMethod("setValue", Map.class).invoke(stations, oldValues);
        var path = ((Path) cacheKey.getClass().getMethod("dataLocation").invoke(cacheKey)).toAbsolutePath().normalize();
        var save = cache.getDeclaredMethod("save", Path.class, branchType); save.setAccessible(true); save.invoke(null, path, legacy);
        cache.getMethod("load", cacheKey.getClass()).invoke(null, cacheKey);
        if (!Boolean.FALSE.equals(leaf("workstations.tabletopProps").getClass().getMethod("getValue").invoke(leaf("workstations.tabletopProps")))
                || !Boolean.FALSE.equals(leaf("workstations.tabletopPencil").getClass().getMethod("getValue").invoke(leaf("workstations.tabletopPencil"))))
            throw new AssertionError("Legacy opt-out or new pencil default lost");
        report.addProperty("legacyMissingPencilOptOut", true);
        finish(mc, null);
    }

    private void torchUiTick(Minecraft mc) throws Exception {
        if (torchStep == 0) {
            camera(mc, 10.5, 100.8, 3.5, 12); torchStep++; return;
        }
        if (torchStep == 1 || torchStep == 5 || torchStep == 9) {
            branch = cache.getMethod("getBranch", cacheKey.getClass()).invoke(null, cacheKey);
            String held = torchChoice("redstoneHeld", torchStep == 5);
            String placedChoice = torchChoice("redstonePlaced", torchStep == 5);
            for (var e : Map.of("lighting.redstoneHeld", held, "lighting.redstonePlaced", placedChoice).entrySet())
                if (!Objects.equals(leaf(e.getKey()).getClass().getMethod("getValue").invoke(leaf(e.getKey())), e.getValue()))
                    throw new AssertionError("Native UI choice did not survive save/reload: " + e);
            var row = new JsonObject(); row.addProperty("name", torchStep == 1 ? "ui-defaults" : torchStep == 5 ? "ui-changed" : "ui-restored");
            row.addProperty("heldChoice", held); row.addProperty("placedChoice", placedChoice); profiles.add(row);
            capture(mc, "torches");
            var samples = row.getAsJsonObject("resources");
            String block = samples.getAsJsonObject("models/block/redstone_torch.json").get("sha256").getAsString();
            String item = samples.getAsJsonObject("items/redstone_torch.json").get("sha256").getAsString();
            if (torchStep == 1) { originalPlaced = block; originalHeld = item; }
            else if (torchStep == 5 && (block.equals(originalPlaced) || item.equals(originalHeld))
                    || torchStep == 9 && (!block.equals(originalPlaced) || !item.equals(originalHeld)))
                throw new AssertionError("Native UI resources did not change/restore together");
            var path = ((Path) cacheKey.getClass().getMethod("dataLocation").invoke(cacheKey)).toAbsolutePath().normalize();
            if (!path.startsWith(root)) throw new AssertionError("Foreign option path");
            if (torchStep != 1) row.addProperty("persistedOptions", Files.readString(path));
            if (torchStep == 9) { finish(mc, null); return; }
            showOptions(mc); torchCategoryReady = false; torchStep++; wait = 0; return;
        }
        if (torchStep == 2 || torchStep == 6) {
            if (!torchCategoryReady) {
                report.addProperty("menuWidth", mc.screen.width); report.addProperty("menuHeight", mc.screen.height);
                press(mc, "Lighting"); torchCategoryReady = true; wait = 0; return;
            }
            selectNativeEnum(mc, "redstoneHeld", torchChoice("redstoneHeld", torchStep == 2), 96);
            selectNativeEnum(mc, "redstonePlaced", torchChoice("redstonePlaced", torchStep == 2), 121);
            Screenshot.grab(mc.gameDirectory, "torch-ui-" + torchStep + ".png", mc.getMainRenderTarget(), ignored -> {});
            nativeDone(mc); torchStep++; wait = 0; return;
        }
        if (torchStep == 3 || torchStep == 7) {
            if (mc.screen != null) nativeDone(mc);
            torchStep++; wait = 0; return;
        }
        if (torchStep == 4 || torchStep == 8) {
            if (mc.screen != null) throw new AssertionError("Native Done did not return to the world");
            busy = true; wait = 0;
            mc.reloadResourcePacks().whenComplete((ignored, failure) -> mc.execute(() -> {
                if (failure != null) { finish(mc, failure); return; }
                torchStep++; busy = false; wait = 0;
            }));
        }
    }

    private String torchChoice(String suffix, boolean alternative) {
        String value = (String) defaults.get("lighting." + suffix);
        return alternative ? (value.equals("Refined") ? "Nautilus" : "Refined") : value;
    }

    private void selectNativeEnum(Minecraft mc, String suffix, String target, int y) throws Exception {
        var states = (List<?>) mc.screen.getClass().getField("widgets").get(mc.screen);
        Object state = states.stream().filter(s -> {
            try { return ((String) s.getClass().getField("id").get(s)).endsWith(suffix); }
            catch (ReflectiveOperationException e) { throw new IllegalStateException(e); }
        }).findFirst().orElseThrow(() -> new AssertionError("Missing native option " + suffix));
        for (int attempt = 0; attempt < 4; attempt++) {
            if (Objects.equals(state.getClass().getField("cachedValue").get(state), target)) return;
            double x = mc.screen.width - 60;
            if (!mc.screen.mouseClicked(x, y, 0)) throw new AssertionError("Native enum click was not handled: " + suffix);
            mc.screen.mouseReleased(x, y, 0);
        }
        throw new AssertionError("Native enum did not reach " + target);
    }

    private void nativeDone(Minecraft mc) throws Exception {
        var done = (net.minecraft.client.gui.components.Button) mc.screen.getClass().getField("done").get(mc.screen);
        if (!done.active) throw new AssertionError("Native Done is disabled");
        done.onPress();
    }

    private Object leaf(String key) throws Exception {
        Object value = branch;
        for (var part : key.split("\\.")) value = branchType.getMethod("get", String.class).invoke(value, part);
        return Objects.requireNonNull(value, key);
    }

    private void apply(Minecraft mc, Map<String, Object> changes, String name) throws Exception {
        busy = true; wait = 0;
        mc.gui.getChat().clearMessages(true);
        boolean flat = name.equals("nautilus-hand");
        var holderType = Class.forName("fuzs.puzzleslib.api.config.v3.ConfigHolder");
        var magicType = Class.forName("fuzs.easymagic.config.ClientConfig");
        var magicHolder = Class.forName("fuzs.easymagic.EasyMagic").getField("CONFIG").get(null);
        var magicConfig = holderType.getMethod("get", Class.class).invoke(magicHolder, magicType);
        var field = magicType.getField("renderEnchantingTableContents");
        Object choice = Arrays.stream(field.getType().getEnumConstants())
                .filter(x -> ((Enum<?>) x).name().equals(flat ? "FLAT" : "FLOATING")).findFirst().orElseThrow();
        field.set(magicConfig, choice);
        var craftType = Class.forName("fuzs.visualworkbench.config.ClientConfig");
        var craftHolder = Class.forName("fuzs.visualworkbench.VisualWorkbench").getField("CONFIG").get(null);
        var craftConfig = holderType.getMethod("get", Class.class).invoke(craftHolder, craftType);
        craftType.getField("flatRendering").setBoolean(craftConfig, flat);
        for (var e : defaults.entrySet()) leaf(e.getKey()).getClass().getMethod("setValue", Object.class).invoke(leaf(e.getKey()), e.getValue());
        for (var e : changes.entrySet()) leaf(e.getKey()).getClass().getMethod("setValue", Object.class).invoke(leaf(e.getKey()), e.getValue());
        var path = ((Path) cacheKey.getClass().getMethod("dataLocation").invoke(cacheKey)).toAbsolutePath().normalize();
        if (!path.startsWith(root)) throw new AssertionError("Refusing foreign native option path: " + path);
        var save = cache.getDeclaredMethod("save", Path.class, branchType); save.setAccessible(true); save.invoke(null, path, branch);
        var current = new JsonObject(); current.addProperty("name", name); current.add("changes", new Gson().toJsonTree(changes)); profiles.add(current);
        mc.reloadResourcePacks().whenComplete((ignored, failure) -> mc.execute(() -> {
            if (failure != null) { finish(mc, failure); return; }
            profile++; busy = false; wait = 0;
        }));
    }

    private void camera(Minecraft mc, double x, double y, double z, float pitch) {
        mc.getSingleplayerServer().execute(() -> mc.getSingleplayerServer().getPlayerList().getPlayers().getFirst()
                .connection.teleport(x, y, z, 180, pitch));
        wait = 0;
    }

    private void capture(Minecraft mc, String view) throws Exception {
        var result = profiles.get(profiles.size() - 1).getAsJsonObject();
        var rm = mc.getResourceManager(); var samples = new JsonObject();
        for (var path : List.of("blockstates/crafting_table.json", "models/block/redstone_torch.json", "models/block/redstone_torch_off.json",
                "models/block/redstone_wall_torch.json", "models/block/redstone_wall_torch_off.json",
                "items/redstone_torch.json", "models/block/crafting_table.json", "models/block/chain.json", "models/block/lantern_hanging.json")) {
            var resource = rm.getResource(ResourceLocation.fromNamespaceAndPath("minecraft", path)).orElseThrow();
            try (var stream = resource.open()) {
                var item = new JsonObject(); item.addProperty("pack", resource.sourcePackId());
                item.addProperty("sha256", java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(stream.readAllBytes())));
                samples.add(path, item);
            }
        }
        result.add("resources", samples);
        var be = mc.level.getBlockEntity(new BlockPos(2, 100, 0));
        if (!(be instanceof Container inventory) || inventory.getItem(0).isEmpty()) throw new AssertionError("Client did not receive workbench contents");
        result.addProperty("clientWorkbenchClass", be.getClass().getName());
        var crafted = (ItemStack) be.getClass().getMethod("getCraftingResult").invoke(be);
        if (!crafted.is(Items.CHEST)) throw new AssertionError("Expected native chest result: " + crafted);
        result.addProperty("workbenchResult", BuiltInRegistries.ITEM.getKey(crafted.getItem()).toString());
        for (var action : List.of("break", "step", "place", "hit", "fall")) {
            var sound = mc.getSoundManager().getSoundEvent(ResourceLocation.fromNamespaceAndPath("dndecor", "large_chain_" + action));
            if (sound == null || sound.getWeight() <= 0) throw new AssertionError("Missing native chain sound " + action);
        }
        result.addProperty("chainSounds", 5);
        result.addProperty("glError", org.lwjgl.opengl.GL11C.glGetError());
        if (mc.screen != null) throw new AssertionError("Unexpected screen obscures visual capture: " + mc.screen.getClass());
        Screenshot.grab(mc.gameDirectory, "visual-" + result.get("name").getAsString() + "-" + view + ".png", mc.getMainRenderTarget(), ignored -> {});
    }

    private void press(Minecraft mc, String text) {
        // LibJF Tiny draws category rows outside Screen.children(). These are
        // observed GUI coordinates for the pinned backend and fixed fixture scale.
        if (!text.equals("Lighting") || !mc.screen.mouseClicked(mc.screen.width * .5, 121, 0))
            throw new AssertionError("Pinned native category click was not handled: " + text);
    }

    private void showOptions(Minecraft mc) throws Exception {
        var type = Class.forName("net.minecraft.client.gui.screens.packs.PackSelectionModel$Entry");
        var entry = Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{type}, (p, method, args) -> {
            if (method.getName().equals("getTitle")) return Component.literal((String) cacheKey.getClass().getMethod("displayName").invoke(cacheKey));
            throw new UnsupportedOperationException(method.getName());
        });
        var widget = Class.forName("io.gitlab.jfronny.respackopts.RespackoptsPackWidget").getConstructor().newInstance();
        widget.getClass().getMethod("onClick", type).invoke(widget, entry);
    }

    private void serverTick(ServerTickEvent.Post event) {
        if (placed || serverFailure != null || event.getServer().getPlayerCount() == 0) return;
        try {
            var server = event.getServer(); var world = server.overworld();
            world.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, server);
            world.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, server); world.setDayTime(6000);
            for (int x = -2; x < 34; x++) for (int z = -2; z < 13; z++) world.setBlock(new BlockPos(x, 99, z), Blocks.SMOOTH_STONE.defaultBlockState(), 3);
            for (var pair : List.of(Map.entry(2, Blocks.CRAFTING_TABLE), Map.entry(5, Blocks.ENCHANTING_TABLE))) {
                var pos = new BlockPos(pair.getKey(), 100, 0);
                var item = pair.getKey() == 2 ? Items.CRAFTING_TABLE : Items.ENCHANTING_TABLE;
                world.setBlock(pos, (accessories && pair.getKey() == 2 ? Blocks.CRAFTING_TABLE : ((BlockItem) item).getBlock()).defaultBlockState(), 3);
                var be = world.getBlockEntity(pos);
                if (!(be instanceof Container)) {
                    var actor = server.getPlayerList().getPlayers().getFirst();
                    actor.connection.teleport(pos.getX() + 0.5, 101, 2.5, 180, 30);
                    actor.gameMode.useItemOn(actor, world, ItemStack.EMPTY, InteractionHand.MAIN_HAND,
                            new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(pos), net.minecraft.core.Direction.UP, pos, false));
                    actor.closeContainer();
                    be = world.getBlockEntity(pos);
                }
                if (!(be instanceof Container contents)) throw new AssertionError("Expected native container: " + pair.getValue() + " / " + be);
                contents.setItem(0, new ItemStack(pair.getKey() == 2 ? Items.OAK_PLANKS : Items.DIAMOND_SWORD));
                if (pair.getKey() == 2) for (int slot = 0; slot < 9; slot++)
                    if (slot != 4) contents.setItem(slot, new ItemStack(Items.OAK_PLANKS, 16));
                if (pair.getKey() == 5) contents.setItem(1, new ItemStack(Items.LAPIS_LAZULI, 3));
                be.setChanged(); world.sendBlockUpdated(pos, world.getBlockState(pos), world.getBlockState(pos), 3);
                var actor = server.getPlayerList().getPlayers().getFirst();
                actor.connection.teleport(pos.getX() + .5, 101, 2.5, 180, 30);
                actor.gameMode.useItemOn(actor, world, ItemStack.EMPTY, InteractionHand.MAIN_HAND,
                        new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(pos), net.minecraft.core.Direction.UP, pos, false));
                actor.closeContainer();
            }
            if (accessories) {
                var input = net.minecraft.world.item.crafting.CraftingInput.of(2, 2,
                        java.util.Collections.nCopies(4, new ItemStack(Items.OAK_PLANKS)));
                var recipe = server.getRecipeManager().getRecipeFor(net.minecraft.world.item.crafting.RecipeType.CRAFTING, input, world).orElseThrow();
                var stack = recipe.value().assemble(input, world.registryAccess());
                if (!stack.is(Items.CRAFTING_TABLE)) throw new AssertionError("Native table recipe returned " + stack);
                var actor = server.getPlayerList().getPlayers().getFirst(); var floor = new BlockPos(10, 99, 3);
                actor.connection.teleport(10.5, 100, 4.5, 180, 30);
                actor.setItemInHand(InteractionHand.MAIN_HAND, stack);
                actor.gameMode.useItemOn(actor, world, stack, InteractionHand.MAIN_HAND,
                        new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(floor), net.minecraft.core.Direction.UP, floor, false));
                if (world.getBlockState(floor.above()).isAir()) throw new AssertionError("Crafted table placement failed");
                world.setBlock(new BlockPos(12, 100, 3), Blocks.CRAFTING_TABLE.defaultBlockState(), 3);
                var delivery = new JsonObject();
                delivery.addProperty("recipe", recipe.id().toString());
                delivery.addProperty("craftedPlaced", BuiltInRegistries.BLOCK.getKey(world.getBlockState(floor.above()).getBlock()).toString());
                delivery.addProperty("converted", BuiltInRegistries.BLOCK.getKey(world.getBlockState(new BlockPos(2, 100, 0)).getBlock()).toString());
                delivery.addProperty("unconverted", BuiltInRegistries.BLOCK.getKey(world.getBlockState(new BlockPos(12, 100, 3)).getBlock()).toString());
                workstationDelivery = delivery;
            }
            for (int i = 0; i < 4; i++) world.setBlock(new BlockPos(8 + i, 100, 0), Blocks.REDSTONE_TORCH.defaultBlockState().setValue(BlockStateProperties.LIT, i % 2 == 0), 3);
            for (int i = 0; i < 4; i++) {
                var pos = new BlockPos(14 + i * 2, 103, 0);
                world.setBlock(pos.above(3), Blocks.STONE.defaultBlockState(), 3);
                world.setBlock(pos.above(), Blocks.CHAIN.defaultBlockState(), 3);
                world.setBlock(pos, (i % 2 == 0 ? Blocks.LANTERN : Blocks.SOUL_LANTERN).defaultBlockState().setValue(BlockStateProperties.HANGING, true), 3);
                world.setBlock(pos.above(2), Blocks.CHAIN.defaultBlockState(), 3);
            }
            int i = 0;
            for (var material : List.of("andesite", "brass", "copper", "gold", "industrial", "iron", "netherite", "zinc")) {
                var block = BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath("dndecor", "large_" + material + "_chain"));
                if (block == Blocks.AIR) throw new AssertionError("Missing DnD material " + material);
                int row = 0;
                for (var axis : net.minecraft.core.Direction.Axis.values())
                    world.setBlock(new BlockPos(14 + i, 101, 3 + 3 * row++), block.defaultBlockState().setValue(BlockStateProperties.AXIS, axis), 3);
                i++;
            }
            for (var type : List.of(net.minecraft.world.entity.EntityType.CREEPER, net.minecraft.world.entity.EntityType.ENDERMAN)) {
                var mob = type.create(world);
                if (mob == null) throw new AssertionError("Missing native mob");
                mob.setNoAi(true); mob.setSilent(true); mob.setInvulnerable(true); mob.setNoGravity(true);
                mob.moveTo(type == net.minecraft.world.entity.EntityType.CREEPER ? 0 : 1, 100, 1, 0, 0);
                world.addFreshEntity(mob);
            }
            var player = server.getPlayerList().getPlayers().getFirst(); player.connection.teleport(10.5, 102, 10.5, 180, 15);
            player.getAbilities().flying = true; player.onUpdateAbilities();
            player.getInventory().setItem(0, new ItemStack(Items.REDSTONE_TORCH)); player.getInventory().selected = 0;
            placed = true;
        } catch (Throwable failure) { serverFailure = failure; }
    }

    private void finish(Minecraft mc, Throwable failure) {
        if (finished) return; finished = true;
        report.addProperty("complete", failure == null);
        if (failure != null) {
            report.addProperty("failure", failure.toString());
            if (serverFailure != null) report.addProperty("serverFailure", serverFailure.toString());
            var trace = new java.io.StringWriter(); failure.printStackTrace(new java.io.PrintWriter(trace));
            report.addProperty("trace", trace.toString());
        }
        try { ProbeStateFiles.write(root.resolve("visual-options-results.json"), new GsonBuilder().setPrettyPrinting().create().toJson(report)); }
        catch (Exception error) { throw new IllegalStateException(error); }
        mc.stop();
    }
}
