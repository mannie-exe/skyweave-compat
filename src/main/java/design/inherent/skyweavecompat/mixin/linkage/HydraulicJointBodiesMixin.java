package design.inherent.skyweavecompat.mixin.linkage;

import com.enxv.aerouniversaljoint.content.HydraulicConnectionHeadBlockEntity;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.ryanhcode.sable.api.physics.PhysicsPipeline;
import dev.ryanhcode.sable.api.physics.PhysicsPipelineBody;
import dev.ryanhcode.sable.api.physics.constraint.GenericConstraintHandle;
import dev.ryanhcode.sable.api.physics.constraint.PhysicsConstraintConfiguration;
import dev.ryanhcode.sable.api.physics.constraint.PhysicsConstraintHandle;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import java.lang.ref.WeakReference;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

/** A native joint's frames may change; its participating bodies cannot be retargeted. */
@Mixin(value = HydraulicConnectionHeadBlockEntity.class, remap = false)
public abstract class HydraulicJointBodiesMixin {
    @Unique private WeakReference<PhysicsConstraintHandle> skyweave$joint;
    @Unique private WeakReference<PhysicsPipelineBody> skyweave$firstBody;
    @Unique private WeakReference<PhysicsPipelineBody> skyweave$secondBody;

    @WrapOperation(method = "sable$physicsTick", at = @At(value = "INVOKE", target =
            "Ldev/ryanhcode/sable/api/physics/PhysicsPipeline;addConstraint(Ldev/ryanhcode/sable/api/physics/PhysicsPipelineBody;Ldev/ryanhcode/sable/api/physics/PhysicsPipelineBody;Ldev/ryanhcode/sable/api/physics/constraint/PhysicsConstraintConfiguration;)Ldev/ryanhcode/sable/api/physics/constraint/PhysicsConstraintHandle;"),
            require = 1, allow = 1)
    private PhysicsConstraintHandle skyweave$recordBodies(PhysicsPipeline pipeline,
            PhysicsPipelineBody first, PhysicsPipelineBody second, PhysicsConstraintConfiguration configuration,
            Operation<PhysicsConstraintHandle> original) {
        var joint = original.call(pipeline, first, second, configuration);
        skyweave$joint = joint == null ? null : new WeakReference<>(joint);
        skyweave$firstBody = first == null ? null : new WeakReference<>(first);
        skyweave$secondBody = second == null ? null : new WeakReference<>(second);
        return joint;
    }

    @WrapOperation(method = "sable$physicsTick", at = @At(value = "INVOKE", target =
            "Ldev/ryanhcode/sable/api/physics/constraint/GenericConstraintHandle;isValid()Z"),
            require = 1, allow = 1)
    private boolean skyweave$reuseOnlyBoundBodies(GenericConstraintHandle joint, Operation<Boolean> original,
            @Local(index = 7) ServerSubLevel first, @Local(index = 8) ServerSubLevel second) {
        // Only this reuse decision is guarded. Native removal still sees the real validity.
        return original.call(joint) && skyweave$joint != null && skyweave$joint.get() == joint
                && skyweave$sameBody(skyweave$firstBody, first)
                && skyweave$sameBody(skyweave$secondBody, second);
    }

    @Unique private static boolean skyweave$sameBody(WeakReference<PhysicsPipelineBody> recorded, PhysicsPipelineBody current) {
        // Null is the native world endpoint; a collected body must not become that sentinel.
        return recorded == null ? current == null : current != null && recorded.get() == current;
    }
}
