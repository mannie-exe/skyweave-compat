package design.inherent.skyweavecompat.combat;

/** Immutable admitted-input facts; contains no live player, world or mutable item reference. */
public record AttackSnapshot(long session, long sequence, int hand, String item,
        float charge, double attackSpeed, boolean onGround, float fallDistance, Kind kind, Critical critical) {
    public enum Kind { ENTITY, MISS, BLOCK }
    public enum Critical { NO_DECISION, ORDINARY, CRITICAL }
    public AttackSnapshot {
        if (session <= 0 || sequence <= 0 || hand < 0 || hand > 1 || item == null || kind == null || critical == null
                || !Float.isFinite(charge) || charge < 0 || charge > 1
                || !Double.isFinite(attackSpeed) || attackSpeed < 0
                || !Float.isFinite(fallDistance) || fallDistance < 0)
            throw new IllegalArgumentException("Invalid captured attack");
    }
    public boolean isCritical() { return critical == Critical.CRITICAL; }
}
