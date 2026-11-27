package design.inherent.skyweavecompat.client;

import java.util.List;
import java.util.Map;
import java.util.Random;
import net.minecraft.client.model.geom.ModelPart;
import org.joml.Matrix3f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import punchymoves.client.animation.LocomotionController.ItemPose;
import static org.junit.jupiter.api.Assertions.*;

class LanternGripTest {
    @Test void inverseUsesActualArmBasisIncludingBothHandAndMovingOrientations() {
        var random = new Random(213);
        var fixed = new Matrix3f().rotateX((float) -Math.PI / 2).rotateY((float) Math.PI);
        for (int i = 0; i < 400; i++) {
            float x = random.nextFloat() * 6 - 3, y = random.nextFloat() * 6 - 3, z = random.nextFloat() * 6 - 3;
            Vector3f stored = LanternGrip.counterRotation(x, y, z);
            var consumed = new Matrix3f().rotation(new Quaternionf().rotationZYX(
                    (float) -Math.toRadians(stored.y), (float) -Math.toRadians(stored.z), (float) -Math.toRadians(stored.x)));
            var finalBasis = new Matrix3f().rotation(new Quaternionf().rotationZYX(z, y, x)).mul(fixed).mul(consumed);
            float[] actual = finalBasis.get(new float[9]), expected = fixed.get(new float[9]);
            for (int j = 0; j < 9; j++) assertEquals(expected[j], actual[j], 3e-5f, "basis element " + j + " case=" + i + " arm=" + x + "," + y + "," + z
                    + " stored=" + stored + " fastmath=" + System.getProperty("joml.fastmath"));
        }
        for (int sign : new int[]{1, -1}) {
            Vector3f settled = LanternGrip.counterRotation((float) -Math.PI / 2, 0, sign * (float) Math.PI / 4);
            assertEquals(90, settled.x, .001); assertEquals(sign * 45, settled.y, .001);
        }
    }

    @Test void managedHandleStaysAtNativeMountAcrossItemOffsetsAndEquipScales() {
        // Native production control: main offsets +/-7.5,0,7.5 displace the handle by
        // 0.62148 render units after the player's .9375 scale. Offhand offset is zero.
        var arm = new ModelPart(List.of(), Map.of());
        arm.xRot = (float) -Math.PI / 2; arm.zRot = (float) Math.PI / 4;
        for (float side : new float[]{-1, 0, 1}) {
            for (float scale : new float[]{.4993f, .75f, 1}) {
                var offset = new Vector3f(side * 7.5f, 0, side == 0 ? 0 : 7.5f);
                var source = new ItemPose(new Vector3f(offset), new Vector3f(), new Vector3f(scale));
                var corrected = LanternGrip.pose(arm, source);
                var appliedTranslation = new Vector3f(-corrected.position().x / 16,
                        -corrected.position().z / 16, corrected.position().y / 16).mul(.9375f);
                assertEquals(new Vector3f(), corrected.position());
                assertEquals(0f, appliedTranslation.length(), 0f, "managed handle leaves native mount");
                assertSame(source.scale(), corrected.scale());
                assertEquals(offset, source.position(), "native pose is borrowed, never mutated");
            }
        }
    }

    @Test void nativeEquipScaleAndNonfiniteFallbackArePreserved() {
        var arm = new ModelPart(List.of(), Map.of());
        arm.xRot = -1.3f; arm.yRot = .2f; arm.zRot = .7f;
        var source = new ItemPose(new Vector3f(.1f, .2f, .3f), new Vector3f(7, 8, 9), new Vector3f(.4f, .6f, .8f));
        var corrected = LanternGrip.pose(arm, source);
        assertEquals(new Vector3f(), corrected.position()); assertSame(source.scale(), corrected.scale());
        assertEquals(new Vector3f(.1f, .2f, .3f), source.position());
        assertNotEquals(source.rotation(), corrected.rotation());
        assertEquals(new Vector3f(7, 8, 9), source.rotation());
        arm.yRot = Float.NaN;
        assertSame(source, LanternGrip.pose(arm, source));
    }
}
