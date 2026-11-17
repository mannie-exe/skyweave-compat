package design.inherent.skyweavecompat.client.combat;

import design.inherent.skyweavecompat.combat.AttackSnapshot;
import design.inherent.skyweavecompat.combat.PendingAttack;
import fuzs.combatnouveau.CombatNouveau;
import fuzs.combatnouveau.config.ServerConfig;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import punchy.api.PunchyApi;
import punchy.client.state.AttackActionTracker;
import punchy.client.animation.PunchyAnimationManager;

/** Producer-bound prediction; never sends an attack or changes native gameplay. */
public final class PunchyAttackBridge {
    private static final PendingAttack PENDING = new PendingAttack();
    private static AttackSnapshot consuming;
    private static boolean tick, criticalRead;
    private record PlaybackCapture(AttackSnapshot attack, long previousPlayback) {}
    private static final ThreadLocal<PlaybackCapture> PLAYBACK = new ThreadLocal<>();
    private static final ThreadLocal<SpeedContext> SPEED = ThreadLocal.withInitial(SpeedContext::new);
    public static final class SpeedContext {
        private String clip;
        private float rate = 1;
        public String clip() { return clip; }
        public float rate() { return rate; }
        public void set(String clip, float rate) { this.clip = clip; this.rate = rate; }
    }
    public static SpeedContext speedContext() { return SPEED.get(); }
    public static void register() {
        PunchyApi.attackRules("skyweave_compat:combat_nouveau", new PunchyApi.AttackRules() {
            @Override public Boolean unthrottled(Object value) {
                var hand = AttackActionTracker.peekHand();
                if (hand == null || !(value instanceof ItemStack stack)) return null;
                var snapshot = PENDING.matching(AttackActionTracker.peekEpoch(), hand.ordinal(), ClientAttackBridge.session());
                return snapshot != null && snapshot.charge() >= 1
                        && snapshot.item().equals(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString())
                        && CombatNouveau.CONFIG.get(ServerConfig.class).minAttackStrength >= 1 ? Boolean.TRUE : null;
            }
        });
        PunchyApi.clipSpeed("skyweave_compat:combat_nouveau", (clip, layer, reason) -> {
            var context = SPEED.get();
            return "action".equals(layer) && clip.equals(context.clip()) ? context.rate() : 1;
        });
    }
    public static void produced() {
        var frame = ClientAttackBridge.current();
        var snapshot = frame == null ? null : frame.snapshot();
        var hand = AttackActionTracker.peekHand();
        if (snapshot == null || snapshot.kind() == AttackSnapshot.Kind.BLOCK || hand == null
                || snapshot.hand() != hand.ordinal()) { PENDING.clear(); return; }
        PENDING.record(AttackActionTracker.peekEpoch(), snapshot);
    }
    public static void beginTick() { tick = true; consuming = null; criticalRead = false; }
    public static void endTick() { tick = false; consuming = null; criticalRead = false; }
    public static void consumed(InteractionHand hand) {
        var snapshot = PENDING.take(AttackActionTracker.peekEpoch(), hand == null ? -1 : hand.ordinal(), ClientAttackBridge.session());
        consuming = tick ? snapshot : null;
        criticalRead = false;
    }
    public static boolean critical(boolean original) {
        if (consuming == null || criticalRead || consuming.session() != ClientAttackBridge.session()) return original;
        criticalRead = true;
        return consuming.isCritical();
    }
    public static AttackSnapshot consuming() { return consuming; }
    public static Runnable playback(Runnable original, ItemStack selectedStack) {
        AttackSnapshot captured = consuming;
        if (captured == null) return original;
        boolean pacing = CombatPreferences.punchy()
                && captured.item().equals(BuiltInRegistries.ITEM.getKey(selectedStack.getItem()).toString());
        return () -> {
            if (captured.session() != ClientAttackBridge.session()) return;
            var previous = PLAYBACK.get();
            PLAYBACK.set(new PlaybackCapture(pacing ? captured : null, PunchyAnimationManager.POSE_HANDLER.getPlaybackId()));
            try { original.run(); } finally { if (previous == null) PLAYBACK.remove(); else PLAYBACK.set(previous); }
        };
    }
    public static void visualPlayback() {
        var capture = PLAYBACK.get();
        long playback = PunchyAnimationManager.POSE_HANDLER.getPlaybackId();
        if (capture != null && capture.previousPlayback() == playback) return;
        var attack = capture == null ? null : capture.attack();
        var hand = AttackActionTracker.peekVisualHand();
        if (attack != null && (attack.session() != ClientAttackBridge.session() || hand == null || attack.hand() != hand.ordinal())) attack = null;
        ((PunchyPlaybackBinding) PunchyAnimationManager.POSE_HANDLER).skyweave$bindAttack(attack, playback);
    }
    public static void clear() { PENDING.clear(); consuming = null; criticalRead = false; SPEED.remove(); PLAYBACK.remove(); }
    public static void invalidate(long session) {
        var hand = AttackActionTracker.peekHand();
        if (hand != null && PENDING.matching(AttackActionTracker.peekEpoch(), hand.ordinal(), session) != null)
            AttackActionTracker.clear();
        else clear();
    }
    public static void discard(long sequence) {
        var hand = AttackActionTracker.peekHand();
        var snapshot = hand == null ? null : PENDING.matching(AttackActionTracker.peekEpoch(), hand.ordinal(), ClientAttackBridge.session());
        if (snapshot != null && snapshot.sequence() == sequence) PENDING.clear();
    }
    private PunchyAttackBridge() {}
}
