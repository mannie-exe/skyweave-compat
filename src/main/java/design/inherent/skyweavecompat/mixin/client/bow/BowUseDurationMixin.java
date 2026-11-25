package design.inherent.skyweavecompat.mixin.client.bow;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import design.inherent.skyweavecompat.client.model.BowModelContext;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import punchy.client.animation.PunchyAnimationManager;
import punchy.config.PunchyConfig;
import timmychips.modefiteitemdefinitions.property.type.codec.RangeDispatchDefinition;

/** Only the visible first-person mesh waits for the animation's own handoff event. */
@Mixin(targets = "timmychips.modefiteitemdefinitions.property.resolver.rangeentry.UseDurationFloat", remap = false)
abstract class BowUseDurationMixin {
    @WrapMethod(method = "getValue")
    private float skyweave$handoff(ItemStack stack, LivingEntity entity, RangeDispatchDefinition.Definition definition,
                                   Operation<Float> original) {
        var context = BowModelContext.current();
        var client = Minecraft.getInstance();
        if (context != null && context.stack() == stack && context.entity() == entity
                && client.player != null && entity == client.player
                && client.options.getCameraType() == CameraType.FIRST_PERSON
                && stack.getItem() instanceof BowItem && entity.isUsingItem() && entity.getUseItem() == stack
                && PunchyConfig.isModEnabled() && !PunchyConfig.isItemBlacklisted(stack)
                && !PunchyConfig.isHandBlacklisted(client.player, client.player.getUsedItemHand())) {
            HumanoidArm arm = client.player.getUsedItemHand() == InteractionHand.MAIN_HAND
                    ? client.player.getMainArm() : client.player.getMainArm().getOpposite();
            ItemDisplayContext expected = arm == HumanoidArm.RIGHT ? ItemDisplayContext.FIRST_PERSON_RIGHT_HAND
                    : ItemDisplayContext.FIRST_PERSON_LEFT_HAND;
            var pose = PunchyAnimationManager.POSE_HANDLER;
            var clip = pose.getCurrentClip();
            var origin = BowModelContext.origin();
            ItemDisplayContext punchyDisplay = arm == HumanoidArm.RIGHT ? ItemDisplayContext.THIRD_PERSON_RIGHT_HAND
                    : ItemDisplayContext.THIRD_PERSON_LEFT_HAND;
            boolean firstPersonDraw = origin != null && origin.actor() == entity && origin.arm() == arm
                    && (context.display() == expected || context.display() == punchyDisplay);
            if (firstPersonDraw && pose.isPlaying() && clip != null
                    && PunchyAnimationManager.isClipNamed(clip, "use_bow")) {
                boolean declared = false, handedOff = false;
                for (String event : new String[]{"start_mesh_animation", "loadstart", "load_start"}) {
                    declared |= clip.hasEffectAny(event);
                    handedOff |= clip.isEffectActive(event, pose.getCurrentTime());
                }
                if (declared && !handedOff) return 0;
            }
        }
        // Resume the native visual draw value, not another charge clock or a rebased server duration.
        return original.call(stack, entity, definition);
    }
}
