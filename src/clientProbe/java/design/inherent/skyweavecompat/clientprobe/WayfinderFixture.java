package design.inherent.skyweavecompat.clientprobe;

import com.google.gson.*;
import com.mojang.blaze3d.platform.InputConstants;
import design.inherent.skyweavecompat.wayfinder.Wayfinder;
import design.inherent.skyweavecompat.wayfinder.WayfinderNetwork;
import java.lang.reflect.Field;
import java.util.*;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Controlled native scene preparation and observations, only in the existing owned misc fixture. */
final class WayfinderFixture {
    private static volatile String preparation = "idle";
    private static volatile JsonObject serverState = new JsonObject();
    private static volatile String targetBlock = "unobserved";
    private static int nextChunk, lastChunk, distance, generated;
    private static UUID playerId;
    private static boolean generating, dh;
    private static long previousServerTick, maximumServerGap;

    static void register() {
        NeoForge.EVENT_BUS.addListener(WayfinderFixture::serverTick);
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.client.event.ClientTickEvent.Post event) -> {
            var mc = Minecraft.getInstance();
            if(dh)WayfinderRemoteFixture.tick(mc);
            if (dh && !generating && preparation.equals("waiting for DH") && mc.level != null
                    && WayfinderDhFixture.ready(mc,distance)) preparation = "ready";
        });
    }
    static void apply(Minecraft mc, JsonObject control) throws Exception {
        switch (control.get("action").getAsString()) {
            case "prepare" -> {
                var server = mc.getSingleplayerServer();
                if (server == null) throw new IllegalStateException("Expected owned integrated world");
                int requested = control.get("distance").getAsInt();
                if (requested < 16 || requested > 65536 || requested %16 !=0) throw new IllegalArgumentException("Invalid fixture distance");
                dh = design.inherent.skyweavecompat.OptionalIntegrations.enabled(design.inherent.skyweavecompat.Feature.WAYFINDER_DH);
                playerId = mc.player.getUUID(); preparation = "starting";
                server.execute(() -> {
                    distance = requested; nextChunk = generated = 0; lastChunk = (distance+15)>>4;
                    generating = !control.has("reuse") || !control.get("reuse").getAsBoolean();
                    if (!generating) preparation = dh ? "waiting for DH" : "ready";
                    previousServerTick = maximumServerGap = 0;
                    var rules = server.overworld().getGameRules();
                    rules.getRule(net.minecraft.world.level.GameRules.RULE_DAYLIGHT).set(false,server);
                    rules.getRule(net.minecraft.world.level.GameRules.RULE_DOMOBSPAWNING).set(false,server);
                    server.overworld().setDayTime(6000); aim(server);
                });
            }
            case "aim" -> {
                var server = mc.getSingleplayerServer(); server.execute(() -> aim(server));
            }
            case "inspect-target", "import-target" -> {
                var server = mc.getSingleplayerServer();
                boolean importing = control.get("action").getAsString().equals("import-target");
                server.execute(() -> {
                    var level = server.overworld(); var chunk = level.getChunk(distance >> 4,0);
                    var block = level.getBlockState(new BlockPos(distance+8,102,8));
                    targetBlock = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(block.getBlock()).toString();
                    if (importing) {
                        if (!dh || !block.is(Blocks.STONE)) throw new IllegalStateException("Expected the actual native marker");
                        WayfinderDhFixture.importChunk(level,chunk);
                    }
                });
            }
            case "command" -> mc.getConnection().sendCommand("skyweavecompat wayfinder");
            case "coarse-pause", "coarse-transfer" -> {
                dh=design.inherent.skyweavecompat.OptionalIntegrations.enabled(design.inherent.skyweavecompat.Feature.WAYFINDER_DH);
                if(!dh)throw new IllegalStateException("Expected the selected native DH fixture");
                if(control.get("action").getAsString().equals("coarse-pause"))WayfinderRemoteFixture.pause(mc);
                else WayfinderRemoteFixture.start(mc,control);
            }
            case "column" -> {
                dh=design.inherent.skyweavecompat.OptionalIntegrations.enabled(design.inherent.skyweavecompat.Feature.WAYFINDER_DH);
                if(!dh)throw new IllegalStateException("The real optional DH reader is not active");
                WayfinderDhFixture.query(mc,control.get("x").getAsInt(),control.get("z").getAsInt(),
                        control.has("bottom")?control.get("bottom").getAsDouble():mc.level.getMinBuildHeight(),
                        control.has("top")?control.get("top").getAsDouble():mc.level.getMaxBuildHeight());
            }
            case "fly" -> {
                if(!mc.player.getAbilities().mayfly)throw new IllegalStateException("Fixture requires native Creative flight permission");
                mc.player.getAbilities().flying=true;mc.player.onUpdateAbilities();
                mc.player.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
            }
            case "cancel" -> mc.getConnection().sendCommand("skyweavecompat wayfinder cancel");
            case "bind" -> {
                var binding = Arrays.stream(mc.options.keyMappings).filter(key -> key.getName().equals("key.skyweave_compat.wayfinder")).findFirst().orElseThrow();
                binding.setKey(InputConstants.Type.KEYSYM.getOrCreate(control.get("code").getAsInt()));
                KeyMapping.resetMapping(); mc.options.save();
            }
            case "chat" -> mc.setScreen(new net.minecraft.client.gui.screens.ChatScreen(""));
            case "settings" -> mc.setScreen(new design.inherent.skyweavecompat.client.ConfigurationOverview(
                    net.neoforged.fml.ModList.get().getModContainerById("skyweave_compat").orElseThrow(),mc.screen));
            default -> throw new IllegalArgumentException("Unknown Wayfinder fixture action");
        }
    }
    private static void aim(net.minecraft.server.MinecraftServer server) {
        var player = server.getPlayerList().getPlayer(playerId);
        if (player == null) throw new IllegalStateException("Fixture player left");
        server.getPlayerList().op(player.getGameProfile());
        player.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
        player.getAbilities().flying = true; player.onUpdateAbilities(); player.setNoGravity(true);
        float pitch = (float)Math.toDegrees(Math.atan2(200+player.getEyeHeight()-103,distance+8.));
        // Native trig uses a quantized table; analytic atan2 alone misses thin distant targets.
        var view = player.calculateViewVector(pitch,-90);
        double sourceY = 102.95 - view.y / view.x * (distance+8.) - player.getEyeHeight();
        player.teleportTo(server.overworld(),.5,sourceY,8.5,Set.of(),-90,pitch);
        player.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO); player.fallDistance = 0;
    }
    private static void serverTick(ServerTickEvent.Post event) {
        var server = event.getServer();
        if (playerId == null || !server.isSingleplayer()) return;
        try {
            if (generating) {
                for (int budget = 0; budget < 2 && nextChunk <= lastChunk; budget++,nextChunk++) {
                    var level = server.overworld(); var chunk = level.getChunk(nextChunk,0);
                    if (nextChunk == distance >>4) for (int x = 0; x <16; x++) for (int z =0; z<16;z++)
                        level.setBlockAndUpdate(new BlockPos(distance+x,102,z),Blocks.STONE.defaultBlockState());
                    if (dh) WayfinderDhFixture.importChunk(level,chunk);
                    generated++;
                }
                preparation = "generated "+generated+"/"+(lastChunk+1);
                if (nextChunk > lastChunk) { generating = false; preparation = dh ? "waiting for DH" : "ready"; }
            }
            var snapshot = new JsonObject(); snapshot.addProperty("generatedChunks",generated);
            snapshot.addProperty("targetDistance",distance);
            var player = server.getPlayerList().getPlayer(playerId);
            if (player != null) {
                snapshot.add("eye",new Gson().toJsonTree(player.getEyePosition()));
                snapshot.add("view",new Gson().toJsonTree(player.getViewVector(1)));
            }
            var states = (Map<?,?>)field(Wayfinder.class,"SERVERS").get(null);
            var state = states.get(server);
            if (state != null) {
                var jobs = (Map<?,?>)field(state.getClass(),"jobs").get(state);
                snapshot.addProperty("jobs",jobs.size()); snapshot.addProperty("nativeRequests",field(state.getClass(),"requests").getInt(state));
                var job = jobs.get(playerId);
                snapshot.addProperty("phase",job == null ? "idle" : field(job.getClass(),"phase").get(job).toString());
                snapshot.addProperty("leases",job == null ? 0 : ((Set<?>)field(job.getClass(),"leases").get(job)).size());
                long now = System.nanoTime();
                if (job != null && previousServerTick !=0) maximumServerGap = Math.max(maximumServerGap,now-previousServerTick);
                previousServerTick = job == null ? 0 : now;
                snapshot.addProperty("maximumActiveTickGapMillis",maximumServerGap/1e6);
            }
            serverState = snapshot;
        } catch (Throwable error) { preparation = error.toString(); generating = false; error.printStackTrace(); }
    }
    static Field field(Class<?> type, String name) throws Exception {
        var field = type.getDeclaredField(name); field.setAccessible(true); return field;
    }
    static JsonObject state(Minecraft mc) throws Exception {
        var row = new JsonObject(); row.addProperty("preparation",preparation); row.add("server",serverState.deepCopy());
        row.addProperty("targetBlock",targetBlock);
        if (mc.player != null) {
            row.add("clientEye",new Gson().toJsonTree(mc.player.getEyePosition()));
            row.add("clientView",new Gson().toJsonTree(mc.player.getViewVector(1)));
        }
        if (mc.level != null) {
            row.addProperty("nativeLoadedChunks",mc.level.getChunkSource().getLoadedChunksCount());
            row.addProperty("nativeTargetLoaded",mc.level.getChunkSource().getChunk(
                    distance >> 4,0,net.minecraft.world.level.chunk.status.ChunkStatus.FULL,false) != null);
        }
        var keys = new JsonArray();
        for (var key : mc.options.keyMappings) if (key.getKey().getValue()==org.lwjgl.glfw.GLFW.GLFW_KEY_G
                || key.getName().equals("key.skyweave_compat.wayfinder")) {
            var entry = new JsonObject(); entry.addProperty("name",key.getName()); entry.addProperty("key",key.getKey().getName());
            entry.addProperty("down",key.isDown()); keys.add(entry);
        }
        row.add("keys",keys);
        if (dh) {
            row.addProperty("dhPreparation",WayfinderDhFixture.observation());
            row.addProperty("dhTargetColumn",WayfinderDhFixture.targetColumn());
            row.add("dhColumnQuery",WayfinderDhFixture.queryState());
            row.add("dhParentTransfer",WayfinderRemoteFixture.state());
        }
        var handler = WayfinderNetwork.client;
        for (var captured : handler.getClass().getDeclaredFields()) {
            captured.setAccessible(true); var value = captured.get(handler);
            if (value == null || !value.getClass().getName().equals("design.inherent.skyweavecompat.client.WayfinderClient")) continue;
            var job = field(value.getClass(),"job").get(value);
            row.addProperty("clientPending",job !=null);
            if (job !=null) {
                var trace = (design.inherent.skyweavecompat.wayfinder.WayfinderTrace)field(job.getClass(),"trace").get(job);
                row.addProperty("queryDistance",trace.distance()); row.addProperty("workUnits",trace.workUnits());
                row.add("requestRay",new Gson().toJsonTree(field(job.getClass(),"ray").get(job)));
                if(dh) {
                    var reader=field(trace.getClass(),"lod").get(trace);
                    if(reader instanceof design.inherent.skyweavecompat.wayfinder.dh.DhWayfinderColumns columns) {
                        row.addProperty("dataReads",columns.readCount());
                        row.addProperty("retainedSources",((Map<?,?>)field(columns.getClass(),"sources").get(columns)).size());
                        row.addProperty("retainedColumns",((Map<?,?>)field(columns.getClass(),"columns").get(columns)).size());
                    }
                }
            }
        }
        var message = field(net.minecraft.client.gui.Gui.class,"overlayMessageString").get(mc.gui);
        if (message instanceof net.minecraft.network.chat.Component text) row.addProperty("feedback",text.getString());
        row.addProperty("feedbackTicks",field(net.minecraft.client.gui.Gui.class,"overlayMessageTime").getInt(mc.gui));
        if(mc.player!=null)row.addProperty("operator",mc.player.hasPermissions(2));
        return row;
    }
}
