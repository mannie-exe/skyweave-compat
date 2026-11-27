/* SPDX-License-Identifier: Apache-2.0 */
package design.inherent.skyweavecompat.client;

import dev.kosmx.playerAnim.api.TransformType;
import dev.kosmx.playerAnim.api.firstPerson.FirstPersonMode;
import dev.kosmx.playerAnim.api.layered.modifier.AdjustmentModifier;
import dev.kosmx.playerAnim.core.impl.AnimationProcessor;
import dev.kosmx.playerAnim.core.util.Vec3f;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;
import org.joml.Matrix3f;
import org.joml.Quaternionf;

/** Limit only Better Combat's added upward lean, retaining the composed native arm basis. */
public final class CombatPitch {
    static final float UPWARD_LIMIT = (float) Math.toRadians(20);
    private static final ThreadLocal<Boolean> ORIGINAL = new ThreadLocal<>();

    private CombatPitch() {}

    public static Function<String, Optional<AdjustmentModifier.PartModifier>> source(
            Function<String, Optional<AdjustmentModifier.PartModifier>> nativeSource) {
        return part -> {
            var value = nativeSource.apply(part);
            if (Boolean.TRUE.equals(ORIGINAL.get()) || FirstPersonMode.isFirstPersonPass()) return value;
            return value.map(pose -> limit(part, pose));
        };
    }

    static AdjustmentModifier.PartModifier limit(String part, AdjustmentModifier.PartModifier pose) {
        if (!part.equals("body") && !part.equals("rightLeg") && !part.equals("leftLeg")) return pose;
        var r = pose.rotation();
        if (!finite(r) || r.getX() <= UPWARD_LIMIT) return pose;
        return new AdjustmentModifier.PartModifier(new Vec3f(UPWARD_LIMIT, r.getY(), r.getZ()), pose.scale(), pose.offset());
    }

    public static Vec3f arm(AnimationProcessor processor, String part, TransformType type, Vec3f input, Vec3f nativeArm) {
        if (type != TransformType.ROTATION || FirstPersonMode.isFirstPersonPass()
                || !part.equals("rightArm") && !part.equals("leftArm")) return nativeArm;
        Vec3f adjustedBody = processor.get3DTransform("body", type, Vec3f.ZERO);
        Vec3f originalBody = original(() -> processor.get3DTransform("body", type, Vec3f.ZERO));
        if (adjustedBody.equals(originalBody)) return nativeArm;
        Vec3f originalArm = original(() -> processor.get3DTransform(part, type, input));
        return compensate(originalBody, adjustedBody, originalArm);
    }

    static <T> T original(Supplier<T> read) {
        Boolean previous = ORIGINAL.get();
        ORIGINAL.set(true);
        try { return read.get(); }
        finally {
            if (previous == null) ORIGINAL.remove(); else ORIGINAL.set(previous);
        }
    }

    static Vec3f compensate(Vec3f originalBody, Vec3f adjustedBody, Vec3f arm) {
        if (originalBody.equals(adjustedBody) || !finite(originalBody) || !finite(adjustedBody) || !finite(arm)) return arm;
        // Native global body ZYX precedes Minecraft's model reflection and the local arm ZYX.
        // Preserve B_new * S * A_new = B_old * S * A_old, including twisting swings.
        var target = new Matrix3f().scaling(-1, -1, 1)
                .mul(rotation(adjustedBody).transpose()).mul(rotation(originalBody))
                .mul(new Matrix3f().scaling(-1, -1, 1)).mul(rotation(arm));
        double cosineY = Math.hypot(target.m00(), target.m01());
        float y = (float) Math.atan2(-target.m02(), cosineY);
        float x = cosineY > 1e-6 ? (float) Math.atan2(target.m12(), target.m22())
                : (float) Math.atan2(-target.m21(), target.m11());
        float z = cosineY > 1e-6 ? (float) Math.atan2(target.m01(), target.m00()) : 0;
        return new Vec3f(x, y, z);
    }

    private static Matrix3f rotation(Vec3f v) {
        return new Matrix3f().rotation(new Quaternionf().rotationZYX(v.getZ(), v.getY(), v.getX()));
    }

    private static boolean finite(Vec3f v) {
        return Float.isFinite(v.getX()) && Float.isFinite(v.getY()) && Float.isFinite(v.getZ());
    }
}
