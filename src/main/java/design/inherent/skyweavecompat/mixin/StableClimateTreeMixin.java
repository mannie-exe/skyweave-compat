package design.inherent.skyweavecompat.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import design.inherent.skyweavecompat.climate.NativeClimateIndex;
import net.minecraft.world.level.biome.Climate;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Only a tree created for our private index changes cache semantics. */
@Mixin(targets="net.minecraft.world.level.biome.Climate$RTree")
public abstract class StableClimateTreeMixin {
    @Shadow @Final private ThreadLocal<?> lastResult;
    @Unique private boolean skyweave$stable;
    @Inject(method="<init>",at=@At("RETURN"))
    private void skyweave$bind(CallbackInfo callback) { skyweave$stable=NativeClimateIndex.creating(); }
    @WrapMethod(method="search")
    private Object skyweave$search(Climate.TargetPoint point,@Coerce Object metric,Operation<Object> original) {
        if(!skyweave$stable)return original.call(point,metric);
        lastResult.remove();
        try { return original.call(point,metric); }
        finally { lastResult.remove(); }
    }
}
