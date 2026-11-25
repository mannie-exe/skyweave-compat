package design.inherent.skyweavecompat.clientprobe.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import design.inherent.skyweavecompat.clientprobe.ClientBowHandoffProbe;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import timmychips.modefiteitemdefinitions.property.type.codec.RangeDispatchDefinition;

/** Observe the actual registry result; never supply missing draw context or change the value. */
@Mixin(targets="timmychips.modefiteitemdefinitions.property.registry.RangePropertyRegistry",remap=false)
abstract class BowDurationWitnessMixin {
    @WrapMethod(method="resolve")
    private static float bow$observe(ResourceLocation property,ItemStack stack,LivingEntity entity,
            RangeDispatchDefinition.Definition definition,Operation<Float> original) {
        float value=original.call(property,stack,entity,definition);
        ClientBowHandoffProbe.observeDuration(property,stack,value);
        return value;
    }
}
