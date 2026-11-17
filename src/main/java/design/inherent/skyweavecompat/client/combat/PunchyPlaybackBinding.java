package design.inherent.skyweavecompat.client.combat;

import design.inherent.skyweavecompat.combat.AttackSnapshot;

/** Implemented by the qualified native handler; no global playback history is retained. */
public interface PunchyPlaybackBinding {
    void skyweave$bindAttack(AttackSnapshot attack, long playback);
}
