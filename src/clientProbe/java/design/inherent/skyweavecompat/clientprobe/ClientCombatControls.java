package design.inherent.skyweavecompat.clientprobe;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import design.inherent.skyweavecompat.client.combat.ClientAttackBridge;
import design.inherent.skyweavecompat.client.combat.CombatPreferences;
import design.inherent.skyweavecompat.client.combat.MovesAttackBridge;
import design.inherent.skyweavecompat.client.combat.PunchyAttackBridge;
import design.inherent.skyweavecompat.combat.AttackSnapshot;
import design.inherent.skyweavecompat.combat.PendingAttack;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import punchy.client.state.AttackActionTracker;

/** Finite native fault/lifecycle controls, only in the disposable probe artifact. */
final class ClientCombatControls {
    @FunctionalInterface private interface Control { boolean run(int age) throws Exception; }
    private final ClientCombatProbe probe;
    private final JsonArray results = new JsonArray();
    private final List<Control> steps;
    private int step, age, elapsed, assertions, phase;
    private JsonObject row, attack, second;
    private CompletableFuture<?> future;
    private long oldSession, oldEpoch, oldVisual;
    private Object oldPlayer;
    private UUID target;
    private Object bodyController;
    private java.util.Map<String, java.util.Map<String, Float>> retriggerSettings;
    private java.util.Map<String, Float> previousRetrigger;
    static boolean holdPunchy, throwAttack;
    static boolean deferAttack;
    static Runnable deferred;

    ClientCombatControls(ClientCombatProbe probe, JsonObject report) {
        this.probe = probe; report.add("controls", results);
        // Same-frame producer controls require immediate native input, without a camera owner's deferred replay.
        mc().options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_FRONT);
        report.addProperty("controlCamera", "mirrored_third_person; Better Third Person deferred input has its own control");
        steps = Boolean.getBoolean("skyweave_compat.probe.combat.settingsOnly") ? List.of(this::configuration) : List.of(this::retainedMiss, this::missCharge, this::coalescedOrdinary, this::rejectedAfterCritical, this::foreignAfterCritical,
                this::discard, this::exception, this::blacklist, this::hotbar,
                this::fastAttribute, this::slowAttribute, this::effects, this::preferences, this::unknownRecovery, this::deferredPlayback,
                this::reload, this::afterReload, this::death, this::dimensionOut,
                this::dimensionBack, this::teleport, this::disconnect, this::reconnect, this::afterReconnect,
                this::thirdPersonInput, this::sable, this::configuration);
    }
    boolean tick() throws Exception {
        if (++elapsed > 3000) throw new AssertionError("Extended controls exceeded 3000 ticks");
        if (future != null) {
            if (!future.isDone()) return false;
            future.join(); future = null;
        }
        if (steps.get(step).run(++age)) {
            row.addProperty("assertions", assertions); row.addProperty("complete", true);
            java.nio.file.Files.writeString(net.neoforged.fml.loading.FMLPaths.GAMEDIR.get().resolve("combat-controls-progress.json"),
                    new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(results) + "\n");
            age = 0; assertions = 0; phase = 0;
            if (++step == steps.size()) { holdPunchy = false; return true; }
        }
        return false;
    }
    private void begin(String name) {
        row = new JsonObject(); row.addProperty("name", name); results.add(row);
        mc().options.keyAttack.setDown(false);
    }
    private void check(boolean value, String message) {
        ++assertions;
        if (!value) throw new AssertionError(row.get("name").getAsString() + ": " + message);
    }
    private static Minecraft mc() { return Minecraft.getInstance(); }
    private void server(java.util.function.Consumer<ServerPlayer> action) {
        var id = mc().player.getUUID(); var server = mc().getSingleplayerServer();
        future = server.submit(() -> action.accept(server.getPlayerList().getPlayer(id)));
    }
    private void sword(double baseSpeed) {
        server(player -> {
            player.setItemInHand(InteractionHand.MAIN_HAND, Items.IRON_SWORD.getDefaultInstance());
            player.getAttribute(Attributes.ATTACK_SPEED).setBaseValue(baseSpeed);
        });
    }
    private static Object field(Class<?> type, String name) throws ReflectiveOperationException {
        var field = type.getDeclaredField(name); field.setAccessible(true); return field.get(null);
    }
    private static Object field(Object owner, String name) throws ReflectiveOperationException {
        var field = owner.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(owner);
    }
    private static AttackSnapshot pending() throws ReflectiveOperationException {
        return ((PendingAttack) field(PunchyAttackBridge.class, "PENDING")).matching(
                AttackActionTracker.peekEpoch(), InteractionHand.MAIN_HAND.ordinal(), ClientAttackBridge.session());
    }
    private boolean retainedMiss(int n) throws Exception {
        if (n == 1) { begin("native_retained_energy_miss"); sword(4); }
        if (n == 10) {
            var config = fuzs.combatnouveau.CombatNouveau.CONFIG.get(fuzs.combatnouveau.config.ServerConfig.class);
            boolean previous = config.retainEnergyOnMiss; config.retainEnergyOnMiss = true;
            try { attack = probe.controlMiss("retained_energy_miss"); }
            finally { config.retainEnergyOnMiss = previous; }
            row.add("native", attack.deepCopy());
            row.addProperty("retainEnergy", true);
            check(attack.get("nativeSwings").getAsInt() == 1, "CN did not perform its retained-energy native swing");
            check(attack.get("movesTriggerDelta").getAsInt() == 1 && pending() != null, "Retained-energy native miss lost presentation context");
            check(pending().charge() == 1, "Retained-energy miss lost pre-attack charge");
        }
        if (n == 20) {
            var config = fuzs.combatnouveau.CombatNouveau.CONFIG.get(fuzs.combatnouveau.config.ServerConfig.class);
            boolean previous = config.retainEnergyOnMiss; config.retainEnergyOnMiss = true;
            try { attack = probe.controlAirBlockMiss("retained_energy_air_block_miss"); }
            finally { config.retainEnergyOnMiss = previous; }
            row.add("airBlock", attack.deepCopy());
            check(attack.get("nativeSwings").getAsInt() == 1 && attack.get("movesTriggerDelta").getAsInt() == 1,
                    "Air-block miss was not delivered once");
            check(!(boolean) field(punchymoves.client.PlayerAnimationEngine.class, "primaryActionBlockTarget"), "Air-block miss became mining context");
        }
        if (n < 35) return false;
        check(pending() == null, "Retained-energy miss did not consume its receipt"); return true;
    }
    private boolean missCharge(int n) throws Exception {
        if (n == 1) { begin("native_air_sweep_preserves_pre_reset_charge"); sword(4); }
        if (n == 10) {
            attack = probe.controlMiss("air_sweep_miss");
            check(attack.get("nativeSwings").getAsInt() == 1 && attack.get("movesTriggerDelta").getAsInt() == 1, "Native miss was not delivered once");
            row.addProperty("capturedCharge", pending() == null ? -1 : pending().charge());
            check(pending() != null && pending().charge() == 1, "CN air-sweep reset replaced pre-attack charge");
        }
        return n >= 35;
    }
    private boolean coalescedOrdinary(int n) throws Exception {
        if (n == 1) { begin("latest_coalesced_ordinary"); sword(4); }
        if (n == 10) {
            attack = probe.controlAttack("coalesced_critical", true, true, false); oldEpoch = AttackActionTracker.peekEpoch();
            second = probe.controlAttack("coalesced_ordinary", true, false, false);
            check(AttackActionTracker.peekEpoch() == oldEpoch, "Native producer did not coalesce in one epoch");
            check(pending() != null && !pending().isCritical(), "Latest ordinary did not own the receipt");
        }
        if (n < 35) return false;
        check(!second.get("consumedCritical").getAsBoolean(), "Consumer inherited the earlier critical");
        check(pending() == null, "Consumed receipt survived"); return true;
    }
    private boolean rejectedAfterCritical(int n) throws Exception {
        if (n == 1) begin("rejection_preserves_admitted_epoch");
        if (n == 10) {
            probe.controlAttack("admitted_before_rejection", true, true, true); oldEpoch = AttackActionTracker.peekEpoch();
            second = probe.controlAttack("rejected_after_critical", false, false, false);
            check(second.get("nativeSwings").getAsInt() == 0 && second.get("movesTriggerDelta").getAsInt() == 0, "Rejected action produced presentation");
            check(AttackActionTracker.peekEpoch() == oldEpoch && pending().isCritical(), "Rejection replaced admitted metadata");
            check(field(MovesAttackBridge.class, "latest") != null, "Rejected input discarded an admitted Moves handoff");
        }
        if (n < 35) return false;
        check(second.get("consumedCritical").getAsBoolean(), "Earlier admitted sprint critical was lost"); return true;
    }
    private boolean foreignAfterCritical(int n) throws Exception {
        if (n == 1) begin("foreign_input_drops_owned_prediction");
        if (n == 10) {
            attack = probe.controlAttack("owned_before_foreign", true, true, true);
            mc().player.setOnGround(true); mc().player.fallDistance = 0;
            AttackActionTracker.recordAttack(InteractionHand.MAIN_HAND);
            check(pending() == null, "Foreign producer inherited owned metadata");
            punchymoves.client.PlayerAnimationEngine.triggerPrimaryAction(mc().player);
            check(field(MovesAttackBridge.class, "latest") == null, "Foreign Moves producer inherited owned context");
        }
        if (n < 35) return false;
        check(!attack.get("consumedCritical").getAsBoolean(), "Owned critical leaked into foreign ordinary"); return true;
    }
    private boolean discard(int n) throws Exception {
        if (n == 1) begin("native_clear_discards_receipt");
        if (n == 10) {
            probe.controlAttack("discarded_critical", true, true, true); AttackActionTracker.clear();
            check(pending() == null && !AttackActionTracker.isTriggered(), "Native clear retained a receipt");
        }
        if (n == 20) attack = probe.controlAttack("ordinary_after_clear", true, false, false);
        if (n < 40) return false;
        check(!attack.get("consumedCritical").getAsBoolean(), "Discarded critical was reused"); return true;
    }
    private boolean exception(int n) throws Exception {
        if (n == 1) begin("native_exception_unwinds_scope");
        if (n == 10) {
            throwAttack = true;
            try { probe.controlAttack("controlled_exception", true, false, false); throw new AssertionError("Expected native fault"); }
            catch (ControlledAttackFault expected) { check(ClientAttackBridge.current() == null, "Frame survived exception"); }
            finally { throwAttack = false; }
            check(pending() == null, "Exception retained pending metadata");
        }
        if (n == 20) attack = probe.controlAttack("ordinary_after_exception", true, false, false);
        if (n < 40) return false;
        check(attack.get("nativeSwings").getAsInt() == 1 && !attack.get("consumedCritical").getAsBoolean(), "Next input after unwind failed"); return true;
    }
    private boolean blacklist(int n) throws Exception {
        if (n == 1) {
            begin("native_item_blacklist");
            var data = punchy.config.PunchyConfig.snapshot(); data.itemBlacklist = List.of("minecraft:iron_sword");
            punchy.config.PunchyConfig.applyAndSave(data);
        }
        if (n == 10) {
            check(punchy.client.animation.PunchyAnimationManager.isHandBlacklisted(mc(), InteractionHand.MAIN_HAND), "Native blacklist did not apply");
            probe.controlAttack("blacklisted_sword", true, true, true);
        }
        if (n < 35) return false;
        check(pending() == null, "Blacklisted receipt survived");
        var data = punchy.config.PunchyConfig.snapshot(); data.itemBlacklist = List.of();
        punchy.config.PunchyConfig.applyAndSave(data); return true;
    }
    private boolean hotbar(int n) throws Exception {
        if (n == 1) { begin("delayed_hotbar_change"); holdPunchy = true; }
        if (n == 10) {
            probe.controlAttack("sword_before_delayed_hotbar", true, false, false);
            check(pending() != null && pending().item().equals("minecraft:iron_sword"), "Pre-attack item not retained");
            mc().player.setItemInHand(InteractionHand.MAIN_HAND, Items.DIAMOND_AXE.getDefaultInstance());
            server(player -> player.setItemInHand(InteractionHand.MAIN_HAND, Items.DIAMOND_AXE.getDefaultInstance()));
        }
        if (n == 13) holdPunchy = false;
        if (n < 35) return false;
        check(pending() == null, "Delayed consumer retained old item receipt"); sword(4); return true;
    }
    private boolean fastAttribute(int n) throws Exception { return attribute(n, true); }
    private boolean slowAttribute(int n) throws Exception { return attribute(n, false); }
    private boolean attribute(int n, boolean fast) throws Exception {
        if (n == 1) { begin(fast ? "fast_captured_attribute" : "slow_captured_attribute"); sword(fast ? 5.6 : 3.2); }
        if (n == 10) {
            attack = probe.controlAttack(fast ? "fast_then_normal" : "slow_then_normal", true, false, false);
            double speed = attack.get("attackSpeed").getAsDouble(); row.addProperty("capturedSpeed", speed);
            check(Math.abs(speed - (fast ? 3.2 : .8)) < .001, "Synchronized native speed differs");
            server(player -> player.getAttribute(Attributes.ATTACK_SPEED).setBaseValue(4));
        }
        if (n < 40) return false;
        check(Math.abs(mc().player.getAttributeValue(Attributes.ATTACK_SPEED) - 1.6) < .001, "Post-admission attribute did not change");
        check(attack.get("nativeSwings").getAsInt() == 1, "Attribute test changed native action count"); return true;
    }
    private boolean effects(int n) throws Exception {
        if (n == 1) begin("native_effect_restrictions");
        if (n == 10) {
            mc().player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 100));
            attack = probe.controlAttack("blind_falling", true, true, true); mc().player.removeEffect(MobEffects.BLINDNESS);
        }
        if (n == 25) {
            check(!attack.get("nativeCritical").getAsBoolean() && !attack.get("consumedCritical").getAsBoolean(), "Blindness became critical");
            mc().player.addEffect(new MobEffectInstance(MobEffects.DIG_SPEED, 100, 1));
            attack = probe.controlAttack("haste_sword", true, false, false); mc().player.removeEffect(MobEffects.DIG_SPEED);
        }
        if (n == 40) {
            check(attack.get("nativeSwings").getAsInt() == 1, "Haste lost swing");
            mc().player.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, 100, 1));
            attack = probe.controlAttack("fatigue_sword", true, false, false); mc().player.removeEffect(MobEffects.DIG_SLOWDOWN);
        }
        if (n < 60) return false;
        check(attack.get("nativeSwings").getAsInt() == 1, "Fatigue lost swing"); return true;
    }
    private boolean preferences(int n) throws Exception {
        if (n == 1) begin("independent_live_client_preferences");
        if (n == 10) {
            CombatPreferences.PUNCHY_PACING.set(false); CombatPreferences.MOVES_PACING.set(true);
            attack = probe.controlAttack("first_pacing_off_body_on", true, false, false);
        }
        if (n == 30) {
            check(attack.get("nativeSwings").getAsInt() == 1, "Presentation opt-out lost gameplay");
            CombatPreferences.PUNCHY_PACING.set(true); CombatPreferences.MOVES_PACING.set(false);
            attack = probe.controlAttack("first_pacing_on_body_off", true, false, false);
        }
        if (n < 55) return false;
        CombatPreferences.MOVES_PACING.set(true); check(!attack.get("consumedCritical").getAsBoolean(), "Preference change leaked critical"); return true;
    }
    private boolean reload(int n) throws Exception {
        if (n == 1) { begin("real_resource_reload_clears_bindings"); holdPunchy = true; }
        if (n == 10) {
            oldVisual = AttackActionTracker.peekVisualEpoch();
            probe.controlAttack("pending_at_reload", true, true, true); oldSession = ClientAttackBridge.session();
            check(pending() != null, "Expected delayed receipt before reload"); future = mc().reloadResourcePacks();
        }
        if (n < 15) return false;
        check(ClientAttackBridge.session() != oldSession && pending() == null, "Reload retained old binding");
        check(!AttackActionTracker.isTriggered(), "Reload retained an owned native pending trigger");
        holdPunchy = false; return true;
    }
    private boolean afterReload(int n) throws Exception {
        if (n == 1) begin("registered_apis_survive_reload");
        if (n == 10) {
            check(AttackActionTracker.peekVisualEpoch() == oldVisual, "Invalidated trigger replayed after reload");
            attack = probe.controlAttack("ordinary_after_reload", true, false, false);
        }
        if (n < 35) return false;
        check(!attack.get("consumedCritical").getAsBoolean(), "Reload left stale critical");
        check(pending() == null, "Reloaded consumer failed to consume"); return true;
    }
    private boolean death(int n) throws Exception {
        if (n == 1) { begin("native_death_respawn"); holdPunchy = true; }
        if (n == 10) {
            probe.controlAttack("pending_at_death", true, true, true); oldPlayer = mc().player; oldSession = ClientAttackBridge.session();
            server(ServerPlayer::kill);
        }
        if (n >= 15 && phase == 0 && mc().player != null && !mc().player.isAlive()) {
            check(ClientAttackBridge.session() == 0 && pending() == null, "Death retained binding");
            check(!AttackActionTracker.isTriggered(), "Death retained owned native pending trigger");
            mc().player.respawn(); phase = 1;
        }
        if (n < 20 || phase == 0 || mc().player == null || !mc().player.isAlive() || mc().player == oldPlayer) return false;
        check(ClientAttackBridge.session() != oldSession, "Respawn reused session"); holdPunchy = false;
        server(player -> { player.teleportTo(.5, -60, .5); player.setItemInHand(InteractionHand.MAIN_HAND, Items.IRON_SWORD.getDefaultInstance()); });
        oldPlayer = null; return true;
    }
    private boolean dimensionOut(int n) throws Exception {
        if (n == 1) { begin("real_dimension_change"); oldSession = ClientAttackBridge.session(); holdPunchy = true;
            server(player -> player.teleportTo(player.server.getLevel(Level.NETHER), .5, 80, .5, 0, 0)); }
        if (n < 15 || mc().level == null || mc().level.dimension() != Level.NETHER) return false;
        check(ClientAttackBridge.session() != oldSession && pending() == null, "Dimension retained binding"); return true;
    }
    private boolean dimensionBack(int n) throws Exception {
        if (n == 1) { begin("return_to_overworld"); oldSession = ClientAttackBridge.session();
            server(player -> player.teleportTo(player.server.getLevel(Level.OVERWORLD), .5, -60, .5, 0, 0)); }
        if (n < 15 || mc().level == null || mc().level.dimension() != Level.OVERWORLD || mc().level.getEntity(probe.targetId()) == null) return false;
        check(ClientAttackBridge.session() != oldSession, "Return reused session"); holdPunchy = false; return true;
    }
    private boolean teleport(int n) throws Exception {
        if (n == 1) { begin("same_dimension_teleport"); server(player -> player.teleportTo(16.5, -60, .5)); }
        if (n == 15) server(player -> player.teleportTo(.5, -60, .5));
        if (n == 30) attack = probe.controlAttack("ordinary_after_teleport", true, false, false);
        if (n < 50) return false;
        check(!attack.get("consumedCritical").getAsBoolean() && pending() == null, "Teleport reused stale binding"); return true;
    }
    private boolean disconnect(int n) {
        if (n == 1) {
            begin("real_integrated_disconnect"); target = mc().level.getEntity(probe.targetId()).getUUID();
            oldSession = ClientAttackBridge.session(); mc().level.disconnect(); mc().disconnect(new TitleScreen());
        }
        if (mc().player != null || mc().level != null || mc().getSingleplayerServer() != null) return false;
        check(ClientAttackBridge.session() == 0, "Disconnected session active"); return true;
    }
    private boolean reconnect(int n) throws Exception {
        if (n == 1) { begin("same_process_reopen"); mc().createWorldOpenFlows().openWorld("combat-bridge-review", () -> {}); }
        if (mc().screen instanceof net.minecraft.client.gui.screens.BackupConfirmScreen)
            for (var child : mc().screen.children())
                if (child instanceof net.minecraft.client.gui.components.Button button
                        && button.getMessage().getString().equals("I know what I'm doing!")) { button.onPress(); break; }
        if (n < 20 || mc().player == null || mc().level == null || mc().getSingleplayerServer() == null) return false;
        if (phase == 0) {
            server(player -> probe.targetId(player.serverLevel().getEntity(target).getId())); phase = 1; return false;
        }
        if (mc().level.getEntity(probe.targetId()) == null) return false;
        check(ClientAttackBridge.session() != oldSession && pending() == null, "Reopen retained context");
        mc().setScreen(null); sword(4); return true;
    }
    private boolean afterReconnect(int n) throws Exception {
        if (n == 1) begin("ordinary_after_reconnect");
        if (n == 10) attack = probe.controlAttack("ordinary_after_reconnect", true, false, false);
        if (n < 35) return false;
        check(attack.get("nativeSwings").getAsInt() == 1 && !attack.get("consumedCritical").getAsBoolean(), "Reconnected action failed");
        check(pending() == null && field(MovesAttackBridge.class, "latest") == null, "Consumed context survived"); return true;
    }
    private boolean sable(int n) throws Exception {
        if (n == 1) {
            begin("optional_moving_sable_platform");
            if (!net.neoforged.fml.ModList.get().isLoaded("sable")) { row.addProperty("present", false); return true; }
            row.addProperty("present", true); server(player -> ClientCombatShips.prepare(player, probe.targetId()));
        }
        if (n < 10) return false;
        var observation = ClientCombatShips.observe();
        if (n == 10) row.add("initial", observation);
        if (n >= 15 && phase == 0 && mc().player.onGround() && observation.get("tracked").getAsBoolean()) {
            attack = probe.controlAttack("supported_moving_sable_attack", true, false, false); phase = 1;
            row.add("attackContext", observation);
        }
        if (n < 50) return false;
        row.add("final", observation);
        check(observation.get("tracked").getAsBoolean() && observation.get("movedDistance").getAsDouble() > .05, "Native body was not tracked and moving");
        check(phase == 1 && attack.get("nativeSwings").getAsInt() == 1 && attack.get("movesTriggerDelta").getAsInt() == 1, "Supported ship attack was not delivered once");
        check(attack.get("nativeCritical").getAsBoolean() == attack.get("consumedCritical").getAsBoolean(), "Ship attack lost native classification");
        return true;
    }
    private Object cameraQueue() throws Exception {
        var manager = Class.forName("io.socol.betterthirdperson.BetterThirdPerson").getMethod("getCameraManager").invoke(null);
        return field(field(manager, "delayedActions"), "actionQueue");
    }
    private boolean thirdPersonInput(int n) throws Exception {
        if (n == 1) {
            begin("better_third_person_deferred_native_input");
            if (!net.neoforged.fml.ModList.get().isLoaded("betterthirdperson")) { row.addProperty("present", false); return true; }
            row.addProperty("present", true); sword(4);
            mc().options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_BACK);
        }
        if (n == 10) {
            check(((java.util.Queue<?>) cameraQueue()).isEmpty(), "Unexpected old camera input");
            oldEpoch = ((Number) field(punchymoves.client.PlayerAnimationEngine.class, "primaryActionTrigger")).longValue();
            attack = probe.controlAttack("camera_deferred_attack", true, false, false);
            row.addProperty("immediateNativeSwings", attack.get("nativeSwings").getAsInt());
            row.addProperty("queued", ((java.util.Queue<?>) cameraQueue()).size());
            check(attack.get("nativeSwings").getAsInt() == 0 && attack.get("movesTriggerDelta").getAsInt() == 0,
                    "Camera-deferred input produced an early swing");
            check(((java.util.Queue<?>) cameraQueue()).size() == 1, "Native camera did not retain one action");
        }
        if (n < 35) return false;
        long delivered = ((Number) field(punchymoves.client.PlayerAnimationEngine.class, "primaryActionTrigger")).longValue() - oldEpoch;
        row.addProperty("deliveredMoves", delivered); row.addProperty("replayedSwings", attack.get("nativeSwings").getAsInt());
        check(attack.get("nativeSwings").getAsInt() == 1 && delivered == 1, "Camera replay did not deliver once");
        check(((java.util.Queue<?>) cameraQueue()).isEmpty() && pending() == null, "Camera replay retained queued input or prediction");
        mc().options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_FRONT); return true;
    }
    private boolean configuration(int n) throws Exception {
        if (n == 1) {
            begin("native_client_pacing_configuration");
            var screen = ClientConfigurationProbe.openScreen(mc().screen);
            mc().setScreen(screen);
            var search = screen.getClass().getDeclaredField("search"); search.setAccessible(true);
            ((net.minecraft.client.gui.components.EditBox) search.get(screen)).setValue("attack pacing");
        }
        if (n == 4) {
            var page = design.inherent.skyweavecompat.configuration.SettingsCatalog.page(
                    design.inherent.skyweavecompat.configuration.SettingsCatalog.Group.PLAYER_ANIMATION);
            var links = (java.util.Map<?, ?>) field(mc().screen, "links");
            var link = links.get(page.id());
            check(link instanceof net.minecraft.client.gui.components.Button button && button.active, "Punchy! + Moves navigation is unavailable");
            ((net.minecraft.client.gui.components.Button) link).onPress();
            var panel = field(mc().screen, "content");
            for (var entry : (java.util.List<?>) field(panel, "rows")) {
                var key = (String) field(entry, "key");
                if (key == null || !key.contains("punchyPacing")) continue;
                var jump = panel.getClass().getDeclaredMethod("jump", String.class); jump.setAccessible(true); jump.invoke(panel, key);
            }
        }
        if (n < 10) return false;
        check(mc().screen instanceof design.inherent.skyweavecompat.client.ConfigurationOverview, "Native settings screen did not remain open");
        check(CombatPreferences.SPEC.isLoaded(), "Native client preference specification is unavailable");
        var panel = field(mc().screen, "content");
        var fields = new JsonArray(); String lastAnchor = null;
        for (var entry : (java.util.List<?>) field(panel, "rows")) {
            var key = (String) field(entry, "key");
            if (key == null || !(key.contains("punchyPacing") || key.contains("movesPacing"))) continue;
            var controls = (java.util.List<?>) field(entry, "controls");
            check(!controls.isEmpty() && ((net.minecraft.client.gui.components.AbstractWidget) controls.getFirst()).active, "Pacing field is not editable");
            fields.add(((net.minecraft.network.chat.Component) field(entry, "label")).getString()); lastAnchor = key;
        }
        check(fields.size() == 2, "Both pacing fields are not presented on the selected page");
        if (n == 10) {
            row.add("fields", fields); row.addProperty("firstPerson", CombatPreferences.punchy()); row.addProperty("thirdPerson", CombatPreferences.moves());
            net.minecraft.client.Screenshot.grab(mc().gameDirectory, "combat-settings.png", mc().getMainRenderTarget(), ignored -> {});
        }
        if (n == 12) { var jump = panel.getClass().getDeclaredMethod("jump", String.class); jump.setAccessible(true); jump.invoke(panel, lastAnchor); }
        if (n < 15) return false;
        net.minecraft.client.Screenshot.grab(mc().gameDirectory, "combat-settings-last.png", mc().getMainRenderTarget(), ignored -> {});
        mc().setScreen(null); return true;
    }
    static final class ControlledAttackFault extends RuntimeException {}
    private boolean unknownRecovery(int n) throws Exception {
        if (n == 1) { begin("unknown_body_clip_retains_native_retrigger"); sword(4); }
        if (n == 10) probe.controlAttack("known_before_unknown_clip", true, false, false);
        if (n == 12) {
            var states = (java.util.Map<?, ?>) field(punchymoves.client.PlayerAnimationEngine.class, "PLAYERS");
            bodyController = field(states.get(mc().player), "controller");
            check(design.inherent.skyweavecompat.client.combat.MovesPacing.knownRecovery(bodyController, false, true), "Expected actual known native sword layer");
            var library = (punchymoves.client.animation.AnimationLibrary) field(punchymoves.client.PlayerAnimationEngine.class, "animations");
            row.addProperty("authoredNativeThreshold", library.retriggerProgress(mc().player.getMainHandItem(), "attack", 50));
            @SuppressWarnings("unchecked") var settings = (java.util.Map<String, java.util.Map<String, Float>>) field(field(library, "bindings"), "specificRetriggerProgress");
            retriggerSettings = settings; previousRetrigger = settings.put("minecraft:iron_sword", java.util.Map.of("attack", 80f));
            check(Math.abs(library.retriggerProgress(mc().player.getMainHandItem(), "attack", 50) - .8f) < .0001f, "Fixture did not establish native80percent refusal threshold");
            Object layer = field(bodyController, "swordAttack");
            var clip = (punchymoves.client.animation.AnimationLibrary.Clip) field(layer, "clip");
            var clipField = layer.getClass().getDeclaredField("clip"); clipField.setAccessible(true);
            clipField.set(layer, new punchymoves.client.animation.AnimationLibrary.Clip("skyweave_fixture:unsupported", clip.length(), clip.loop(), clip.bones()));
            var time = layer.getClass().getDeclaredField("time"); time.setAccessible(true); time.setFloat(layer, .01f);
            check(!design.inherent.skyweavecompat.client.combat.MovesPacing.knownRecovery(bodyController, false, true), "Unknown resource bypassed profile admission");
        }
        if (n == 13) probe.controlAttack("admitted_during_unknown_clip", true, false, false);
        if (n == 15) check(((Number) field(bodyController, "pendingPrimaryActions")).intValue() == 1, "Unknown clip bypassed native recovery gate");
        if (n < 40) return false;
        check(((Number) field(bodyController, "pendingPrimaryActions")).intValue() == 0, "Native recovery never released admitted action");
        if (previousRetrigger == null) retriggerSettings.remove("minecraft:iron_sword"); else retriggerSettings.put("minecraft:iron_sword", previousRetrigger);
        retriggerSettings = null; previousRetrigger = null;
        bodyController = null; return true;
    }
    private boolean deferredPlayback(int n) throws Exception {
        if (n == 1) {
            begin("deferred_playback_invalidates_with_pacing_off");
            CombatPreferences.PUNCHY_PACING.set(false); deferAttack = true; oldVisual = AttackActionTracker.peekVisualEpoch();
        }
        if (n == 10) probe.controlAttack("deferred_before_reset", true, false, false);
        if (n == 12) {
            check(deferred != null, "Native coordinator did not receive the deferred action"); ClientAttackBridge.reset();
            var action = deferred; deferred = null; deferAttack = false; action.run();
        }
        if (n < 30) return false;
        check(AttackActionTracker.peekVisualEpoch() == oldVisual, "Invalidated deferred action played with pacing off");
        CombatPreferences.PUNCHY_PACING.set(true); return true;
    }
}
