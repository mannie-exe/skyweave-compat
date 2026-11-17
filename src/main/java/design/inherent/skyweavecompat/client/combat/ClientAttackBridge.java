package design.inherent.skyweavecompat.client.combat;

import design.inherent.skyweavecompat.Feature;
import design.inherent.skyweavecompat.OptionalIntegrations;
import design.inherent.skyweavecompat.combat.AttackSnapshot;
import java.lang.ref.WeakReference;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.HitResult;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.neoforged.neoforge.common.NeoForge;

/** A native input scope plus session identity. Foreign adapters are reached only after metadata admission. */
public final class ClientAttackBridge {
    private static final ThreadLocal<Frame> CURRENT = new ThreadLocal<>();
    private static WeakReference<Object> world = new WeakReference<>(null), player = new WeakReference<>(null);
    private static long session = 1, sequence;
    public static boolean admitted() {
        return OptionalIntegrations.enabled(Feature.CN_PUNCHY) || OptionalIntegrations.enabled(Feature.CN_MOVES);
    }
    public static void register(IEventBus bus) {
        bus.addListener((RegisterClientReloadListenersEvent event) ->
                event.registerReloadListener((ResourceManagerReloadListener) resources -> reset()));
        bus.addListener((net.neoforged.fml.event.lifecycle.FMLClientSetupEvent event) -> event.enqueueWork(() -> {
            if (OptionalIntegrations.enabled(Feature.CN_PUNCHY)) PunchyAttackBridge.register();
        }));
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post event) -> session());
        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut event) -> reset());
    }
    public static long session() {
        var mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || !mc.player.isAlive()) {
            if (world.get() != null || player.get() != null) reset();
            return 0;
        }
        if (world.get() != mc.level || player.get() != mc.player) {
            reset(); world = new WeakReference<>(mc.level); player = new WeakReference<>(mc.player);
        }
        return session;
    }
    public static void reset() {
        long previous = session;
        ++session;
        world.clear(); player.clear();
        if (OptionalIntegrations.enabled(Feature.CN_PUNCHY)) PunchyAttackBridge.invalidate(previous);
        if (OptionalIntegrations.enabled(Feature.CN_MOVES)) MovesAttackBridge.clear();
    }
    public static Frame begin(LocalPlayer actor, HitResult target) {
        Frame frame = new Frame(CURRENT.get(), actor, session(), ++sequence, target);
        CURRENT.set(frame); return frame;
    }
    public static Frame current() { return CURRENT.get(); }
    public static void end(Frame frame, boolean completed) {
        CURRENT.set(frame.previous);
        if (!completed) {
            if (OptionalIntegrations.enabled(Feature.CN_PUNCHY)) PunchyAttackBridge.discard(frame.sequence);
            if (OptionalIntegrations.enabled(Feature.CN_MOVES)) MovesAttackBridge.discard(frame.sequence);
        }
    }
    public static void critical(Player actor, Entity target, boolean critical) {
        Frame frame = current();
        if (frame != null && frame.actor == actor && frame.target == target)
            frame.critical = critical ? AttackSnapshot.Critical.CRITICAL : AttackSnapshot.Critical.ORDINARY;
    }
    public static final class Frame {
        private final Frame previous;
        public final LocalPlayer actor;
        private final long session, sequence;
        private AttackSnapshot.Kind kind;
        private Entity target;
        private AttackSnapshot.Critical critical = AttackSnapshot.Critical.NO_DECISION;
        private String item;
        private float charge, fallDistance;
        private double speed;
        private boolean onGround, admitted, deliveringMoves;
        private int hand;
        private Frame(Frame previous, LocalPlayer actor, long session, long sequence, HitResult hit) {
            this.previous = previous; this.actor = actor; this.session = session; this.sequence = sequence;
            kind = hit != null && hit.getType() == HitResult.Type.BLOCK ? AttackSnapshot.Kind.BLOCK : AttackSnapshot.Kind.MISS;
            capture();
        }
        public void entity(Entity target) { this.target = target; kind = AttackSnapshot.Kind.ENTITY; capture(); }
        public void miss() { target = null; kind = AttackSnapshot.Kind.MISS; }
        private void capture() {
            if (actor == null) return;
            item = BuiltInRegistries.ITEM.getKey(actor.getMainHandItem().getItem()).toString();
            charge = actor.getAttackStrengthScale(.5f); speed = actor.getAttributeValue(Attributes.ATTACK_SPEED);
            onGround = actor.onGround(); fallDistance = actor.fallDistance;
        }
        public void admit(InteractionHand hand) { this.hand = hand.ordinal(); admitted = true; }
        public boolean admitMiss(InteractionHand hand) {
            if (admitted || kind != AttackSnapshot.Kind.MISS) return false;
            admit(hand); return true;
        }
        public AttackSnapshot snapshot() {
            if (!admitted || actor == null || actor != Minecraft.getInstance().player
                    || session == 0 || session != ClientAttackBridge.session() || item == null || !Float.isFinite(charge)
                    || !Double.isFinite(speed) || speed < 0 || !Float.isFinite(fallDistance)) return null;
            return new AttackSnapshot(session, sequence, hand, item, Math.clamp(charge, 0, 1), speed,
                    onGround, Math.max(0, fallDistance), kind, critical);
        }
        public boolean deliveringMoves() { return deliveringMoves; }
        public void deliverMoves() {
            if (!OptionalIntegrations.enabled(Feature.CN_MOVES) || snapshot() == null) return;
            deliveringMoves = true;
            try { punchymoves.client.PlayerAnimationEngine.triggerPrimaryAction(actor); }
            finally { deliveringMoves = false; }
        }
    }
    private ClientAttackBridge() {}
}
