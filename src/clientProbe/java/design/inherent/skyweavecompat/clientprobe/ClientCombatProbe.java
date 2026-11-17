package design.inherent.skyweavecompat.clientprobe;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import design.inherent.skyweavecompat.clientprobe.mixin.CombatMinecraftAccess;
import design.inherent.skyweavecompat.clientprobe.mixin.CombatPlayerAccess;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.CameraType;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.phys.*;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;
import net.neoforged.neoforge.common.NeoForge;

/** Actual native input/prediction/animation witnesses in a marked disposable client. */
public final class ClientCombatProbe {
    private record Case(String name, Item item, String target, boolean charged, boolean falling, boolean sprinting, int interval) {
        Case(String name, Item item, String target, boolean charged, boolean falling, boolean sprinting) {
            this(name, item, target, charged, falling, sprinting, 40);
        }
    }
    private static final List<Case> CASES = java.util.stream.Stream.concat(List.of(
            new Case("rejected_sword", Items.IRON_SWORD, "entity", false, false, false),
            new Case("charged_sword", Items.IRON_SWORD, "entity", true, false, false),
            new Case("rejected_miss", Items.IRON_SWORD, "miss", false, false, false),
            new Case("charged_miss", Items.IRON_SWORD, "miss", true, false, false),
            new Case("block_mining", Items.IRON_PICKAXE, "block", true, false, false),
            new Case("falling_critical", Items.IRON_SWORD, "entity", true, true, false),
            new Case("sprinting_critical", Items.IRON_SWORD, "entity", true, true, true),
            new Case("grounded_sprint", Items.IRON_SWORD, "entity", true, false, true),
            new Case("charged_axe", Items.IRON_AXE, "entity", true, false, false),
            new Case("charged_fist", Items.AIR, "entity", true, false, false)).stream(),
            java.util.stream.IntStream.range(0, 8).mapToObj(i ->
                    new Case("rapid_fist_" + i, Items.AIR, "entity", true, false, false, 5))).toList();
    private static ClientCombatProbe active;
    private static final String WORLD = "combat-bridge-review";
    private final Path root = FMLPaths.GAMEDIR.get();
    private final JsonObject report = new JsonObject();
    private final JsonArray cases = new JsonArray();
    private final JsonArray frames = new JsonArray();
    private final JsonArray moments = new JsonArray();
    private final JsonArray bodies = new JsonArray();
    private final JsonArray clocks = new JsonArray();
    private final JsonArray visuals = new JsonArray();
    private static java.lang.reflect.Field nativeTimer;
    private static java.lang.reflect.Method nativeModifier;
    private final java.util.Set<String> shots = new java.util.HashSet<>();
    private CompletableFuture<?> pending;
    private boolean opened, prepared, stopped, draining;
    private int tick, caseTick, drainTicks, index = -1, targetId;
    private long frame, lastTickFrame = -1;
    private JsonObject current;
    private int swings;
    private ClientCombatControls controls;

    ClientCombatProbe() {
        if (!Files.isRegularFile(root.resolve("owned-cn-fixture.json"))) throw new IllegalStateException("Missing combat fixture marker");
        active = this;
        report.add("cases", cases); report.add("frames", frames);
        report.add("moments", moments); report.add("bodies", bodies);
        report.add("clocks", clocks);
        report.add("visuals", visuals);
        report.addProperty("view", System.getProperty("skyweave_compat.probe.combat.view", "first"));
        report.addProperty("scope", "Native startAttack invocation, integrated prediction and actual rendered animation; controlled ticker/aim, not physical keyboard/TCP/server-damage proof");
        NeoForge.EVENT_BUS.addListener(this::tick);
        NeoForge.EVENT_BUS.addListener(this::frame);
        NeoForge.EVENT_BUS.addListener(this::body);
        NeoForge.EVENT_BUS.addListener(net.neoforged.bus.api.EventPriority.LOWEST, true,
                net.neoforged.neoforge.client.event.InputEvent.InteractionKeyMappingTriggered.class, event -> {
                    if (current != null && !stopped && event.isAttack()) {
                        current.addProperty("inputEventCancelled", event.isCanceled());
                        current.addProperty("inputEventSwing", event.shouldSwingHand());
                    }
                });
        punchy.api.PunchyApi.onMoment("skyweave_compat:combat_probe", "", moment -> {
            if (current == null || stopped || moments.size() >= 2000) return;
            var row = new JsonObject(); row.addProperty("name", moment.name()); row.addProperty("hand", moment.hand());
            row.addProperty("value", moment.value()); row.addProperty("clip", moment.clip()); row.addProperty("source", moment.source());
            row.addProperty("case", current.get("name").getAsString()); row.addProperty("tick", tick); row.addProperty("frame", frame);
            moments.add(row);
        });
    }

    public static void swing(Player player) {
        ClientCombatNetworkProbe.swing(player);
        var p = active;
        if (p != null && player == Minecraft.getInstance().player && p.current != null) {
            p.current.addProperty("nativeSwings", ++p.swings);
        }
    }
    public static void critical(Player player, boolean critical, float multiplier) {
        var p = active;
        if (p != null && player == Minecraft.getInstance().player && p.current != null) {
            p.current.addProperty("nativeCritical", critical); p.current.addProperty("nativeMultiplier", multiplier);
        }
    }
    public static void consumedCritical(boolean critical) {
        var p = active;
        if (p != null && p.current != null) p.current.addProperty("consumedCritical", critical);
    }
    public static void visual(long epoch, long playback, InteractionHand hand) {
        var p = active;
        if (p == null || p.current == null || p.stopped) return;
        var row = new JsonObject(); row.addProperty("epoch", epoch); row.addProperty("playback", playback);
        row.addProperty("producerEpoch", punchy.client.state.AttackActionTracker.peekEpoch());
        row.addProperty("hand", hand.name()); row.addProperty("case", p.current.get("name").getAsString());
        row.addProperty("tick", p.tick); row.addProperty("playerTick", Minecraft.getInstance().player.tickCount);
        p.visuals.add(row);
    }
    public static void clock(Object layer, String clip, float length, float time) {
        var p = active;
        if (p == null || p.current == null || p.stopped || p.clocks.size() >= 10000) return;
        try {
            if (nativeTimer == null) {
                nativeTimer = layer.getClass().getDeclaredField("timerControl"); nativeTimer.setAccessible(true);
                nativeModifier = nativeTimer.getType().getDeclaredMethod("modifier"); nativeModifier.setAccessible(true);
            }
            float modifier = ((Number) nativeModifier.invoke(nativeTimer.get(layer))).floatValue();
            var row = new JsonObject(); row.addProperty("clip", clip); row.addProperty("length", length);
            row.addProperty("time", time); row.addProperty("rate", modifier); row.addProperty("tick", p.tick); row.addProperty("frame", p.frame);
            row.addProperty("case", p.current.get("name").getAsString()); p.clocks.add(row);
        } catch (ReflectiveOperationException failure) { p.fail(failure); }
    }
    private void frame(RenderFrameEvent.Post event) {
        ++frame;
        if (current == null || stopped || frames.size() >= 3000) return;
        try {
            var row = new JsonObject(); row.addProperty("frame", frame); row.addProperty("tick", tick);
            row.addProperty("case", current.get("name").getAsString());
            row.addProperty("offsetTick", tick - current.get("attackTick").getAsInt());
            Class<?> manager = Class.forName("punchy.client.animation.PunchyAnimationManager");
            Object pose = manager.getField("POSE_HANDLER").get(null);
            Object clip = pose.getClass().getMethod("getCurrentClip").invoke(pose);
            row.addProperty("playback", ((Number) pose.getClass().getMethod("getPlaybackId").invoke(pose)).longValue());
            row.addProperty("clipTime", ((Number) pose.getClass().getMethod("getCurrentTime").invoke(pose)).floatValue());
            if (clip != null) {
                row.addProperty("clip", (String) clip.getClass().getMethod("getName").invoke(clip));
                row.addProperty("clipLength", ((Number) clip.getClass().getMethod("getLength").invoke(clip)).floatValue());
            }
            frames.add(row);
            int offset = row.get("offsetTick").getAsInt();
            String name = row.get("case").getAsString();
            if (Boolean.getBoolean("skyweave_compat.probe.combat.capture")
                    && List.of("charged_sword", "falling_critical", "charged_axe", "charged_fist").contains(name)
                    && java.util.Set.of(0, 1, 2, 3, 4, 6, 8, 12, 16, 20).contains(offset) && shots.add(name + "-" + offset))
                Screenshot.grab(Minecraft.getInstance().gameDirectory, name + "-" + offset + ".png",
                        Minecraft.getInstance().getMainRenderTarget(), ignored -> {});
        } catch (ReflectiveOperationException e) { fail(e); }
    }
    private void body(RenderPlayerEvent.Post event) {
        if (current == null || stopped || bodies.size() >= 3000 || event.getEntity() != Minecraft.getInstance().player) return;
        var model = event.getRenderer().getModel(); var row = new JsonObject();
        row.addProperty("tick", tick); row.addProperty("frame", frame); row.addProperty("case", current.get("name").getAsString());
        row.addProperty("rightArmX", model.rightArm.xRot); row.addProperty("rightArmY", model.rightArm.yRot);
        row.addProperty("leftArmX", model.leftArm.xRot); row.addProperty("leftArmY", model.leftArm.yRot);
        row.addProperty("bodyX", model.body.xRot); row.addProperty("bodyY", model.body.yRot); bodies.add(row);
    }
    private void tick(ClientTickEvent.Post event) {
        var mc = Minecraft.getInstance();
        if (stopped || mc.getOverlay() != null) return;
        try {
            if (!opened && mc.screen instanceof TitleScreen) {
                opened = true; mc.options.pauseOnLostFocus = false;
                mc.options.renderDistance().set(3); mc.options.simulationDistance().set(3); mc.options.framerateLimit().set(60);
                mc.options.setCameraType("third".equals(System.getProperty("skyweave_compat.probe.combat.view"))
                        ? CameraType.THIRD_PERSON_BACK : CameraType.FIRST_PERSON);
                if (Boolean.getBoolean("skyweave_compat.probe.combat.settingsOnly")) {
                    controls = new ClientCombatControls(this, report); return;
                }
                if (Files.exists(root.resolve("saves/" + WORLD))) throw new IllegalStateException("Fresh combat world required");
                mc.createWorldOpenFlows().createFreshLevel(WORLD,
                        new LevelSettings("Combat review", GameType.SURVIVAL, false, Difficulty.PEACEFUL, true,
                                new GameRules(), WorldDataConfiguration.DEFAULT), new WorldOptions(42, false, false),
                        registries -> registries.registryOrThrow(Registries.WORLD_PRESET)
                                .get(ResourceLocation.withDefaultNamespace("flat")).createWorldDimensions(), mc.screen);
                return;
            }
            if (controls != null) {
                ++tick;
                if (controls.tick()) finish();
                return;
            }
            if (mc.player == null || mc.level == null || mc.getSingleplayerServer() == null) return;
            if (pending != null) { if (!pending.isDone()) return; pending.join(); pending = null; }
            if (lastTickFrame == frame) return;
            lastTickFrame = frame;
            if (!prepared) {
                prepared = true;
                var packs = new JsonArray();
                mc.getResourcePackRepository().getSelectedPacks().forEach(pack -> packs.add(pack.getId()));
                report.add("selectedResourcePacks", packs);
                Files.writeString(root.resolve("saves/" + WORLD + "/.combat-review-world"), "owned combat fixture\n");
                pending = mc.getSingleplayerServer().submit(() -> {
                    var player = mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());
                    player.teleportTo(.5, -60, .5); player.setGameMode(GameType.SURVIVAL);
                    var level = player.serverLevel(); level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, player.server);
                    level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, player.server);
                    var cow = new Cow(EntityType.COW, level); cow.setNoAi(true); cow.setNoGravity(true); cow.setInvulnerable(true);
                    cow.setPos(.5, -60, 3); level.addFreshEntity(cow); targetId = cow.getId();
                    level.setBlockAndUpdate(new BlockPos(2, -59, 2), Blocks.STONE.defaultBlockState());
                }); return;
            }
            mc.setScreen(null);
            ++tick;
            if (draining) {
                mc.options.keyAttack.setDown(false);
                if (++drainTicks >= 40) {
                    if (Boolean.getBoolean("skyweave_compat.probe.combat.extended")) controls = new ClientCombatControls(this, report);
                    else finish();
                }
                return;
            }
            if (++caseTick > (index < 0 ? 0 : CASES.get(index).interval())) {
                caseTick = 1;
                if (++index >= CASES.size()) { draining = true; return; }
                Case scenario = CASES.get(index);
                if (index == 0 || CASES.get(index - 1).item() != scenario.item())
                    pending = mc.getSingleplayerServer().submit(() -> {
                        var player = mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());
                        player.setItemInHand(InteractionHand.MAIN_HAND, scenario.item().getDefaultInstance());
                    });
            }
            int attackAt = Math.min(10, CASES.get(index).interval() - 3);
            if (caseTick == attackAt) attack(CASES.get(index));
            if (caseTick == attackAt + 2) mc.options.keyAttack.setDown(false);
        } catch (Throwable e) { fail(e); }
    }
    private void attack(Case scenario) throws ReflectiveOperationException {
        var mc = Minecraft.getInstance();
        var target = mc.level.getEntity(targetId);
        if (target == null) throw new IllegalStateException("Native target unavailable");
        current = new JsonObject(); current.addProperty("name", scenario.name()); cases.add(current); swings = 0;
        current.addProperty("attackTick", tick);
        current.addProperty("playerTick", mc.player.tickCount);
        var configuration = fuzs.combatnouveau.CombatNouveau.CONFIG.get(fuzs.combatnouveau.config.ServerConfig.class);
        current.addProperty("minimumCharge", configuration.minAttackStrength);
        current.addProperty("sprintingCriticalsEnabled", configuration.criticalHitsWhileSprinting);
        current.addProperty("punchyBridge", design.inherent.skyweavecompat.OptionalIntegrations.enabled(design.inherent.skyweavecompat.Feature.CN_PUNCHY));
        current.addProperty("movesBridge", design.inherent.skyweavecompat.OptionalIntegrations.enabled(design.inherent.skyweavecompat.Feature.CN_MOVES));
        current.addProperty("preferencesRegistered", design.inherent.skyweavecompat.client.combat.CombatPreferences.SPEC.isLoaded());
        if (design.inherent.skyweavecompat.client.combat.CombatPreferences.SPEC.isLoaded()) {
            current.addProperty("punchyPacing", design.inherent.skyweavecompat.client.combat.CombatPreferences.punchy());
            current.addProperty("movesPacing", design.inherent.skyweavecompat.client.combat.CombatPreferences.moves());
        }
        current.addProperty("attackSpeed", mc.player.getAttributeValue(Attributes.ATTACK_SPEED));
        current.addProperty("handsBusy", mc.player.isHandsBusy());
        current.addProperty("usingItem", mc.player.isUsingItem());
        current.addProperty("screen", mc.screen == null ? "none" : mc.screen.getClass().getName());
        current.addProperty("heldItem", BuiltInRegistries.ITEM.getKey(mc.player.getMainHandItem().getItem()).toString());
        current.addProperty("targetRemoved", target.isRemoved());
        mc.player.setOnGround(!scenario.falling()); mc.player.fallDistance = scenario.falling() ? 1 : 0;
        mc.player.setSprinting(scenario.sprinting()); mc.player.setYRot(0); mc.player.setXRot(0);
        if (!scenario.name().startsWith("rapid_fist_"))
            ((CombatPlayerAccess) mc.player).skyweave$attackTicker(scenario.charged() ? 100 : 0);
        ((CombatMinecraftAccess) mc).skyweave$missTime(0);
        mc.hitResult = switch (scenario.target()) {
            case "entity" -> new EntityHitResult(target);
            case "block" -> new BlockHitResult(new Vec3(2.5, -58.5, 2), Direction.NORTH, new BlockPos(2, -59, 2), false);
            case "air_block" -> new BlockHitResult(new Vec3(.5, -58, 8), Direction.NORTH, new BlockPos(0, -58, 8), false);
            default -> BlockHitResult.miss(new Vec3(.5, -58, 8), Direction.NORTH, new BlockPos(0, -58, 8));
        };
        mc.options.keyAttack.setDown(true);
        long before = primaryTrigger();
        current.addProperty("nativeReturn", ((CombatMinecraftAccess) mc).skyweave$startAttack());
        current.addProperty("producerEpoch", punchy.client.state.AttackActionTracker.peekEpoch());
        current.addProperty("inputGesture", punchy.client.state.AttackActionTracker.peekInputGesture());
        current.addProperty("movesTriggerDelta", primaryTrigger() - before);
        current.addProperty("nativeSwings", swings);
        current.addProperty("remainingCharge", mc.player.getAttackStrengthScale(.5f));
    }
    private static long primaryTrigger() throws ReflectiveOperationException {
        var field = Class.forName("punchymoves.client.PlayerAnimationEngine").getDeclaredField("primaryActionTrigger");
        field.setAccessible(true); return ((Number) field.get(null)).longValue();
    }
    JsonObject controlAttack(String name, boolean charged, boolean falling, boolean sprinting) throws ReflectiveOperationException {
        attack(new Case(name, Items.IRON_SWORD, "entity", charged, falling, sprinting));
        return current;
    }
    JsonObject controlMiss(String name) throws ReflectiveOperationException {
        attack(new Case(name, Items.IRON_SWORD, "miss", true, false, false)); return current;
    }
    JsonObject controlAirBlockMiss(String name) throws ReflectiveOperationException {
        attack(new Case(name, Items.IRON_SWORD, "air_block", true, false, false)); return current;
    }
    int targetId() { return targetId; }
    void targetId(int id) { targetId = id; }
    public static boolean holdPunchy() { return ClientCombatControls.holdPunchy; }
    public static void remoteEdge(boolean local, boolean result) { ClientCombatNetworkProbe.remoteEdge(local, result); }
    public static void remoteSwing(Player player, boolean fromServer, boolean before, int beforeTime, boolean after, int afterTime) {
        ClientCombatNetworkProbe.remoteSwing(player, fromServer, before, beforeTime, after, afterTime);
    }
    public static boolean deferAttack(Runnable action) {
        if (!ClientCombatControls.deferAttack) return false;
        if (ClientCombatControls.deferred != null) throw new AssertionError("Unexpected second deferred native action");
        ClientCombatControls.deferred = action; return true;
    }
    public static void throwIfRequested() {
        if (ClientCombatControls.throwAttack) throw new ClientCombatControls.ControlledAttackFault();
    }
    private void fail(Throwable failure) {
        failure.printStackTrace(); report.addProperty("failure", failure.toString()); finish();
    }
    private void finish() {
        if (stopped) return;
        stopped = true; Minecraft.getInstance().options.keyAttack.setDown(false);
        report.addProperty("complete", !report.has("failure"));
        try { Files.writeString(root.resolve("combat-report.json"), new GsonBuilder().setPrettyPrinting().create().toJson(report) + "\n"); }
        catch (java.io.IOException e) { throw new RuntimeException(e); }
        Minecraft.getInstance().stop();
    }
}
