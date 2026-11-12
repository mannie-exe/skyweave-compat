package design.inherent.skyweavecompat.mixin.water;

import com.AdamNew.dynamicwaters.SableCompat;
import design.inherent.skyweavecompat.climate.IslandClimate;
import design.inherent.skyweavecompat.water.IslandWaterShips;
import design.inherent.skyweavecompat.water.ServerWaterContainer;
import com.llamalad7.mixinextras.injector.wrapoperation.*;
import dev.ryanhcode.sable.Sable;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Avoid upstream's reflection across a client-only overload on dedicated servers. */
@Mixin(value = SableCompat.class, remap = false)
public abstract class WaterSableMixin {
    @WrapOperation(method="initPhysics",at=@At(value="INVOKE",target="Ljava/lang/Class;getMethod(Ljava/lang/String;[Ljava/lang/Class;)Ljava/lang/reflect/Method;"))
    private static java.lang.reflect.Method serverContainerMethod(Class<?> type,String name,Class<?>[] parameters,
            Operation<java.lang.reflect.Method> original) throws NoSuchMethodException {
        if(type.getName().equals("dev.ryanhcode.sable.api.sublevel.SubLevelContainer"))
            return ServerWaterContainer.class.getMethod(name,parameters);
        return original.call(type,name,parameters);
    }
    @Inject(method = "getGlobalPos", at = @At("HEAD"), cancellable = true)
    private static void entity(Entity entity, CallbackInfoReturnable<Vec3> result) {
        if (IslandClimate.applies(entity.level()) && Sable.HELPER.getContaining(entity) != null)
            result.setReturnValue(Sable.HELPER.projectOutOfSubLevel(entity.level(), entity.position()));
    }
    @Inject(method = "getGlobalBlockPos", at = @At("HEAD"), cancellable = true)
    private static void block(BlockGetter blocks, BlockPos pos, CallbackInfoReturnable<BlockPos> result) {
        if (blocks instanceof Level level && IslandClimate.applies(level) && Sable.HELPER.getContaining(level, pos) != null)
            result.setReturnValue(BlockPos.containing(Sable.HELPER.projectOutOfSubLevel(level, pos.getCenter())));
    }
    @Inject(method = "tickPhysicsShips", at = @At("HEAD"), cancellable = true)
    private static void currents(ServerLevel level, CallbackInfo callback) {
        if (!IslandClimate.applies(level)) return;
        IslandWaterShips.tick(level);
        callback.cancel();
    }
}
