package design.inherent.skyweavecompat.clientprobe;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.platform.InputConstants;
import design.inherent.skyweavecompat.clientprobe.mixin.CombatPlayerAccess;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.HitResult;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;
import net.neoforged.neoforge.common.NeoForge;

/** Two real TCP clients; native key queue, authority and remote rendering remain separate witnesses. */
final class ClientCombatNetworkProbe {
    private static ClientCombatNetworkProbe active;
    private final Path root = FMLPaths.GAMEDIR.get();
    private final boolean actor = System.getProperty("skyweave_compat.probe.combat.role").equals("actor");
    private final JsonObject report = new JsonObject();
    private final JsonArray cases = new JsonArray();
    private final JsonArray swingStates = new JsonArray();
    private final Set<Integer> bodyPoses = new HashSet<>();
    private boolean opened, stopped, sawActor;
    private int ticks, sceneTick, remoteSwings, remoteStarts, remoteEdges, departed, bodySamples;
    private JsonObject current;
    private int swings, index = -1;
    private long beforeMoves;

    ClientCombatNetworkProbe() {
        if (!Files.isRegularFile(root.resolve("owned-cn-fixture.json"))) throw new IllegalStateException("Missing owned TCP client marker");
        active = this; report.addProperty("role", actor ? "actor" : "observer"); report.add("cases", cases);
        report.add("remoteSwingStates", swingStates);
        report.addProperty("scope", "Real loopback dedicated-server connection and native key-binding queue; controlled aim/charge, not physical mouse hardware");
        NeoForge.EVENT_BUS.addListener(this::tick); NeoForge.EVENT_BUS.addListener(this::body);
    }
    static void swing(Player player) {
        var p = active; if (p == null || p.stopped) return;
        if (player == Minecraft.getInstance().player && p.current != null) p.current.addProperty("nativeSwings", ++p.swings);
        else if (player.getName().getString().equals("CombatActor")) ++p.remoteSwings;
    }
    static void remoteEdge(boolean local, boolean result) {
        if (active != null && !active.actor && !local && result) ++active.remoteEdges;
    }
    static void remoteSwing(Player player, boolean fromServer, boolean before, int beforeTime, boolean after, int afterTime) {
        var p = active;
        if (p == null || p.actor || p.stopped || !player.getName().getString().equals("CombatActor")) return;
        var row = new JsonObject(); row.addProperty("tick", p.ticks); row.addProperty("playerTick", player.tickCount);
        row.addProperty("fromServer", fromServer); row.addProperty("before", before); row.addProperty("beforeTime", beforeTime);
        row.addProperty("after", after); row.addProperty("afterTime", afterTime); p.swingStates.add(row);
        if (after && afterTime == -1 && (!before || beforeTime != -1)) ++p.remoteStarts;
    }
    private static long moves() throws ReflectiveOperationException {
        var f = punchymoves.client.PlayerAnimationEngine.class.getDeclaredField("primaryActionTrigger"); f.setAccessible(true); return ((Number)f.get(null)).longValue();
    }
    private void tick(ClientTickEvent.Post event) {
        var mc = Minecraft.getInstance(); if (stopped || mc.getOverlay() != null) return;
        try {
            if (++ticks > 4000) throw new AssertionError("Owned TCP client exceeded200seconds");
            if (!opened && mc.screen instanceof TitleScreen) {
                opened = true; mc.options.pauseOnLostFocus = false;
                String address = System.getProperty("skyweave_compat.probe.combat.server");
                if (address == null || !address.matches("127\\.0\\.0\\.1:[0-9]{1,5}")) throw new IllegalArgumentException("Expected loopback server");
                ConnectScreen.startConnecting(mc.screen, mc, ServerAddress.parseString(address), new ServerData("Combat fixture", address, ServerData.Type.OTHER), false, null);
            }
            if (mc.player == null || mc.level == null) return;
            if (mc.getSingleplayerServer() != null || mc.isLocalServer()) throw new AssertionError("Expected a separate dedicated server");
            mc.setScreen(null); mc.player.setYRot(0); mc.player.setXRot(actor ? 14 : 0);
            if (!actor) {
                boolean present = mc.level.players().stream().anyMatch(p -> p.getName().getString().equals("CombatActor"));
                sawActor |= present;
                if (sawActor && !present && ++departed > 30) {
                    if (remoteStarts != 3 || remoteEdges < 3 || bodySamples == 0 || bodyPoses.size() < 3)
                        throw new AssertionError("Remote observation incomplete: raw calls=" + remoteSwings + ", starts=" + remoteStarts + ", edges=" + remoteEdges + ", poses=" + bodyPoses.size());
                    finish();
                }
                return;
            }
            if (!mc.player.getMainHandItem().is(Items.IRON_SWORD)
                    || mc.level.players().stream().noneMatch(p -> p.getName().getString().equals("CombatObserver"))) return;
            Cow target = null;
            for (var e : mc.level.entitiesForRendering()) if (e instanceof Cow cow && cow.getName().getString().equals("combat-network-target")) { target = cow; break; }
            if (target == null) return;
            if (++sceneTick <= 40) return;
            int next = (sceneTick - 41) / 40;
            if (next >= 4) { finish(); return; }
            if (next != index) {
                index = next; current = new JsonObject(); current.addProperty("name", new String[]{"rejected_hit", "charged_hit", "charged_miss", "charged_hit_again"}[index]);
                current.addProperty("nativeSwings", 0); cases.add(current); swings = 0;
            }
            int offset = (sceneTick - 41) % 40;
            if (index == 2) mc.player.setXRot(-30);
            if (offset == 10) {
                if ((index != 2 && (mc.hitResult == null || mc.hitResult.getType() != HitResult.Type.ENTITY))
                        || (index == 2 && mc.hitResult.getType() != HitResult.Type.MISS)) throw new AssertionError("Native crosshair did not match fixture aim: " + mc.hitResult);
                ((CombatPlayerAccess) mc.player).skyweave$attackTicker(index == 0 ? 0 : 100);
                beforeMoves = moves(); current.addProperty("inputPlayerTick", mc.player.tickCount);
                current.addProperty("nativePicked", mc.hitResult.getType().name());
                KeyMapping.click(InputConstants.Type.MOUSE.getOrCreate(0)); mc.options.keyAttack.setDown(true);
            }
            if (offset == 12) {
                mc.options.keyAttack.setDown(false); current.addProperty("movesTriggerDelta", moves() - beforeMoves);
                if (swings != (index == 0 ? 0 : 1)) throw new AssertionError("Native key queue produced unexpected swing count");
                if (design.inherent.skyweavecompat.OptionalIntegrations.enabled(design.inherent.skyweavecompat.Feature.CN_MOVES)
                        && current.get("movesTriggerDelta").getAsLong() != swings) throw new AssertionError("Admitted key queue did not deliver exactly once");
            }
        } catch (Throwable failure) { report.addProperty("failure", failure.toString()); failure.printStackTrace(); finish(); }
    }
    private void body(RenderPlayerEvent.Post event) {
        if (actor || stopped || !event.getEntity().getName().getString().equals("CombatActor")) return;
        ++bodySamples; var m = event.getRenderer().getModel();
        if (bodyPoses.size() < 1000) bodyPoses.add(java.util.Objects.hash(m.rightArm.xRot, m.rightArm.zRot, m.leftArm.xRot));
    }
    private void finish() {
        if (stopped) return; stopped = true; var mc = Minecraft.getInstance(); mc.options.keyAttack.setDown(false);
        report.addProperty("remoteSwingCalls", remoteSwings); report.addProperty("remoteStarts", remoteStarts); report.addProperty("remoteEdges", remoteEdges);
        report.addProperty("bodySamples", bodySamples); report.addProperty("distinctBodyPoses", bodyPoses.size());
        report.addProperty("complete", !report.has("failure"));
        try { Files.writeString(root.resolve("combat-network-client.json"), new GsonBuilder().setPrettyPrinting().create().toJson(report) + "\n"); }
        catch (java.io.IOException failure) { throw new java.io.UncheckedIOException(failure); }
        if (mc.level != null) mc.level.disconnect(); mc.disconnect(new TitleScreen()); mc.stop();
    }
}
