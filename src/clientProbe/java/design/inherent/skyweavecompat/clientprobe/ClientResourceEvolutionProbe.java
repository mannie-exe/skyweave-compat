package design.inherent.skyweavecompat.clientprobe;

import com.google.gson.*;
import java.nio.file.*;
import java.util.*;
import net.minecraft.client.*;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.*;
import net.minecraft.core.registries.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.*;
import net.minecraft.world.item.alchemy.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Real, disposable native resource scenes. Never included in the product artifact. */
final class ClientResourceEvolutionProbe {
    private record Sample(String name, ItemStack stack, boolean use, InteractionHand activeHand, Block placement) {
        Sample(String name, ItemStack stack, boolean use) { this(name, stack, use, InteractionHand.MAIN_HAND, null); }
        Sample(String name, ItemStack stack, boolean use, InteractionHand hand) { this(name, stack, use, hand, null); }
    }
    private record Equipped(String item, String slot, int index) {}
    private final Path root = FMLPaths.GAMEDIR.get().toAbsolutePath().normalize();
    private final JsonObject report = new JsonObject();
    private final JsonArray samples = new JsonArray();
    private final List<Sample> items = new ArrayList<>();
    private volatile boolean placed;
    private volatile Throwable serverFailure;
    private boolean opened, initialized, prepared, finished;
    private boolean packMenu, busy, packRoundTrip;
    private int ticks, wait, index, view;
    private List<String> sceneMobs = List.of();
    private volatile Equipped equipped;
    private volatile int useBefore, useAfter;
    private volatile int serverStackBindings;
    private volatile int placedBlocks;
    private boolean clearingEquipment;
    private JsonObject baselineAttributes;
    private static final String CURIO_HANDLER = "top.theillusivec4.curios.api.type.capability.ICuriosItemHandler";
    private volatile Set<UUID> fixtureMobs = Set.of();
    private final Map<UUID, JsonObject> renderedMobs = new HashMap<>();
    private final java.util.concurrent.ConcurrentLinkedQueue<String> nativeShots = new java.util.concurrent.ConcurrentLinkedQueue<>();
    private final Set<UUID> shotOwners = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private volatile Set<UUID> expectedShotOwners = Set.of();
    private int mobView;
    private volatile int skeletonShotsExpected;

    ClientResourceEvolutionProbe() {
        if (!Files.isRegularFile(root.resolve("rendering-review-fixture.json")))
            throw new IllegalStateException("Missing owned resource fixture marker");
        report.add("samples", samples);
        report.addProperty("control", Boolean.getBoolean("skyweave_compat.probe.resourceEvolution.control"));
        NeoForge.EVENT_BUS.addListener(this::clientTick);
        NeoForge.EVENT_BUS.addListener(this::serverTick);
        NeoForge.EVENT_BUS.addListener(this::mobRendered);
        NeoForge.EVENT_BUS.addListener(this::projectileJoined);
    }

    private void clientTick(ClientTickEvent.Post event) {
        var mc = Minecraft.getInstance();
        if (finished) return;
        try {
            if (++ticks > 12000) throw new AssertionError("Resource fixture tick budget exhausted");
            if (serverFailure != null) throw new AssertionError("Resource scene failed", serverFailure);
            if (!opened && mc.screen instanceof TitleScreen && mc.getOverlay() == null) {
                opened = true;
                mc.createWorldOpenFlows().createFreshLevel("resource-evolution-review",
                        new LevelSettings("Resource evolution review", GameType.CREATIVE, false,
                                Difficulty.NORMAL, true, new GameRules(), WorldDataConfiguration.DEFAULT),
                        new WorldOptions(42, false, false), registries -> registries.registryOrThrow(Registries.WORLD_PRESET)
                                .get(ResourceLocation.withDefaultNamespace("flat")).createWorldDimensions(), mc.screen);
            }
            if (!placed || mc.player == null || mc.level == null || mc.getOverlay() != null) return;
            if (mc.screen != null && mc.screen.getClass().getName().equals("punchymoves.client.tutorial.TutorialScreen")) {
                Class.forName("punchymoves.client.tutorial.TutorialManager").getMethod("dismiss").invoke(null);
                mc.setScreen(null); wait = 0; return;
            }
            if (busy) return;
            if (packMenu) {
                if (++wait < 30) return;
                if (!(mc.screen instanceof net.minecraft.client.gui.screens.packs.PackSelectionScreen))
                    throw new AssertionError("Native resource-pack menu did not open");
                capture(mc, "pack-selection", null);
                String label = net.minecraft.network.chat.Component.translatable("gui.done").getString();
                var done = mc.screen.children().stream().filter(widget -> widget instanceof net.minecraft.client.gui.components.Button button
                        && button.getMessage().getString().equals(label)).map(widget -> (net.minecraft.client.gui.components.Button) widget)
                        .findFirst().orElseThrow(() -> new AssertionError("Native resource-pack Done button absent"));
                if (!done.active) throw new AssertionError("Native resource-pack Done button disabled");
                packMenu = false; done.onPress(); return;
            }
            if (mc.screen != null || ++wait < 30) return;
            if (!initialized) {
                initialized = true;
                ClientHandEffectsProbe.shaders(System.getProperty("skyweave_compat.probe.resourceEvolution.shader", "off"));
                wait = 0; return;
            }
            if (!prepared) {
                prepared = true;
                inspect(mc); collectItems(); baselineAttributes = attributes(mc.player); mc.gui.getChat().clearMessages(true);
                if (equipmentFocus()) report.add("equipmentBaselineAttributes", baselineAttributes);
                camera(mc, 9, 102, 10, 180, 14); wait = 0; return;
            }
            if (!packRoundTrip) {
                packRoundTrip = true; packMenu = true; wait = 0;
                var originalResources = report.get("resources").deepCopy();
                var originalPacks = report.get("selectedPacks").deepCopy();
                var repository = mc.getResourcePackRepository();
                mc.setScreen(new net.minecraft.client.gui.screens.packs.PackSelectionScreen(repository, selected -> {
                    mc.options.updateResourcePacks(selected);
                    mc.setScreen(null); busy = true;
                    mc.reloadResourcePacks().whenComplete((ignored, failure) -> mc.execute(() -> {
                        if (failure != null) { finish(mc, failure); return; }
                        try {
                            inspect(mc);
                            if (!originalResources.equals(report.get("resources")) || !originalPacks.equals(report.get("selectedPacks")))
                                throw new AssertionError("Native pack save/reload changed selected resources");
                            var result = new JsonObject(); result.addProperty("nativeDone", true);
                            result.addProperty("selectedIdsUnchanged", true); result.addProperty("winningResourcesUnchanged", true);
                            result.addProperty("savedOptions", Files.readString(root.resolve("options.txt")).contains("resourcePacks:"));
                            report.add("nativePackRoundTrip", result); busy = false; wait = 0;
                        } catch (Throwable problem) { finish(mc, problem); }
                    }));
                }, root.resolve("resourcepacks"), net.minecraft.network.chat.Component.translatable("resourcePack.title")));
                return;
            }
            if (mobsFocus()) { mobViews(mc); return; }
            if (index == 0 && view < 4) {
                capture(mc, view == 3 ? "display-shelf" : "scene-" + view, null);
                if (view == 3) {
                    var area = new net.minecraft.world.phys.AABB(31, 99, -1, 49, 104, 6);
                    int fixed = mc.level.getEntitiesOfClass(net.minecraft.world.entity.decoration.ItemFrame.class, area).size();
                    int ground = mc.level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, area).size();
                    if (fixed != 7 || ground != 7) throw new AssertionError("Native display shelf tracking differs: " + fixed + "/" + ground);
                    report.addProperty("nativeFixedDisplays", fixed); report.addProperty("nativeGroundDisplays", ground);
                }
                if (view++ == 0) camera(mc, 2, 102, 6, 180, 20);
                else if (view == 2) camera(mc, 20, 102, 9, 180, 10);
                else if (view == 3) camera(mc, 39, 102, 8, 180, 12);
                else { index = 1; view = 0; camera(mc, 9, 101, 8, 180, 0); }
                wait = 0; return;
            }
            if (index > items.size()) {
                mc.options.keyUse.setDown(false);
                if (equipmentFocus()) {
                    if (!clearingEquipment) {
                        clearingEquipment = true; wait = 0;
                        mc.getSingleplayerServer().execute(() -> {
                            try { clearEquipment(mc.getSingleplayerServer().getPlayerList().getPlayers().getFirst()); }
                            catch (Throwable failure) { serverFailure = failure; }
                        });
                        return;
                    }
                    var finalAttributes = attributes(mc.player); report.add("equipmentClearedAttributes", finalAttributes);
                    for (String key : List.of("max_health", "attack_speed", "scale"))
                        if (Math.abs(finalAttributes.get(key).getAsDouble() - baselineAttributes.get(key).getAsDouble()) > 1e-6)
                            throw new AssertionError("Equipped attribute did not clear: " + key);
                    for (var item : items) if (isEquipped(mc.player, item.stack.getItem()))
                        throw new AssertionError("Equipment remains after native clear: " + item.name);
                }
                finish(mc, null); return;
            }
            var sample = items.get(index - 1);
            if (view == 0) {
                mc.options.setCameraType(CameraType.FIRST_PERSON);
                camera(mc, 9, 101, 8, 180, sample.placement != null ? 65 : 0);
                var encodedStack = ItemStack.CODEC.encodeStart(net.minecraft.resources.RegistryOps.create(
                        com.mojang.serialization.JsonOps.INSTANCE, mc.level.registryAccess()), sample.stack).getOrThrow();
                mc.getSingleplayerServer().execute(() -> {
                    try {
                        var player = mc.getSingleplayerServer().getPlayerList().getPlayers().getFirst();
                        var stack = ItemStack.CODEC.parse(net.minecraft.resources.RegistryOps.create(
                                com.mojang.serialization.JsonOps.INSTANCE, player.serverLevel().registryAccess()), encodedStack).getOrThrow();
                        // Dynamic holders must belong to the destination side, just as on the real native wire.
                        stack.save(player.serverLevel().registryAccess());
                        serverStackBindings++;
                        if (sample.placement != null) {
                            for (int x = 7; x <= 11; x++) for (int z = 4; z <= 9; z++) for (int y = 100; y <= 105; y++)
                                player.serverLevel().setBlock(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState(), 3);
                            placedBlocks = -1;
                        }
                        player.getInventory().selected = 0;
                        player.getInventory().setItem(0, equipmentFocus() || sample.activeHand == InteractionHand.OFF_HAND ? ItemStack.EMPTY : stack.copy());
                        player.setItemInHand(InteractionHand.OFF_HAND, equipmentFocus() ? ItemStack.EMPTY : stack.copy());
                        if (equipmentFocus()) equipped = equip(player, stack);
                        if (sample.use) {
                            useBefore = player.getStats().getValue(net.minecraft.stats.Stats.ITEM_USED.get(stack.getItem()));
                            useAfter = -1;
                        }
                        player.getFoodData().setFoodLevel(10);
                        player.containerMenu.broadcastChanges();
                    } catch (Throwable failure) { serverFailure = failure; }
                });
                view++; wait = 0; return;
            }
            if (view == 1) {
                if (equipmentFocus()) {
                    if (equipped == null || !equipped.item.equals(BuiltInRegistries.ITEM.getKey(sample.stack.getItem()).toString())
                            || !isEquipped(mc.player, sample.stack.getItem()))
                        throw new AssertionError("Client did not receive native equipped item: " + sample.name);
                    if (sample.name.equals("artifacts-crystal_heart") && mc.player.getAttributeValue(Attributes.MAX_HEALTH)
                            <= baselineAttributes.get("max_health").getAsDouble())
                        throw new AssertionError("Native Crystal Heart did not increase synchronized maximum health");
                } else if (!(sample.activeHand == InteractionHand.OFF_HAND ? mc.player.getMainHandItem().isEmpty()
                        : ItemStack.isSameItemSameComponents(mc.player.getMainHandItem(), sample.stack))
                        || !ItemStack.isSameItemSameComponents(mc.player.getOffhandItem(), sample.stack))
                    throw new AssertionError("Client inventory did not receive " + sample.name);
                capture(mc, sample.name + "-first", sample);
                mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
                view++; wait = 0; return;
            }
            if (view == 2) {
                capture(mc, sample.name + "-third", sample);
                if (sample.use) {
                    mc.options.setCameraType(CameraType.FIRST_PERSON);
                    mc.options.keyUse.setDown(true);
                    view++; wait = 22; return;
                }
            } else if (view == 3) {
                capture(mc, sample.name + "-use", sample);
                view = 4; wait = 18; return;
            } else if (view == 4) {
                capture(mc, sample.name + "-use-late", sample);
                view = 5; wait = 12; return;
            } else if (view == 5) {
                mc.options.keyUse.setDown(false);
                mc.getSingleplayerServer().execute(() -> {
                    var player = mc.getSingleplayerServer().getPlayerList().getPlayers().getFirst();
                    useAfter = player.getStats().getValue(net.minecraft.stats.Stats.ITEM_USED.get(sample.stack.getItem()));
                    if (sample.placement != null) {
                        int total = 0;
                        for (int x = 7; x <= 11; x++) for (int z = 4; z <= 9; z++) for (int y = 100; y <= 105; y++)
                            if (player.serverLevel().getBlockState(new BlockPos(x, y, z)).is(sample.placement)) total++;
                        placedBlocks = total;
                    }
                });
                view = 6; wait = 0; return;
            } else {
                if (useAfter <= useBefore) throw new AssertionError("Native item-use statistic did not advance: " + sample.name);
                if (sample.placement != null && placedBlocks <= 0)
                    throw new AssertionError("Native placement did not retain the expected block: " + sample.name);
                capture(mc, sample.name + "-use-complete", sample);
            }
            index++; view = 0; wait = 0;
        } catch (Throwable failure) { finish(mc, failure); }
    }

    private void collectItems() throws Exception {
        for (var item : List.of(Items.GLASS_BOTTLE, Items.HONEY_BOTTLE, Items.DRAGON_BREATH,
                Items.EXPERIENCE_BOTTLE, Items.OMINOUS_BOTTLE))
            items.add(new Sample(BuiltInRegistries.ITEM.getKey(item).getPath(), new ItemStack(item), item == Items.HONEY_BOTTLE));
        items.add(new Sample("water-potion", PotionContents.createItemStack(Items.POTION, Potions.WATER), true));
        items.add(new Sample("healing-potion", PotionContents.createItemStack(Items.POTION, Potions.HEALING), true));
        items.add(new Sample("swiftness-splash", PotionContents.createItemStack(Items.SPLASH_POTION, Potions.SWIFTNESS), true));
        items.add(new Sample("poison-lingering", PotionContents.createItemStack(Items.LINGERING_POTION, Potions.POISON), true));
        for (var id : List.of("create:sweet_roll", "create:builders_tea", "create:super_glue", "create:filter")) {
            var item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(id));
            if (item == Items.AIR) throw new AssertionError("Missing selected item " + id);
            items.add(new Sample(id.replace(':', '-'), new ItemStack(item), id.endsWith("sweet_roll") || id.endsWith("builders_tea")));
        }
        for (var item : BuiltInRegistries.ITEM) {
            var id = BuiltInRegistries.ITEM.getKey(item);
            if (id.getNamespace().equals("artifacts") && !id.getPath().endsWith("spawn_egg"))
                items.add(new Sample("artifacts-" + id.getPath(), new ItemStack(item), false));
        }
        for (var sample : List.copyOf(items)) if (sample.use)
            items.add(new Sample(sample.name + "-offhand", sample.stack.copy(), true, InteractionHand.OFF_HAND));
        if (items.stream().filter(i -> i.name.startsWith("artifacts-")).count() != 48)
            throw new AssertionError("Artifacts equipment registry changed");
        report.addProperty("registeredArtifactsEquipment", 48);
        String focus = System.getProperty("skyweave_compat.probe.resourceEvolution.focus", "all");
        if (focus.equals("rotations")) {
            var selected = Set.of("artifacts-cloud_in_a_bottle", "artifacts-novelty_drinking_hat", "artifacts-plastic_drinking_hat", "artifacts-snorkel");
            items.removeIf(item -> !selected.contains(item.name));
            if (items.size() != selected.size()) throw new AssertionError("Missing focused rotation item");
        } else if (focus.equals("music")) {
            items.clear();
            for (var item : BuiltInRegistries.ITEM) {
                var id = BuiltInRegistries.ITEM.getKey(item);
                if (id.getNamespace().equals("minecraft") && (id.getPath().startsWith("music_disc_") || item == Items.DISC_FRAGMENT_5))
                    items.add(new Sample(id.getPath(), new ItemStack(item), false));
            }
            if (items.size() != 20) throw new AssertionError("Native music-disc/fragment registry changed: " + items.size());
        } else if (focus.equals("equipment")) {
            var wearable = Class.forName("artifacts.item.WearableArtifactItem");
            items.removeIf(item -> !wearable.isInstance(item.stack.getItem()));
            if (items.size() != 45) throw new AssertionError("Native wearable Artifacts registry changed: " + items.size());
        } else if (focus.equals("formats")) {
            items.clear();
            for (var item : List.of(Items.APPLE, Items.BREAD, Items.COOKED_BEEF, Items.SUSPICIOUS_STEW))
                items.add(new Sample(BuiltInRegistries.ITEM.getKey(item).getPath(), new ItemStack(item), true));
            for (String id : List.of("create:sweet_roll", "create:builders_tea"))
                items.add(new Sample(id.replace(':', '-'), new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse(id))), true));
            for (var sample : List.copyOf(items))
                items.add(new Sample(sample.name + "-offhand", sample.stack.copy(), true, InteractionHand.OFF_HAND));
            for (var item : List.of(Items.BOOK, Items.WRITABLE_BOOK, Items.WRITTEN_BOOK, Items.PAINTING))
                items.add(new Sample(BuiltInRegistries.ITEM.getKey(item).getPath(), new ItemStack(item), false));
            var enchantments = Minecraft.getInstance().level.registryAccess().registryOrThrow(Registries.ENCHANTMENT);
            var mending = new ItemStack(Items.ENCHANTED_BOOK);
            mending.enchant(enchantments.getHolderOrThrow(net.minecraft.world.item.enchantment.Enchantments.MENDING), 1);
            items.add(new Sample("book-mending", mending, false));
            var sharpness = new ItemStack(Items.ENCHANTED_BOOK);
            sharpness.enchant(enchantments.getHolderOrThrow(net.minecraft.world.item.enchantment.Enchantments.SHARPNESS), 5);
            items.add(new Sample("book-sharpness5", sharpness, false));
        } else if (focus.equals("interactive")) {
            items.clear();
            for (var item : List.of(Items.TORCH, Items.SOUL_TORCH, Items.REDSTONE_TORCH, Items.NOTE_BLOCK, Items.SCULK_SENSOR)) {
                var block = ((BlockItem) item).getBlock();
                var name = BuiltInRegistries.ITEM.getKey(item).getPath();
                items.add(new Sample(name, new ItemStack(item), true, InteractionHand.MAIN_HAND, block));
                items.add(new Sample(name + "-offhand", new ItemStack(item), true, InteractionHand.OFF_HAND, block));
            }
            for (var item : List.of(Items.LANTERN, Items.SOUL_LANTERN, Items.WATER_BUCKET, Items.CHEST, Items.ENCHANTING_TABLE,
                    Items.PISTON, Items.ITEM_FRAME, Items.MACE, Items.OAK_SAPLING, Items.OAK_LEAVES, Items.TOTEM_OF_UNDYING))
                items.add(new Sample(BuiltInRegistries.ITEM.getKey(item).getPath(), new ItemStack(item), false));
        } else if (focus.equals("ores")) {
            items.clear();
            for (var item : List.of(Items.AMETHYST_CLUSTER, Items.AMETHYST_SHARD, Items.BRICK, Items.CHARCOAL, Items.COAL,
                    Items.COPPER_INGOT, Items.DIAMOND, Items.ECHO_SHARD, Items.EMERALD, Items.FLINT, Items.GOLD_INGOT,
                    Items.GOLD_NUGGET, Items.IRON_INGOT, Items.IRON_NUGGET, Items.LAPIS_LAZULI, Items.LARGE_AMETHYST_BUD,
                    Items.MEDIUM_AMETHYST_BUD, Items.NETHER_BRICK, Items.NETHERITE_INGOT, Items.NETHERITE_SCRAP,
                    Items.POINTED_DRIPSTONE, Items.QUARTZ, Items.RAW_COPPER, Items.RAW_GOLD, Items.RAW_IRON,
                    Items.REDSTONE, Items.SMALL_AMETHYST_BUD))
                items.add(new Sample(BuiltInRegistries.ITEM.getKey(item).getPath(), new ItemStack(item), false));
        } else if (focus.equals("mobs")) {
            items.clear();
        } else if (!focus.equals("all")) throw new AssertionError("Unknown resource fixture focus");
        report.addProperty("focus", focus);
        report.addProperty("heldItemCases", items.size());
    }

    private void inspect(Minecraft mc) throws Exception {
        var requiredMods = JsonParser.parseString(Files.readString(root.resolve("resource-mod-candidates.json"))).getAsJsonArray();
        var loaded = new JsonObject();
        for (var mod : net.neoforged.fml.ModList.get().getMods()) loaded.addProperty(mod.getModId(), mod.getVersion().toString());
        for (var entry : requiredMods) if (!loaded.has(entry.getAsString()))
            throw new AssertionError("Required fixture mod was not loaded: " + entry.getAsString());
        report.add("nativeLoadedMods", loaded); report.add("requiredNativeMods", requiredMods);
        if (loaded.has("interactivestuff")) {
            if (!mc.getResourcePackRepository().getSelectedIds().contains("interactivestuff:interactive_resourcepack"))
                throw new AssertionError("InteractiveStuff builtin scripts are not selected");
            var interpreter = Class.forName("omar.projects.interactivestuff.scripts.ScriptInterpreter");
            var ready = interpreter.getDeclaredField("ready"); ready.setAccessible(true);
            var runtime = interpreter.getDeclaredField("RUNTIME"); runtime.setAccessible(true);
            var state = new JsonObject(); state.addProperty("ready", ready.getBoolean(null));
            var packs = (Map<?, ?>) runtime.getType().getMethod("packs").invoke(runtime.get(null));
            state.addProperty("loadedScriptPacks", packs.size());
            state.addProperty("availableVynResources", mc.getResourceManager().listResources("scripts", id -> id.getPath().endsWith(".vyn")).size());
            var scripts = new JsonArray();
            for (var entry : packs.entrySet()) {
                var pack = entry.getValue();
                for (var script : (Collection<?>) pack.getClass().getMethod("scripts").invoke(pack)) {
                    var row = new JsonObject();
                    row.addProperty("pack", entry.getKey().toString());
                    row.addProperty("script", script.getClass().getMethod("getScriptName").invoke(script).toString());
                    row.addProperty("state", script.getClass().getMethod("getState").invoke(script).toString());
                    var failure = script.getClass().getDeclaredField("failure"); failure.setAccessible(true);
                    if (failure.get(script) instanceof Throwable problem) row.addProperty("failure", problem.toString());
                    scripts.add(row);
                }
            }
            state.add("scripts", scripts);
            report.add("nativeScriptState", state);
            var nativeCallback = interpreter.getMethod("hasItemCallbacks", ItemStack.class);
            var bridge = Class.forName("punchy.compat.InteractiveStuffCompat").getMethod("scripted", Object.class);
            var callbacks = new JsonObject();
            report.add("nativeScriptedItems", callbacks);
            boolean refinedTorchExclusion = false;
            var torchOwner = mc.getResourceManager().getResource(ResourceLocation.fromNamespaceAndPath("interactivestuff", "scripts/som.vyn"));
            if (torchOwner.isPresent()) {
                try (var reader = torchOwner.get().openAsReader()) {
                    var code = com.google.common.io.CharStreams.toString(reader).replaceAll("\\s+", "");
                    refinedTorchExclusion = code.contains("excludeScript(\"interactivestuff:interactive_resourcepack\",\"torches_and_more\")");
                    if (refinedTorchExclusion) state.addProperty("torchExclusionSource", torchOwner.get().sourcePackId());
                }
            }
            for (var item : List.of(Items.TORCH, Items.WATER_BUCKET, Items.NOTE_BLOCK, Items.SCULK_SENSOR, Items.MACE)) {
                var stack = new ItemStack(item);
                boolean original = (boolean) nativeCallback.invoke(null, stack), adapted = (boolean) bridge.invoke(null, stack);
                var row = new JsonObject(); row.addProperty("native", original); row.addProperty("punchy", adapted);
                boolean expected = item != Items.TORCH || !refinedTorchExclusion;
                row.addProperty("expected", expected);
                callbacks.add(BuiltInRegistries.ITEM.getKey(item).toString(), row);
                if (original != expected || adapted != expected) throw new AssertionError("Native/Punchy scripted item admission failed: " + item
                        + ", native=" + original + ", punchy=" + adapted);
            }
            report.add("nativeScriptedItems", callbacks);
        }
        var candidates = JsonParser.parseString(Files.readString(root.resolve("resource-candidates.json"))).getAsJsonArray();
        report.add("candidatePacks", candidates);
        for (var entry : candidates) {
            String id = "file/" + entry.getAsJsonObject().get("file").getAsString();
            if (!mc.getResourcePackRepository().getSelectedIds().contains(id))
                throw new AssertionError("Requested resource candidate was not selected: " + id);
        }
        var resources = new JsonObject();
        for (var id : List.of("minecraft:blockstates/end_portal_frame.json", "minecraft:blockstates/cave_vines.json",
                "minecraft:blockstates/cave_vines_plant.json", "minecraft:blockstates/vine.json",
                "minecraft:models/item/potion.json", "minecraft:items/music_disc_cat.json", "minecraft:optifine/cem/skeleton.jem",
                "minecraft:optifine/cem/zombie.jem", "artifacts:items/anglers_hat.json", "create:items/sweet_roll.json",
                "minecraft:items/apple.json", "minecraft:items/bread.json", "minecraft:items/suspicious_stew.json",
                "minecraft:items/enchanted_book.json", "minecraft:items/book.json", "minecraft:items/painting.json")) {
            var resource = mc.getResourceManager().getResource(ResourceLocation.parse(id));
            var row = new JsonObject(); row.addProperty("present", resource.isPresent());
            if (resource.isPresent()) try (var stream = resource.get().open()) {
                row.addProperty("pack", resource.get().sourcePackId());
                row.addProperty("sha256", HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(stream.readAllBytes())));
            }
            resources.add(id, row);
        }
        report.add("resources", resources);
        var codec = (com.mojang.serialization.Codec<?>) Class.forName("timmychips.modefiteitemdefinitions.property.type.ItemModelTypes")
                .getField("CODEC").get(null);
        var definitions = new JsonObject();
        for (var id : List.of("minecraft:items/apple.json", "minecraft:items/bread.json", "minecraft:items/suspicious_stew.json",
                "minecraft:items/enchanted_book.json", "minecraft:items/book.json", "minecraft:items/painting.json")) {
            var resource = mc.getResourceManager().getResource(ResourceLocation.parse(id));
            if (resource.isEmpty()) continue;
            try (var stream = resource.get().open()) {
                var json = JsonParser.parseReader(new java.io.InputStreamReader(stream, java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
                var decoded = codec.parse(com.mojang.serialization.JsonOps.INSTANCE, json.get("model"));
                var row = new JsonObject(); row.addProperty("source", resource.get().sourcePackId());
                row.addProperty("accepted", decoded.result().isPresent());
                decoded.error().ifPresent(error -> row.addProperty("error", error.message().substring(0, Math.min(4000, error.message().length()))));
                definitions.add(id, row);
            }
        }
        report.add("nativeDefinitionChecks", definitions);
        inspectComponentSelectors(mc, codec);
        var blockModels = new JsonArray();
        for (var block : List.of(Blocks.VINE, Blocks.CAVE_VINES, Blocks.CAVE_VINES_PLANT, Blocks.END_PORTAL_FRAME))
            for (var state : block.getStateDefinition().getPossibleStates()) {
                var model = mc.getBlockRenderer().getBlockModel(state);
                if (model == mc.getModelManager().getMissingModel()) throw new AssertionError("Missing block model: " + state);
                int quads = model.getQuads(state, null, RandomSource.create(42)).size();
                for (var face : Direction.values()) quads += model.getQuads(state, face, RandomSource.create(42)).size();
                boolean unattachedVine = block == Blocks.VINE && state.getValues().values().stream().allMatch(Boolean.FALSE::equals);
                if (quads == 0 && !unattachedVine) throw new AssertionError("Empty block model: " + state);
                var row = new JsonObject(); row.addProperty("state", state.toString()); row.addProperty("quads", quads); blockModels.add(row);
            }
        report.add("blockModels", blockModels);
        if (design.inherent.skyweavecompat.OptionalIntegrations.enabled(design.inherent.skyweavecompat.Feature.MODEL_ROTATIONS)) {
            var nativeVertices = vertices(mc, Items.CLAY_BALL);
            var convertedVertices = vertices(mc, Items.SLIME_BALL);
            if (nativeVertices.size() != convertedVertices.size() || nativeVertices.isEmpty())
                throw new AssertionError("Native/converted rotation vertex counts differ");
            float largest = 0;
            for (int i = 0; i < nativeVertices.size(); i++)
                largest = Math.max(largest, Math.abs(nativeVertices.get(i) - convertedVertices.get(i)));
            if (largest > 1e-5) throw new AssertionError("Converted geometry differs from native rotation: " + largest);
            report.addProperty("nativeRotationCoordinates", nativeVertices.size());
            report.addProperty("nativeRotationMaximumError", largest);
        }
        var packs = new JsonArray(); mc.getResourcePackRepository().getSelectedIds().forEach(packs::add); report.add("selectedPacks", packs);
        var formats = new JsonObject(); mc.getResourcePackRepository().getSelectedPacks().forEach(pack ->
                formats.addProperty(pack.getId(), pack.getCompatibility().name())); report.add("nativePackCompatibility", formats);
        var mobs = new JsonArray();
        for (var id : sceneMobs) mobs.add(id);
        report.add("sceneMobs", mobs);
    }

    private void inspectComponentSelectors(Minecraft mc, com.mojang.serialization.Codec<?> codec) throws Exception {
        var registry = mc.level.registryAccess().registryOrThrow(Registries.ENCHANTMENT);
        var book = new ItemStack(Items.ENCHANTED_BOOK);
        book.enchant(registry.getHolderOrThrow(net.minecraft.world.item.enchantment.Enchantments.MENDING), 1);
        var painting = new ItemStack(Items.PAINTING); var tag = new net.minecraft.nbt.CompoundTag();
        tag.putString("variant", "minecraft:kebab");
        painting.set(net.minecraft.core.component.DataComponents.ENTITY_DATA, net.minecraft.world.item.component.CustomData.of(tag));
        var stew = new ItemStack(Items.SUSPICIOUS_STEW);
        stew.set(net.minecraft.core.component.DataComponents.SUSPICIOUS_STEW_EFFECTS,
                new net.minecraft.world.item.component.SuspiciousStewEffects(List.of(
                        new net.minecraft.world.item.component.SuspiciousStewEffects.Entry(net.minecraft.world.effect.MobEffects.BLINDNESS, 220))));
        var apple = new ItemStack(Items.APPLE);
        apple.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, net.minecraft.network.chat.Component.literal("Plate of Apple"));
        var stacks = Map.of("minecraft:stored_enchantments", book, "minecraft:painting/variant", painting,
                "minecraft:suspicious_stew_effects", stew, "minecraft:custom_name", apple);
        var resolver = Class.forName("timmychips.modefiteitemdefinitions.property.resolver.selectcase.ComponentCase");
        var method = resolver.getMethod("getValue", ItemStack.class, LivingEntity.class, ItemDisplayContext.class,
                Class.forName("timmychips.modefiteitemdefinitions.property.type.codec.SelectDefinition$Definition"));
        var values = new JsonObject();
        for (String id : new TreeSet<>(stacks.keySet())) {
            var json = JsonParser.parseString("{\"type\":\"minecraft:select\",\"property\":\"minecraft:component\",\"cases\":[],"
                    + "\"fallback\":{\"type\":\"minecraft:model\",\"model\":\"minecraft:item/apple\"}}").getAsJsonObject();
            json.addProperty("component", id);
            Object definition = codec.parse(com.mojang.serialization.JsonOps.INSTANCE, json).getOrThrow();
            String value = (String) method.invoke(resolver.getConstructor().newInstance(), stacks.get(id), mc.player,
                    ItemDisplayContext.FIRST_PERSON_RIGHT_HAND, definition);
            var observed = new JsonObject(); observed.addProperty("kind", value == null ? "null-reference" : "string");
            if (value != null) observed.addProperty("value", value);
            values.add(id, observed);
            boolean expected = id.equals("minecraft:custom_name") ? "Plate of Apple".equals(value)
                    : id.equals("minecraft:painting/variant") ? value == null : "null".equals(value);
            if (!expected)
                throw new AssertionError("Pinned Modefite component behavior changed: " + id + " -> " + value);
        }
        report.add("nativeComponentSelectorValues", values);
    }

    private List<Float> vertices(Minecraft mc, Item item) {
        var stack = new ItemStack(item);
        var model = mc.getItemRenderer().getModel(stack, mc.level, mc.player, 42);
        if (model == mc.getModelManager().getMissingModel()) throw new AssertionError("Missing rotation control model");
        var points = new ArrayList<float[]>();
        var faces = new ArrayList<Direction>(); faces.add(null); faces.addAll(List.of(Direction.values()));
        var passes = model.getRenderPasses(stack, false);
        for (var pass : passes) for (var face : faces) for (var quad : pass.getQuads(null, face, RandomSource.create(42), net.neoforged.neoforge.client.model.data.ModelData.EMPTY, null)) {
            var data = quad.getVertices(); int stride = data.length / 4;
            for (int vertex = 0; vertex < 4; vertex++) points.add(new float[]{
                    Float.intBitsToFloat(data[vertex * stride]), Float.intBitsToFloat(data[vertex * stride + 1]),
                    Float.intBitsToFloat(data[vertex * stride + 2])});
        }
        points.sort((a, b) -> { for (int i = 0; i < 3; i++) { int n = Float.compare(a[i], b[i]); if (n != 0) return n; } return 0; });
        var result = new ArrayList<Float>();
        for (var point : points) for (float value : point) result.add(value);
        var summary = new JsonObject(); summary.addProperty("modelClass", model.getClass().getName());
        summary.addProperty("passes", passes.size()); summary.addProperty("coordinates", result.size());
        report.add("rotationModel/" + BuiltInRegistries.ITEM.getKey(item), summary);
        return result;
    }

    private void capture(Minecraft mc, String name, Sample sample) throws Exception {
        mc.getToasts().clear(); mc.gui.getChat().clearMessages(true);
        var row = new JsonObject(); row.addProperty("name", name); row.addProperty("tick", ticks);
        row.addProperty("view", mc.options.getCameraType().name()); row.addProperty("using", mc.player.isUsingItem());
        if (sample != null) {
            row.addProperty("item", BuiltInRegistries.ITEM.getKey(sample.stack.getItem()).toString());
            row.addProperty("useAnimation", sample.stack.getUseAnimation().name());
            row.addProperty("main", BuiltInRegistries.ITEM.getKey(mc.player.getMainHandItem().getItem()).toString());
            row.addProperty("offhand", BuiltInRegistries.ITEM.getKey(mc.player.getOffhandItem().getItem()).toString());
            if (sample.use) {
                row.addProperty("actionHand", sample.activeHand.name());
                if (mc.player.isUsingItem()) {
                    row.addProperty("observedUsingHand", mc.player.getUsedItemHand().name());
                    if (mc.player.getUsedItemHand() != sample.activeHand)
                        throw new AssertionError("Wrong native action hand: " + sample.name);
                }
                row.addProperty("nativeUsedBefore", useBefore); row.addProperty("nativeUsedAfter", useAfter);
                row.addProperty("foodLevel", mc.player.getFoodData().getFoodLevel());
                if (sample.placement != null) {
                    row.addProperty("placedBlock", BuiltInRegistries.BLOCK.getKey(sample.placement).toString());
                    row.addProperty("nativePlacedCount", placedBlocks);
                }
            }
            if (equipmentFocus()) {
                row.addProperty("equipmentSlot", equipped.slot); row.addProperty("equipmentIndex", equipped.index);
                row.addProperty("nativeEquipped", isEquipped(mc.player, sample.stack.getItem()));
                row.add("attributes", attributes(mc.player));
            }
        }
        row.addProperty("glError", org.lwjgl.opengl.GL11C.glGetError());
        samples.add(row);
        Screenshot.grab(mc.gameDirectory, "resource-" + name + ".png", mc.getMainRenderTarget(), ignored -> {});
    }

    private boolean equipmentFocus() { return System.getProperty("skyweave_compat.probe.resourceEvolution.focus", "all").equals("equipment"); }
    private boolean mobsFocus() { return System.getProperty("skyweave_compat.probe.resourceEvolution.focus", "all").equals("mobs"); }

    private void mobRendered(net.neoforged.neoforge.client.event.RenderLivingEvent.Post<?, ?> event) {
        if (!mobsFocus() || !fixtureMobs.contains(event.getEntity().getUUID())) return;
        var mob = event.getEntity(); var model = event.getRenderer().getModel();
        var row = new JsonObject(); row.addProperty("id", BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType()).toString());
        row.addProperty("uuid", mob.getUUID().toString()); row.addProperty("baby", mob.isBaby());
        row.addProperty("usingItem", mob.isUsingItem()); row.addProperty("renderer", event.getRenderer().getClass().getName());
        row.addProperty("model", model.getClass().getName());
        var parts = new JsonObject();
        var quivers = new JsonObject();
        try {
            for (var field : model.getClass().getFields())
                if (net.minecraft.client.model.geom.ModelPart.class.isAssignableFrom(field.getType())) {
                    var part = field.get(model);
                    if (part != null) {
                        parts.addProperty(field.getName(), part.getClass().getName());
                        if (field.getName().equals("body")) findQuivers((net.minecraft.client.model.geom.ModelPart) part, quivers, "body", 0);
                    }
                }
        } catch (IllegalAccessException failure) { throw new IllegalStateException(failure); }
        row.add("partClasses", parts); row.add("quiverParts", quivers); renderedMobs.put(mob.getUUID(), row);
    }

    private void findQuivers(net.minecraft.client.model.geom.ModelPart part, JsonObject output, String path, int depth) {
        if (depth > 32) throw new AssertionError("Unbounded model-part tree in native mob fixture");
        try {
            var field = net.minecraft.client.model.geom.ModelPart.class.getDeclaredField("children"); field.setAccessible(true);
            var children = (Map<?, ?>) field.get(part);
            for (var entry : children.entrySet()) if (entry.getValue() instanceof net.minecraft.client.model.geom.ModelPart child) {
                String name = path + "/" + entry.getKey();
                if (String.valueOf(entry.getKey()).toLowerCase(Locale.ROOT).contains("quiver")) {
                    var value = new JsonObject(); value.addProperty("visible", child.visible); value.addProperty("skipDraw", child.skipDraw);
                    value.addProperty("parts", child.getAllParts().count()); output.add(name, value);
                }
                findQuivers(child, output, name, depth + 1);
            }
        } catch (ReflectiveOperationException failure) { throw new IllegalStateException(failure); }
    }

    private void projectileJoined(net.neoforged.neoforge.event.entity.EntityJoinLevelEvent event) {
        if (mobsFocus() && !event.getLevel().isClientSide() && event.getEntity() instanceof net.minecraft.world.entity.projectile.Projectile projectile
                && projectile.getOwner() != null && fixtureMobs.contains(projectile.getOwner().getUUID()))
        {
            nativeShots.add(BuiltInRegistries.ENTITY_TYPE.getKey(projectile.getType()).toString());
            shotOwners.add(projectile.getOwner().getUUID());
        }
    }

    private void mobViews(Minecraft mc) throws Exception {
        if (mobView == 0) { camera(mc, 22, 102, 12, 180, 8); mobView++; wait = 0; return; }
        String name = switch (mobView) {
            case 1 -> "mobs-idle-front"; case 2 -> "mobs-idle-rear"; case 3 -> "mobs-bow-front";
            case 4 -> "mobs-bow-rear"; case 5 -> "mobs-after-native-shot"; default -> "mobs-children";
        };
        capture(mc, name, null);
        var states = new JsonArray(); renderedMobs.entrySet().stream().sorted(Map.Entry.comparingByKey())
                .forEach(entry -> states.add(entry.getValue().deepCopy()));
        report.add(name, states);
        if ((mobView == 3 || mobView == 4) && renderedMobs.values().stream().noneMatch(row -> row.get("usingItem").getAsBoolean()))
            throw new AssertionError("Native bow-use state did not reach rendered mobs");
        if (mobView == 1 || mobView == 3) camera(mc, 18.3, 101.4, -4, 0, 0);
        else if (mobView == 2 || mobView == 4) {
            int phase = mobView;
            mc.getSingleplayerServer().execute(() -> {
                try {
                    var world = mc.getSingleplayerServer().overworld();
                    var target = new net.minecraft.world.entity.decoration.ArmorStand(world, 22, 100, 28);
                    target.setNoGravity(true); target.setInvulnerable(true);
                    int count = 0; var expected = new HashSet<UUID>();
                    for (UUID id : fixtureMobs) if (world.getEntity(id) instanceof net.minecraft.world.entity.monster.AbstractSkeleton skeleton) {
                        if (phase == 2) { skeleton.setAggressive(true); skeleton.startUsingItem(InteractionHand.MAIN_HAND); }
                        else { skeleton.performRangedAttack(target, 1); skeleton.stopUsingItem(); skeleton.setAggressive(false); count++; expected.add(id); }
                    }
                    if (phase == 4) { skeletonShotsExpected = count; expectedShotOwners = Set.copyOf(expected); }
                } catch (Throwable failure) { serverFailure = failure; }
            });
            camera(mc, 22, 102, 12, 180, 8);
        } else if (mobView == 5) camera(mc, 22, 101, -5, 0, 5);
        else {
            if (!renderedMobs.keySet().containsAll(fixtureMobs)) throw new AssertionError("Not all fixture mobs reached native RenderLiving.Post");
            if (skeletonShotsExpected <= 0 || !shotOwners.containsAll(expectedShotOwners))
                throw new AssertionError("Native skeleton projectile count differs: " + nativeShots.size() + "/" + skeletonShotsExpected);
            var shots = new JsonArray(); nativeShots.forEach(shots::add); report.add("nativeSkeletonProjectiles", shots);
            report.addProperty("nativeSkeletonAttackOwners", skeletonShotsExpected);
            report.addProperty("renderedFixtureMobs", renderedMobs.size()); finish(mc, null); return;
        }
        mobView++; wait = 0;
    }

    private Object curios(LivingEntity wearer) throws Exception {
        var api = Class.forName("top.theillusivec4.curios.api.CuriosApi");
        var handler = (Optional<?>) api.getMethod("getCuriosInventory", LivingEntity.class).invoke(null, wearer);
        return handler.orElseThrow(() -> new AssertionError("Native Curios inventory absent"));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> nativeSlots(Object handler) throws Exception {
        return (Map<String, Object>) Class.forName(CURIO_HANDLER).getMethod("getCurios").invoke(handler);
    }

    private net.neoforged.neoforge.items.IItemHandlerModifiable stacks(Object handler) throws Exception {
        return (net.neoforged.neoforge.items.IItemHandlerModifiable) Class.forName("top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler")
                .getMethod("getStacks").invoke(handler);
    }

    private void clearEquipment(LivingEntity wearer) throws Exception {
        var handler = curios(wearer);
        var setter = Class.forName(CURIO_HANDLER).getMethod("setEquippedCurio", String.class, int.class, ItemStack.class);
        for (var entry : nativeSlots(handler).entrySet()) {
            var inventory = stacks(entry.getValue());
            for (int slot = 0; slot < inventory.getSlots(); slot++) setter.invoke(handler, entry.getKey(), slot, ItemStack.EMPTY);
        }
    }

    private Equipped equip(LivingEntity wearer, ItemStack stack) throws Exception {
        clearEquipment(wearer);
        var handler = curios(wearer);
        var eligible = (Map<?, ?>) Class.forName("top.theillusivec4.curios.api.CuriosApi")
                .getMethod("getItemStackSlots", ItemStack.class, LivingEntity.class).invoke(null, stack, wearer);
        var slots = nativeSlots(handler);
        for (String name : new TreeSet<>(slots.keySet())) {
            if (!eligible.containsKey(name)) continue;
            var inventory = stacks(slots.get(name));
            for (int index = 0; index < inventory.getSlots(); index++) if (inventory.isItemValid(index, stack)) {
                Class.forName(CURIO_HANDLER).getMethod("setEquippedCurio", String.class, int.class, ItemStack.class).invoke(handler, name, index, stack.copy());
                return new Equipped(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(), name, index);
            }
        }
        throw new AssertionError("No native valid slot for " + BuiltInRegistries.ITEM.getKey(stack.getItem()) + "; eligible=" + eligible.keySet());
    }

    private boolean isEquipped(LivingEntity wearer, Item item) throws Exception {
        return (boolean) Class.forName(CURIO_HANDLER).getMethod("isEquipped", Item.class).invoke(curios(wearer), item);
    }

    private JsonObject attributes(LivingEntity wearer) {
        var result = new JsonObject();
        result.addProperty("max_health", wearer.getAttributeValue(Attributes.MAX_HEALTH));
        result.addProperty("attack_speed", wearer.getAttributeValue(Attributes.ATTACK_SPEED));
        result.addProperty("movement_speed", wearer.getAttributeValue(Attributes.MOVEMENT_SPEED));
        result.addProperty("scale", wearer.getAttributeValue(Attributes.SCALE));
        result.addProperty("luck", wearer.getAttributeValue(Attributes.LUCK));
        return result;
    }

    private void camera(Minecraft mc, double x, double y, double z, float yaw, float pitch) {
        mc.getSingleplayerServer().execute(() -> mc.getSingleplayerServer().getPlayerList().getPlayers().getFirst()
                .connection.teleport(x, y, z, yaw, pitch));
    }

    private void serverTick(ServerTickEvent.Post event) {
        if (placed || serverFailure != null || event.getServer().getPlayerCount() == 0) return;
        try {
            var server = event.getServer(); var world = server.overworld();
            world.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, server);
            world.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, server); world.setDayTime(6000);
            world.getGameRules().getRule(GameRules.RULE_RANDOMTICKING).set(0, server);
            for (int x = -3; x < 50; x++) for (int z = -3; z < 13; z++)
                world.setBlock(new BlockPos(x, 99, z), Blocks.SMOOTH_STONE.defaultBlockState(), 3);
            var display = List.of(new ItemStack(Items.APPLE), new ItemStack(Items.MUSIC_DISC_CAT),
                    PotionContents.createItemStack(Items.POTION, Potions.HEALING), new ItemStack(Items.BOOK),
                    new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse("artifacts:cloud_in_a_bottle"))),
                    new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse("artifacts:plastic_drinking_hat"))),
                    new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse("create:sweet_roll"))));
            for (int i = 0; i < display.size(); i++) {
                var position = new BlockPos(32 + i * 2, 101, 1);
                world.setBlock(position.north(), Blocks.SMOOTH_STONE.defaultBlockState(), 3);
                var frame = new net.minecraft.world.entity.decoration.ItemFrame(world, position, Direction.SOUTH);
                frame.setItem(display.get(i).copy()); world.addFreshEntity(frame);
                var dropped = new net.minecraft.world.entity.item.ItemEntity(world, position.getX() + .5, 100.3, 4, display.get(i).copy());
                dropped.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
                dropped.setNoGravity(true); dropped.setInvulnerable(true); dropped.setNeverPickUp(); dropped.setUnlimitedLifetime();
                world.addFreshEntity(dropped);
            }
            for (int x = 0; x < 8; x++) {
                world.setBlock(new BlockPos(x, 100, -1), Blocks.STONE.defaultBlockState(), 3);
                world.setBlock(new BlockPos(x, 100, 0), Blocks.VINE.defaultBlockState().setValue(BlockStateProperties.NORTH, true), 3);
                world.setBlock(new BlockPos(x, 104, 0), Blocks.STONE.defaultBlockState(), 3);
                world.setBlock(new BlockPos(x, 103, 0), Blocks.CAVE_VINES_PLANT.defaultBlockState().setValue(BlockStateProperties.BERRIES, x % 2 == 0), 3);
                world.setBlock(new BlockPos(x, 102, 0), Blocks.CAVE_VINES.defaultBlockState().setValue(BlockStateProperties.BERRIES, x % 2 != 0), 3);
            }
            world.setBlock(new BlockPos(10, 100, 0), Blocks.END_PORTAL_FRAME.defaultBlockState(), 3);
            world.setBlock(new BlockPos(12, 100, 0), Blocks.END_PORTAL_FRAME.defaultBlockState().setValue(BlockStateProperties.EYE, true), 3);
            var types = new ArrayList<EntityType<?>>();
            types.addAll(List.of(EntityType.SKELETON, EntityType.STRAY, EntityType.BOGGED, EntityType.ZOMBIE, EntityType.DROWNED, EntityType.HUSK));
            for (var type : BuiltInRegistries.ENTITY_TYPE)
                if (BuiltInRegistries.ENTITY_TYPE.getKey(type).getNamespace().equals("variantsandventures")) types.add(type);
            var added = new ArrayList<String>(); var ids = new HashSet<UUID>(); int n = 0;
            for (var type : types) {
                var entity = type.create(world);
                if (!(entity instanceof Mob mob)) continue;
                mob.setUUID(UUID.nameUUIDFromBytes(("resource-evolution/" + BuiltInRegistries.ENTITY_TYPE.getKey(type)).getBytes(java.nio.charset.StandardCharsets.UTF_8)));
                mob.setNoAi(true); mob.setSilent(true); mob.setInvulnerable(true); mob.setPersistenceRequired();
                var helmet = new ItemStack(Items.IRON_HELMET);
                helmet.set(net.minecraft.core.component.DataComponents.UNBREAKABLE, new net.minecraft.world.item.component.Unbreakable(false));
                mob.setItemSlot(EquipmentSlot.HEAD, helmet);
                if (mob instanceof net.minecraft.world.entity.monster.AbstractSkeleton)
                    mob.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
                mob.moveTo(16 + n % 6 * 2.2, 100, n / 6 * 3, 0, 0);
                world.addFreshEntity(mob); ids.add(mob.getUUID()); added.add(BuiltInRegistries.ENTITY_TYPE.getKey(type).toString()); n++;
            }
            if (mobsFocus()) for (var type : List.of(EntityType.ZOMBIE, EntityType.HUSK, EntityType.DROWNED)) {
                var mob = (net.minecraft.world.entity.monster.Zombie) type.create(world);
                mob.setUUID(UUID.nameUUIDFromBytes(("resource-child/" + BuiltInRegistries.ENTITY_TYPE.getKey(type)).getBytes(java.nio.charset.StandardCharsets.UTF_8)));
                mob.setBaby(true); mob.setNoAi(true); mob.setSilent(true); mob.setInvulnerable(true); mob.setPersistenceRequired();
                var helmet = new ItemStack(Items.IRON_HELMET);
                helmet.set(net.minecraft.core.component.DataComponents.UNBREAKABLE, new net.minecraft.world.item.component.Unbreakable(false));
                mob.setItemSlot(EquipmentSlot.HEAD, helmet); mob.moveTo(16 + n % 6 * 2.2, 100, n / 6 * 3, 0, 0);
                world.addFreshEntity(mob); ids.add(mob.getUUID()); added.add(BuiltInRegistries.ENTITY_TYPE.getKey(type) + "/child"); n++;
            }
            fixtureMobs = Set.copyOf(ids);
            sceneMobs = List.copyOf(added);
            var actor = server.getPlayerList().getPlayers().getFirst();
            actor.connection.teleport(9, 102, 10, 180, 14);
            actor.getAbilities().flying = true; actor.onUpdateAbilities();
            placed = true;
        } catch (Throwable failure) { serverFailure = failure; }
    }

    private void finish(Minecraft mc, Throwable failure) {
        if (finished) return; finished = true;
        report.addProperty("nativeServerRegistryBindings", serverStackBindings);
        report.addProperty("complete", failure == null);
        if (failure != null) { report.addProperty("failure", failure.toString()); failure.printStackTrace(); }
        try { Files.writeString(root.resolve("resource-evolution-results.json"), new GsonBuilder().setPrettyPrinting().create().toJson(report)); }
        catch (Exception problem) { throw new RuntimeException(problem); }
        mc.stop();
    }
}
