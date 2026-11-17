package design.inherent.skyweavecompat.client.combat;

import design.inherent.skyweavecompat.combat.AttackSnapshot;

/** One retained input context mirrors the native engine's coalescing primary-action fields. */
public final class MovesAttackBridge {
    private static AttackSnapshot latest;
    private static AttackSnapshot transferring;
    private static int latestEpoch, invalidatedEpoch;
    private static boolean invalidated;
    public static void recorded(AttackSnapshot snapshot, int epoch) {
        latest = snapshot; latestEpoch = epoch;
        invalidated = false;
    }
    public static void foreign() { latest = null; invalidated = false; }
    public static void clear() {
        if (latest != null) { invalidatedEpoch = latestEpoch; invalidated = true; }
        latest = null;
    }
    public static AttackSnapshot transferring() { return transferring; }
    public static void transfer(int epoch, Runnable original) {
        long session = ClientAttackBridge.session();
        if (invalidated && invalidatedEpoch == epoch) { invalidated = false; return; }
        var previous = transferring;
        transferring = latest != null && latestEpoch == epoch && latest.session() == session ? latest : null;
        latest = null;
        try { original.run(); } finally { transferring = previous; }
    }
    public static void discard(long sequence) { if (latest != null && latest.sequence() == sequence) clear(); }
    private MovesAttackBridge() {}
}
