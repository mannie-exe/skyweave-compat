package design.inherent.skyweavecompat.probe;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.CriticalHitEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Read-only attack witnesses plus a finite scene in an explicitly marked disposable server. */
final class CombatNetworkProbe {
    private final Path root = FMLPaths.GAMEDIR.get();
    private final JsonObject report = new JsonObject();
    private final JsonArray attacks = new JsonArray(), criticals = new JsonArray();
    private Cow target;
    private MinecraftServer server;
    private boolean actorJoined, observerJoined;
    private int ticks;

    CombatNetworkProbe() {
        if (!Files.isRegularFile(root.resolve("owned-combat-network.json"))) throw new IllegalStateException("Missing owned network scene marker");
        report.add("attacks", attacks); report.add("criticals", criticals);
        NeoForge.EVENT_BUS.register(this);
    }
    @SubscribeEvent public void start(ServerStartedEvent event) {
        server = event.getServer();
        server.setDifficulty(Difficulty.PEACEFUL, true);
        var level = server.overworld();
        level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, server);
        level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, server);
        target = new Cow(EntityType.COW, level); target.setNoAi(true); target.setNoGravity(true);
        target.setCustomName(Component.literal("combat-network-target")); target.setPos(.5, -60, 3);
        target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000); target.setHealth(1000);
        target.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1);
        level.addFreshEntity(target);
        report.addProperty("dedicated", server.isDedicatedServer()); report.addProperty("port", server.getPort());
        write("combat-network-ready.json");
    }
    @SubscribeEvent public void login(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        String name = player.getGameProfile().getName();
        if (!name.equals("CombatActor") && !name.equals("CombatObserver")) throw new AssertionError("Unexpected fixture identity");
        player.setGameMode(GameType.SURVIVAL);
        player.teleportTo(player.serverLevel(), .5, -60, name.equals("CombatActor") ? .5 : -3, 0, 14);
        if (name.equals("CombatActor")) { actorJoined = true; player.setItemInHand(InteractionHand.MAIN_HAND, Items.IRON_SWORD.getDefaultInstance()); }
        else observerJoined = true;
    }
    @SubscribeEvent(priority = EventPriority.LOWEST) public void attack(AttackEntityEvent event) {
        if (event.getTarget() != target) return;
        var row = new JsonObject(); row.addProperty("player", event.getEntity().getName().getString());
        row.addProperty("gameTick", server.overworld().getGameTime()); row.addProperty("cancelled", event.isCanceled());
        attacks.add(row);
    }
    @SubscribeEvent(priority = EventPriority.LOWEST) public void critical(CriticalHitEvent event) {
        if (event.getTarget() != target) return;
        var row = new JsonObject(); row.addProperty("critical", event.isCriticalHit()); row.addProperty("multiplier", event.getDamageMultiplier());
        criticals.add(row);
    }
    @SubscribeEvent public void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!event.getEntity().getName().getString().equals("CombatActor")) return;
        report.addProperty("actorJoined", actorJoined); report.addProperty("observerJoined", observerJoined);
        report.addProperty("initialHealth", 1000); report.addProperty("remainingHealth", target.getHealth());
        report.addProperty("complete", actorJoined && observerJoined && attacks.size() == 2 && target.getHealth() < 1000);
        write("combat-network-server.json");
    }
    @SubscribeEvent public void tick(ServerTickEvent.Post event) {
        if (++ticks > 6000) { report.addProperty("failure", "Owned network scene exceeded300seconds"); write("combat-network-server.json"); event.getServer().halt(false); }
    }
    private void write(String file) {
        try { Files.writeString(root.resolve(file), new GsonBuilder().setPrettyPrinting().create().toJson(report) + "\n"); }
        catch (java.io.IOException failure) { throw new java.io.UncheckedIOException(failure); }
    }
}
