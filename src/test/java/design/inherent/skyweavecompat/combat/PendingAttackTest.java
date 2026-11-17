package design.inherent.skyweavecompat.combat;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PendingAttackTest {
    private static AttackSnapshot attack(long session, long sequence, int hand, boolean critical) {
        return new AttackSnapshot(session, sequence, hand, "minecraft:iron_sword", 1, 1.6, false, 1,
                AttackSnapshot.Kind.ENTITY, critical ? AttackSnapshot.Critical.CRITICAL : AttackSnapshot.Critical.ORDINARY);
    }
    @Test void receiptSurvivesNativeTriggerConsumptionButIsUsableOnlyOnce() {
        var pending = new PendingAttack();
        var snapshot = attack(1, 7, 0, true);
        pending.record(23, snapshot);
        // Native consumeHand has already cleared triggered; epoch/hand/session still identify its producer.
        assertSame(snapshot, pending.take(23, 0, 1));
        assertNull(pending.take(23, 0, 1));
    }
    @Test void sessionHandAndEpochMismatchesDiscardRatherThanLeakCriticality() {
        for (int kind = 0; kind < 3; kind++) {
            var pending = new PendingAttack(); pending.record(23, attack(1, 7, 0, true));
            assertNull(pending.take(kind == 0 ? 24 : 23, kind == 1 ? 1 : 0, kind == 2 ? 2 : 1));
            assertNull(pending.take(23, 0, 1));
        }
    }
    @Test void coalescingKeepsOnlyTheLatestActualProducerIncludingAnOrdinaryResult() {
        var pending = new PendingAttack(); pending.record(23, attack(1, 7, 0, true));
        var latest = attack(1, 8, 0, false); pending.record(23, latest);
        assertSame(latest, pending.take(23, 0, 1));
        assertFalse(latest.isCritical()); assertNull(pending.take(23, 0, 1));
    }
    @Test void foreignOrDiscardedProducerCannotReuseOwnedPrediction() {
        var pending = new PendingAttack(); pending.record(23, attack(1, 7, 0, true));
        pending.clear(); assertNull(pending.take(23, 0, 1));
        pending.record(24, attack(2, 8, 1, false));
        assertFalse(pending.take(24, 1, 2).isCritical());
    }
    @Test void malformedCaptureCannotEnterThePresentationBinding() {
        for (double speed : new double[]{Double.NaN, Double.POSITIVE_INFINITY, -1})
            assertThrows(IllegalArgumentException.class, () -> new AttackSnapshot(1, 1, 0, "minecraft:air", 1, speed,
                    true, 0, AttackSnapshot.Kind.MISS, AttackSnapshot.Critical.NO_DECISION));
        assertThrows(IllegalArgumentException.class, () -> new PendingAttack().record(1, null));
    }
}
