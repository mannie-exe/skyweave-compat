package design.inherent.skyweavecompat.combat;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AttackPacingTest {
    private static AttackSnapshot sword(double speed) {
        return new AttackSnapshot(1, 1, 0, "minecraft:iron_sword", 1, speed, true, 0,
                AttackSnapshot.Kind.ENTITY, AttackSnapshot.Critical.ORDINARY);
    }
    @Test void strikeAccelerationReturnsSmoothlyToIndependentRecovery() {
        var attack = sword(1.6);
        assertTrue(AttackPacing.firstPerson(attack, "attack_1", 2.75f, 0) > 1);
        assertEquals(1, AttackPacing.firstPerson(attack, "attack_1", 2.75f, 1));
        assertEquals(AttackPacing.firstPerson(attack, "attack_1", 2.75f, .6f),
                AttackPacing.firstPerson(attack, "attack_1", 2.75f, .60001f), .0001f);
        assertEquals(1, AttackPacing.thirdPerson(attack, "sword_attack_1", .8333f, .7f));
    }
    @Test void physicalAttributesAreCapturedAndRatesStayFiniteAtExtremes() {
        for (double speed : new double[]{.0001, 1.6, 4, 1000}) {
            var attack = sword(speed);
            for (int i = 0; i < 100; i++) {
                float rate = AttackPacing.firstPerson(attack, "attack_1", 2.75f, i / 30f);
                assertTrue(Float.isFinite(rate) && rate >= .5 && rate <= 4);
            }
        }
        assertTrue(AttackPacing.firstPerson(sword(3.2), "attack_1", 2.75f, .1f)
                > AttackPacing.firstPerson(sword(1.6), "attack_1", 2.75f, .1f));
    }
    @Test void unknownResourcesAndNonAttackContextsKeepNativeClock() {
        var attack = sword(1.6);
        assertEquals(1, AttackPacing.firstPerson(attack, "custom_clip", 2.75f, 0));
        assertEquals(1, AttackPacing.firstPerson(attack, "attack_1", 3f, 0));
        assertEquals(1, AttackPacing.firstPerson(attack, "attack_1", Float.NaN, 0));
        assertEquals(1, AttackPacing.firstPerson(attack, "attack_1", 2.75f, Float.NaN));
        var mining = new AttackSnapshot(1, 1, 0, "minecraft:iron_sword", 1, 1.6, true, 0,
                AttackSnapshot.Kind.BLOCK, AttackSnapshot.Critical.NO_DECISION);
        assertEquals(1, AttackPacing.firstPerson(mining, "attack_1", 2.75f, 0));
        var foreign = new AttackSnapshot(1, 1, 0, "foreign:unqualified_weapon", 1, 1.6, true, 0,
                AttackSnapshot.Kind.ENTITY, AttackSnapshot.Critical.ORDINARY);
        assertEquals(1, AttackPacing.thirdPerson(foreign, "sword_attack_1", .8333f, 0));
    }
    @Test void recoveryAdmissionRequiresTheQualifiedBodyClip() {
        assertTrue(AttackPacing.knownThirdPersonClip("sword_attack_1", .8333f));
        assertFalse(AttackPacing.knownThirdPersonClip("replacement_sword", .8333f));
        assertFalse(AttackPacing.knownThirdPersonClip("sword_attack_1", 1));
        assertFalse(AttackPacing.knownThirdPersonClip("sword_attack_1", Float.NaN));
        assertFalse(AttackPacing.knownThirdPersonClip(null, .8333f));
    }
}
