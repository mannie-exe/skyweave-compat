package design.inherent.skyweavecompat.combat;

/** Matches Punchy's single coalescing slot. A later admitted producer replaces the owned receipt. */
public final class PendingAttack {
    private long epoch;
    private AttackSnapshot snapshot;
    public void record(long epoch, AttackSnapshot snapshot) {
        if (epoch < 0 || snapshot == null) throw new IllegalArgumentException("Missing producer identity");
        this.epoch = epoch;
        this.snapshot = snapshot;
    }
    public AttackSnapshot matching(long epoch, int hand, long session) {
        return snapshot != null && this.epoch == epoch && snapshot.hand() == hand && snapshot.session() == session
                ? snapshot : null;
    }
    public AttackSnapshot take(long epoch, int hand, long session) {
        AttackSnapshot result = matching(epoch, hand, session);
        clear();
        return result;
    }
    public void clear() { snapshot = null; }
}
