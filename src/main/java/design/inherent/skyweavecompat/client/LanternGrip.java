/* SPDX-License-Identifier: Apache-2.0 */
package design.inherent.skyweavecompat.client;

import net.minecraft.client.model.geom.ModelPart;
import org.joml.Matrix3d;
import org.joml.Quaterniond;
import org.joml.Vector3f;
import punchymoves.client.animation.LocomotionController.ItemPose;

/** Read the final native arm; no sampled animation, arm mutation, or replacement animation clock. */
public final class LanternGrip {
    private LanternGrip() {}

    public static ItemPose pose(ModelPart arm, ItemPose nativePose) {
        Vector3f rotation = counterRotation(arm.xRot, arm.yRot, arm.zRot);
        // Managed wrappers place the handle at the native mount. The authored item-bone
        // offset would move that handle a second time; retain the live equip scale only.
        return rotation == null ? nativePose : new ItemPose(new Vector3f(), rotation, nativePose.scale());
    }

    static Vector3f counterRotation(float x, float y, float z) {
        if (!Float.isFinite(x) || !Float.isFinite(y) || !Float.isFinite(z)) return null;
        var fixed = new Matrix3d().rotateX(-Math.PI / 2).rotateY(Math.PI);
        var arm = new Matrix3d().rotation(new Quaterniond().rotationZYX(z, y, x));
        var target = new Matrix3d(fixed).transpose().mul(arm.transpose()).mul(fixed);
        double cosineY = Math.hypot(target.m00(), target.m01());
        double beta = Math.atan2(-target.m02(), cosineY);
        double alpha = cosineY > 1e-6 ? Math.atan2(target.m12(), target.m22()) : Math.atan2(-target.m21(), target.m11());
        double gamma = cosineY > 1e-6 ? Math.atan2(target.m01(), target.m00()) : 0;
        // Equivalent Euler branches differ in native float cosFromSin stability near half-turns.
        // Prefer the branch whose largest absolute angle is smaller; do not change the rotation.
        double alternateX = wrap(alpha + Math.PI), alternateY = wrap(Math.PI - beta), alternateZ = wrap(gamma + Math.PI);
        if (Math.max(Math.abs(alternateX), Math.max(Math.abs(alternateY), Math.abs(alternateZ)))
                < Math.max(Math.abs(alpha), Math.max(Math.abs(beta), Math.abs(gamma)))) {
            alpha = alternateX; beta = alternateY; gamma = alternateZ;
        }
        // Native HeldItemAnimation consumes degrees in the order Rz(-y) Ry(-z) Rx(-x).
        return new Vector3f((float) -Math.toDegrees(alpha), (float) -Math.toDegrees(gamma), (float) -Math.toDegrees(beta));
    }
    private static double wrap(double angle) {
        return Math.atan2(Math.sin(angle), Math.cos(angle));
    }
}
