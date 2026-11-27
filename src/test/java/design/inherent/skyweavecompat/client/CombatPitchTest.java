package design.inherent.skyweavecompat.client;

import dev.kosmx.playerAnim.api.layered.modifier.AdjustmentModifier.PartModifier;
import dev.kosmx.playerAnim.core.util.Vec3f;
import java.util.Optional;
import java.util.Random;
import org.joml.Matrix3f;
import org.joml.Quaternionf;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CombatPitchTest {
    private static Matrix3f rotation(Vec3f v) {
        return new Matrix3f().rotation(new Quaternionf().rotationZYX(v.getZ(), v.getY(), v.getX()));
    }

    @Test void limitedUpwardAdjustmentPreservesOtherChannelsAndNativeBranches() {
        Vec3f scale = new Vec3f(1, 2, 3), offset = new Vec3f(4, 5, 6);
        for (String part : new String[]{"body", "leftLeg", "rightLeg"}) {
            var nativePose = new PartModifier(new Vec3f(1, .3f, -.4f), scale, offset);
            var adjusted = CombatPitch.limit(part, nativePose);
            assertEquals(CombatPitch.UPWARD_LIMIT, adjusted.rotation().getX());
            assertEquals(.3f, adjusted.rotation().getY());
            assertEquals(-.4f, adjusted.rotation().getZ());
            assertSame(scale, adjusted.scale()); assertSame(offset, adjusted.offset());
            for (float pitch : new float[]{-1, 0, .1f, CombatPitch.UPWARD_LIMIT}) {
                var unchanged = new PartModifier(new Vec3f(pitch, .3f, -.4f), scale, offset);
                assertSame(unchanged, CombatPitch.limit(part, unchanged));
            }
        }
        var nativeArm = new PartModifier(new Vec3f(1, 2, 3), scale, offset);
        for (String part : new String[]{"rightArm", "leftArm", "head", "torso", "foreign"})
            assertSame(nativeArm, CombatPitch.limit(part, nativeArm));
    }

    @Test void fullBasisIsPreservedAcrossTwistingSwingAndGimbalOrientations() {
        var random = new Random(701);
        for (int i = 0; i < 300; i++) {
            Vec3f body = new Vec3f(.5f + random.nextFloat(), random.nextFloat() - .5f, random.nextFloat() - .5f);
            Vec3f adjusted = new Vec3f(CombatPitch.UPWARD_LIMIT, body.getY(), body.getZ());
            Vec3f arm = new Vec3f(random.nextFloat() * 6 - 3, i < 2 ? (float) (Math.PI / 2) * (i == 0 ? 1 : -1)
                    : random.nextFloat() * 6 - 3, random.nextFloat() * 6 - 3);
            Vec3f compensated = CombatPitch.compensate(body, adjusted, arm);
            var original = rotation(body).mul(new Matrix3f().scaling(-1, -1, 1)).mul(rotation(arm));
            var actual = rotation(adjusted).mul(new Matrix3f().scaling(-1, -1, 1)).mul(rotation(compensated));
            float[] expected = original.get(new float[9]), result = actual.get(new float[9]);
            for (int j = 0; j < 9; j++) assertEquals(expected[j], result[j], 3e-5f, "basis element " + j);
        }
    }

    @Test void originalReadScopeRestoresThroughNestedReadsAndFailures() {
        var pose = new PartModifier(new Vec3f(1, 0, 0), Vec3f.ZERO, Vec3f.ZERO);
        var source = CombatPitch.source(part -> Optional.of(pose));
        assertNotSame(pose, source.apply("body").orElseThrow());
        assertThrows(IllegalStateException.class, () -> CombatPitch.original(() -> {
            assertSame(pose, source.apply("body").orElseThrow());
            CombatPitch.original(() -> { assertSame(pose, source.apply("body").orElseThrow()); return null; });
            assertSame(pose, source.apply("body").orElseThrow());
            throw new IllegalStateException("native failure");
        }));
        assertNotSame(pose, source.apply("body").orElseThrow());
        assertSame(pose.rotation(), CombatPitch.compensate(Vec3f.ZERO, Vec3f.ZERO, pose.rotation()));
        assertSame(pose.rotation(), CombatPitch.compensate(new Vec3f(Float.NaN, 0, 0), Vec3f.ZERO, pose.rotation()));
    }
}
