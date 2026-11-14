package design.inherent.skyweavecompat.clientprobe;

import com.google.gson.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.*;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;
import java.nio.file.*;
import java.lang.reflect.*;

/** Native inventory/map observations in an explicitly marked disposable profile; no production entry point. */
final class ClientMiscProbe {
    private final Path root = FMLPaths.GAMEDIR.get();
    private boolean opened;
    private int ticks, sequence = -1;
    private String error;
    private java.util.concurrent.CompletableFuture<Void> resourceReload;
    ClientMiscProbe() {
        if (!Files.isRegularFile(root.resolve("misc-review-fixture.json")))
            throw new IllegalStateException("Misc probe requires its disposable-profile marker");
        NeoForge.EVENT_BUS.addListener(this::tick);
        if(Boolean.getBoolean("skyweave_compat.probe.configuration"))new ClientConfigurationProbe();
        if (Boolean.getBoolean("skyweave_compat.probe.wayfinder")) WayfinderFixture.register();
    }
    private void tick(ClientTickEvent.Post event) {
        var mc = Minecraft.getInstance();
        if (++ticks % 5 != 0 || mc.getOverlay() != null) return;
        try {
            if (!opened && mc.screen instanceof TitleScreen) {
                opened = true;
                // Keep owned fixture work moving when another agent temporarily uses the desktop.
                mc.options.pauseOnLostFocus = false;
                String remote = System.getProperty("skyweave_compat.probe.misc.server");
                if (remote != null) {
                    if (!remote.matches("127\\.0\\.0\\.1:[0-9]{1,5}")) throw new IllegalArgumentException("Expected loopback fixture server");
                    var address = net.minecraft.client.multiplayer.resolver.ServerAddress.parseString(remote);
                    if (address.getPort()<1 || address.getPort()>65535) throw new IllegalArgumentException("Invalid fixture port");
                    net.minecraft.client.gui.screens.ConnectScreen.startConnecting(mc.screen,mc,address,
                            new net.minecraft.client.multiplayer.ServerData("Wayfinder loopback",remote,
                                    net.minecraft.client.multiplayer.ServerData.Type.OTHER),false,null);
                    return;
                }
                boolean skyweave = Boolean.getBoolean("skyweave_compat.probe.misc.skyweave");
                String world = skyweave ? "misc-spawn-review" : "misc-review";
                if (Files.isDirectory(root.resolve("saves/" + world)))
                    mc.createWorldOpenFlows().openWorld(world, () -> mc.setScreen(new TitleScreen()));
                else mc.createWorldOpenFlows().createFreshLevel(world,
                        new LevelSettings("Misc review", GameType.CREATIVE, false, Difficulty.PEACEFUL, true,
                                new GameRules(), WorldDataConfiguration.DEFAULT), new WorldOptions(42, false, false),
                        registries -> registries.registryOrThrow(Registries.WORLD_PRESET)
                                .get(ResourceLocation.parse(skyweave ? "skyweave_compat:islands_v1" : "minecraft:flat"))
                                .createWorldDimensions(), mc.screen);
            }
            var control = root.resolve("misc-review-control.json");
            if (mc.player != null && Files.isRegularFile(control) && Files.size(control) < 16384) {
                var command = JsonParser.parseString(Files.readString(control)).getAsJsonObject();
                int next = command.get("sequence").getAsInt();
                if (next != sequence) { sequence = next; error = null; apply(mc, command); }
            }
        } catch (Exception failure) { error = failure.toString(); failure.printStackTrace(); }
        try { Files.writeString(root.resolve("misc-review-state.json"), new GsonBuilder().setPrettyPrinting().create().toJson(state(mc))); }
        catch (Exception failure) { throw new IllegalStateException("Cannot write owned misc observations", failure); }
    }
    private void apply(Minecraft mc, JsonObject command) throws Exception {
        switch (command.get("operation").getAsString()) {
            case "wayfinder" -> WayfinderFixture.apply(mc,command);
            case "state" -> { }
            case "spawn-snapshot" -> {
                var server = mc.getSingleplayerServer();
                if (server == null) throw new IllegalStateException("Expected owned integrated fixture");
                var playerId = mc.player.getUUID();
                server.execute(() -> {
                    try {
                        var level = server.overworld(); var player = server.getPlayerList().getPlayer(playerId);
                        var generator = (design.inherent.skyweavecompat.terrain.GeologicalGenerator)level.getChunkSource().getGenerator();
                        var field = generator.skyweave$field(level.getChunkSource().randomState());
                        var settings = ((org.sathrek.sky_archipelago.worldgen.generator.core.SkyIslandChunkGenerator)level.getChunkSource().getGenerator())
                                .resolvedSkyIslandSettings();
                        var spawn = level.getSharedSpawnPos();
                        var host = field.sampleDominantIslandDescriptor(spawn.getX(), spawn.getZ(), settings);
                        var facts = new JsonObject();
                        facts.addProperty("spawn", spawn.toShortString());
                        facts.addProperty("family", host == null ? "missing" : host.family().name());
                        facts.addProperty("support", level.getBlockState(spawn.below()).isFaceSturdy(level, spawn.below(), net.minecraft.core.Direction.UP));
                        facts.addProperty("dryArrival", level.getFluidState(player.blockPosition()).isEmpty());
                        facts.addProperty("arrival", player.blockPosition().toShortString());
                        facts.addProperty("netherDenied", design.inherent.skyweavecompat.access.DimensionAccess.denied(server, Level.NETHER));
                        facts.addProperty("selectionSaved", level.getDataStorage().computeIfAbsent(
                                design.inherent.skyweavecompat.terrain.InitialSpawnState.FACTORY,
                                design.inherent.skyweavecompat.terrain.InitialSpawnState.ID).selected());
                        Files.writeString(root.resolve("misc-review-spawn.json"), facts.toString());
                    } catch (Exception failure) { throw new IllegalStateException("Integrated spawn observation failed", failure); }
                });
            }
            case "resource-reload" -> resourceReload = mc.reloadResourcePacks();
            case "reconnect" -> {
                opened = false;
                // Match PauseScreen's integrated-server stop before disconnect's wait loop.
                var server = mc.getSingleplayerServer();
                if (server != null) server.halt(false);
                mc.disconnect(new TitleScreen());
            }
            case "quest-open" -> {
                var code = command.get("id").getAsString();
                if (!code.matches("[0-7][0-9A-F]{15}")) throw new IllegalArgumentException("Expected a positive native quest ID");
                Class.forName("dev.ftb.mods.ftbquests.client.ClientQuestFile").getMethod("openBookToQuestObject", long.class)
                        .invoke(null, Long.parseLong(code, 16));
            }
            case "fixture-item" -> {
                var server = mc.getSingleplayerServer(); var playerId = mc.player.getUUID();
                var item = ResourceLocation.parse(command.get("item").getAsString());
                int count = command.get("count").getAsInt();
                if (server == null || count < 0 || count > 64) throw new IllegalArgumentException("Owned item fixture requires0..64");
                server.execute(() -> {
                    var player = server.getPlayerList().getPlayer(playerId);
                    var source = player.createCommandSourceStack().withPermission(4);
                    server.getCommands().performPrefixedCommand(source, count == 0 ? "clear @s " + item : "give @s " + item + " " + count);
                });
            }
            case "survey-point" -> {
                int x = command.get("x").getAsInt(), z = command.get("z").getAsInt();
                if (Math.abs((long)x) > 4096 || Math.abs((long)z) > 4096)
                    throw new IllegalArgumentException("Survey fixture coordinates exceed4096");
                var server = mc.getSingleplayerServer(); var id = mc.player.getUUID();
                if (server == null) throw new IllegalStateException("Expected owned integrated fixture");
                server.execute(() -> server.getPlayerList().getPlayer(id).teleportTo(x + .5, -58, z + .5));
            }
            case "inventory" -> mc.setScreen(new InventoryScreen(mc.player));
            case "chest" -> {
                var server = mc.getSingleplayerServer(); var id = mc.player.getUUID();
                int seed = command.has("seed") ? command.get("seed").getAsInt() : -1;
                if (seed < -1 || seed > 64) throw new IllegalArgumentException("Chest seed must be0..64 or absent");
                server.execute(() -> {
                    var player = server.getPlayerList().getPlayer(id); var level = player.serverLevel();
                    var position = player.blockPosition().offset(2, 0, 0);
                    level.setBlockAndUpdate(position, net.minecraft.world.level.block.Blocks.CHEST.defaultBlockState());
                    var chest = (net.minecraft.world.level.block.entity.ChestBlockEntity)level.getBlockEntity(position);
                    if (seed >= 0) chest.setItem(0, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND, seed));
                    player.openMenu(chest);
                });
            }
            case "map" -> mc.setScreen((Screen)Class.forName("garden.hestia.hoofprint.HoofprintScreen").getConstructor().newInstance());
            case "close" -> { if (mc.screen != null) mc.screen.onClose(); }
            case "back" -> {
                if (mc.screen == null || !mc.screen.keyPressed(256, 0, 0)) throw new IllegalStateException("Screen refused Escape");
            }
            case "sidebar" -> {
                var type = Class.forName("dev.ftb.mods.ftblibrary.sidebar.SidebarButtonManager");
                var manager = type.getField("INSTANCE").get(null);
                var button = (java.util.Optional<?>)type.getMethod("getButton", ResourceLocation.class)
                        .invoke(manager, ResourceLocation.parse(command.get("id").getAsString()));
                var selected = button.orElseThrow(); selected.getClass().getMethod("clickButton", boolean.class).invoke(selected, false);
            }
            case "scroll" -> mc.screen.mouseScrolled(command.get("x").getAsDouble(), command.get("y").getAsDouble(),
                    command.has("horizontal") ? command.get("horizontal").getAsDouble() : 0, command.get("delta").getAsDouble());
            case "click" -> {
                double x = command.get("x").getAsDouble(), y = command.get("y").getAsDouble();
                int button = command.get("button").getAsInt();
                mc.screen.mouseMoved(x, y); mc.screen.mouseClicked(x, y, button); mc.screen.mouseReleased(x, y, button);
            }
            case "key" -> mc.screen.keyPressed(command.get("key").getAsInt(), 0, 0);
            case "text" -> {
                for (char character : command.get("text").getAsString().toCharArray()) mc.screen.charTyped(character, 0);
            }
            case "quest-snapshot" -> {
                var type = Class.forName("dev.ftb.mods.ftbquests.client.ClientQuestFile");
                var file = type.getField("INSTANCE").get(null);
                var team = type.getField("selfTeamData").get(file);
                var questType = Class.forName("dev.ftb.mods.ftbquests.quest.QuestObject");
                var entries = (java.util.Collection<?>)type.getMethod("getAllObjects").invoke(file);
                var snapshot = new JsonArray();
                for (var entry : entries) {
                    var row = new JsonObject(); var objectType = entry.getClass();
                    row.addProperty("class", objectType.getName());
                    row.addProperty("id", (String)objectType.getMethod("getCodeString").invoke(entry));
                    row.addProperty("title", ((Component)objectType.getMethod("getTitle").invoke(entry)).getString());
                    if (team != null && questType.isInstance(entry))
                        row.addProperty("complete", (Boolean)team.getClass().getMethod("isCompleted", questType).invoke(team, entry));
                    if (team != null && objectType.getName().contains(".quest.task."))
                        row.addProperty("progress", (Long)team.getClass().getMethod("getProgress", long.class)
                                .invoke(team, Long.parseLong(row.get("id").getAsString(), 16)));
                    try { row.addProperty("progressionMode", objectType.getMethod("getProgressionMode").invoke(entry).toString()); }
                    catch (NoSuchMethodException notAQuestOrChapter) { }
                    var tag = new net.minecraft.nbt.CompoundTag();
                    objectType.getMethod("writeData", net.minecraft.nbt.CompoundTag.class, net.minecraft.core.HolderLookup.Provider.class)
                            .invoke(entry, tag, mc.level.registryAccess());
                    row.addProperty("native", tag.toString()); snapshot.add(row);
                }
                Files.writeString(root.resolve("misc-review-quests.json"), new GsonBuilder().setPrettyPrinting().create().toJson(snapshot));
            }
            case "snbt-check" -> {
                var codec = Class.forName("dev.ftb.mods.ftblibrary.snbt.SNBT");
                var read = codec.getMethod("readLines", java.util.List.class);
                var tag = (net.minecraft.nbt.CompoundTag)read.invoke(null,
                        java.util.Arrays.asList(command.get("native").getAsString().split("\\R", -1)));
                if (tag == null) throw new IllegalStateException("FTB rejected emitted SNBT");
                var lines = codec.getMethod("writeLines", net.minecraft.nbt.CompoundTag.class).invoke(null, tag);
                var again = read.invoke(null, lines);
                var proof = new JsonObject(); proof.addProperty("text", tag.getString("text"));
                proof.addProperty("count", tag.getLong("count")); proof.addProperty("countType", tag.get("count").getId());
                proof.addProperty("enabled", tag.getBoolean("enabled")); proof.addProperty("nativeRoundTrip", tag.equals(again));
                Files.writeString(root.resolve("misc-review-snbt.json"), new GsonBuilder().setPrettyPrinting().create().toJson(proof));
            }
            case "drag" -> {
                double x = command.get("x").getAsDouble(), y = command.get("y").getAsDouble(),
                        dx = command.get("dx").getAsDouble(), dy = command.get("dy").getAsDouble();
                mc.screen.mouseClicked(x, y, 0); mc.screen.mouseDragged(x + dx, y + dy, 0, dx, dy);
                mc.screen.mouseReleased(x + dx, y + dy, 0);
            }
            case "map-dimension" -> {
                var dim = net.minecraft.resources.ResourceKey.create(Registries.DIMENSION,
                        ResourceLocation.parse(command.get("dimension").getAsString()));
                var method = mc.screen.getClass().getDeclaredMethod("changeDim", net.minecraft.resources.ResourceKey.class);
                method.setAccessible(true); method.invoke(mc.screen, dim);
            }
            case "gui-scale" -> { mc.options.guiScale().set(command.get("scale").getAsInt()); mc.resizeDisplay(); }
            case "mode" -> {
                var server = mc.getSingleplayerServer(); var id = mc.player.getUUID();
                var mode = command.get("creative").getAsBoolean() ? GameType.CREATIVE : GameType.SURVIVAL;
                server.execute(() -> server.getPlayerList().getPlayer(id).setGameMode(mode));
            }
            case "shutdown" -> {
                var server = mc.getSingleplayerServer();
                if (server != null) server.halt(false);
                mc.stop();
            }
            default -> throw new IllegalArgumentException("Unknown misc fixture operation");
        }
    }
    private JsonObject state(Minecraft mc) throws Exception {
        var row = new JsonObject(); row.addProperty("sequence", sequence); row.addProperty("error", error);
        if (Boolean.getBoolean("skyweave_compat.probe.memory")) row.add("memory", ClientMemoryProbe.snapshot(mc));
        if (Boolean.getBoolean("skyweave_compat.probe.wayfinder")) row.add("wayfinder",WayfinderFixture.state(mc));
        var versions = new JsonObject();
        for (var id : new String[]{"hoofprint", "surveyor", "connector", "ftblibrary", "inventoryprofilesnext", "libipn"})
            net.neoforged.fml.ModList.get().getModContainerById(id).ifPresent(mod -> versions.addProperty(id, mod.getModInfo().getVersion().toString()));
        row.add("versions", versions);
        row.addProperty("loaded", mc.player != null && mc.level != null);
        row.addProperty("connectionIdentity", System.identityHashCode(mc.getConnection()));
        if (mc.getConnection() != null) {
            var address = mc.getConnection().getConnection().getRemoteAddress();
            row.addProperty("connectionKind",address.getClass().getName());
            row.addProperty("connectionAddress",address.toString());
            if (Boolean.getBoolean("skyweave_compat.probe.wayfinder"))
                row.addProperty("wayfinderChannel",mc.getConnection().hasChannel(design.inherent.skyweavecompat.wayfinder.WayfinderPacket.TYPE));
        }
        row.addProperty("resourceReloadDone", resourceReload != null && resourceReload.isDone() && !resourceReload.isCompletedExceptionally());
        row.addProperty("windowActive", mc.isWindowActive());
        row.addProperty("guiScale", mc.getWindow().getGuiScale()); row.addProperty("width", mc.getWindow().getGuiScaledWidth());
        row.addProperty("height", mc.getWindow().getGuiScaledHeight());
        row.add("screen", identity(mc.screen));
        if (mc.screen != null && mc.screen.getClass().getName().equals("dev.ftb.mods.ftblibrary.ui.ScreenWrapper")) {
            var gui = mc.screen.getClass().getMethod("getGui").invoke(mc.screen);
            row.add("previousScreen", identity(gui.getClass().getMethod("getPrevScreen").invoke(gui)));
        }
        if (mc.player == null) return row;
        var position = new JsonObject(); position.addProperty("x", mc.player.getX());
        position.addProperty("y", mc.player.getY()); position.addProperty("z", mc.player.getZ()); row.add("position", position);
        row.addProperty("dimension",mc.level.dimension().location().toString());
        if(design.inherent.skyweavecompat.OptionalIntegrations.enabled(design.inherent.skyweavecompat.Feature.CLIMATE)) {
            row.add("climate",new Gson().toJsonTree(design.inherent.skyweavecompat.client.ClimateClient.observe(mc.level,mc.player.blockPosition())));
            row.addProperty("climateChannel",mc.getConnection().hasChannel(design.inherent.skyweavecompat.climate.ClimatePacket.TYPE));
        }
        row.addProperty("centerChunkLoaded", mc.level.hasChunkAt(mc.player.blockPosition()));
        row.addProperty("creative", mc.player.isCreative()); row.add("currentMenu", identity(mc.player.containerMenu));
        row.add("inventoryMenu", identity(mc.player.inventoryMenu));
        row.addProperty("slotCount", mc.player.containerMenu.slots.size());
        var slots = new JsonArray();
        for (int i = 0; i < Math.min(9, mc.player.containerMenu.slots.size()); i++) {
            var stack = mc.player.containerMenu.getSlot(i).getItem(); var slot = new JsonObject();
            slot.addProperty("item", net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
            slot.addProperty("count", stack.getCount()); slots.add(slot);
        }
        row.add("firstSlots", slots);
        if (mc.screen instanceof AbstractContainerScreen<?> container) row.add("screenMenu", identity(container.getMenu()));
        if (mc.screen != null && mc.screen.getClass().getName().equals("garden.hestia.hoofprint.HoofprintScreen")) {
            var map = new JsonObject();
            for (var name : new String[]{"centreX", "centreZ", "guiScale", "dim", "inspectMode", "caveMode",
                    "hoveredWorldX", "hoveredWorldZ", "editingLandmark", "hoveredLandmark", "landmarkName", "editingStyle"}) {
                var field = mc.screen.getClass().getDeclaredField(name); field.setAccessible(true);
                map.addProperty(name, String.valueOf(field.get(mc.screen)));
            }
            for (var name : new String[]{"getScaleFactor"}) {
                var method = mc.screen.getClass().getDeclaredMethod(name); method.setAccessible(true);
                map.addProperty(name, ((Number)method.invoke(mc.screen)).doubleValue());
            }
            for (var name : new String[]{"screenXToWorldX", "screenYToWorldZ"}) {
                var method = mc.screen.getClass().getDeclaredMethod(name, double.class); method.setAccessible(true);
                map.addProperty(name + "0", ((Number)method.invoke(mc.screen, 0.0)).doubleValue());
                double end = name.equals("screenXToWorldX") ? mc.screen.width : mc.screen.height;
                map.addProperty(name + "End", ((Number)method.invoke(mc.screen, end)).doubleValue());
            }
            var storage = mc.screen.getClass().getMethod("mapStorage").invoke(mc.screen);
            var cached = new JsonObject();
            for (var name : new String[]{"minBlockX", "maxBlockX", "minBlockZ", "maxBlockZ"})
                cached.addProperty(name, ((Number)storage.getClass().getField(name).get(storage)).intValue());
            for (var name : new String[]{"regionTextures", "caveRegionTextures"}) {
                var textures = (java.util.Map<?, ?>)storage.getClass().getField(name).get(storage);
                cached.addProperty(name, textures.size());
                cached.addProperty(name + "Sample", textures.entrySet().stream().limit(4).toList().toString());
            }
            map.add("storage", cached);
            row.add("map", map);
        }
        for (var name : new String[]{"ContainerScreenEventHandler", "InsertWidgetHandler"}) {
            try {
                var type = Class.forName("org.anti_ad.mc.ipnext.gui.inject." + name);
                var owner = type.getField("INSTANCE").get(null);
                var widgets = (java.util.List<?>)type.getMethod("getCurrentWidgets").invoke(owner);
                if (widgets == null) { row.addProperty(name, "not initialized"); continue; }
                var list = new JsonArray();
                for (var widget : widgets) {
                    var entry = identity(widget);
                    for (Class<?> c = widget.getClass(); c != null && c != Object.class; c = c.getSuperclass())
                        for (var field : c.getDeclaredFields()) {
                            if (Modifier.isStatic(field.getModifiers()) || !java.util.Set.of("container", "screen", "bounds", "x", "y", "width", "height").contains(field.getName())) continue;
                            field.setAccessible(true); var value = field.get(widget);
                            if (value instanceof Number || value instanceof String) entry.addProperty(field.getName(), value.toString());
                            else entry.add(field.getName(), identity(value));
                        }
                    list.add(entry);
                }
                row.add(name, list);
                if (name.equals("InsertWidgetHandler")) row.add("ipnScreen", identity(type.getMethod("getCurrentScreen").invoke(owner)));
            } catch (ClassNotFoundException absent) { row.addProperty(name, "absent"); }
        }
        return row;
    }
    private static JsonObject identity(Object value) {
        var id = new JsonObject();
        if (value != null) {
            id.addProperty("class", value.getClass().getName()); id.addProperty("identity", System.identityHashCode(value));
            if (value instanceof net.minecraft.world.inventory.AbstractContainerMenu menu) id.addProperty("menuId", menu.containerId);
        }
        return id;
    }
}
