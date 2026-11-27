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

    @Test void nativeEquipScalePositionAndNonfiniteFallbackArePreserved() {
        var arm = new ModelPart(List.of(), Map.of());
        arm.xRot = -1.3f; arm.yRot = .2f; arm.zRot = .7f;
        var source = new ItemPose(new Vector3f(.1f, .2f, .3f), new Vector3f(7, 8, 9), new Vector3f(.4f, .6f, .8f));
        var corrected = LanternGrip.pose(arm, source);
        assertSame(source.position(), corrected.position()); assertSame(source.scale(), corrected.scale());
        assertNotEquals(source.rotation(), corrected.rotation());
        assertEquals(new Vector3f(7, 8, 9), source.rotation());
        arm.yRot = Float.NaN;
        assertSame(source, LanternGrip.pose(arm, source));
    }
}
