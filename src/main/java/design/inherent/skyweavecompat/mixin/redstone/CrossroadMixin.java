package design.inherent.skyweavecompat.mixin.redstone;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import design.inherent.skyweavecompat.redstone.SignalRead;
import dev.siliconcarbidecube.create_redstone_additions.blocks.Crossroad;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Keep the native Crossroad feedback guard local to the querying thread and world. */
@Mixin(value = Crossroad.class, remap = false)
public abstract class CrossroadMixin {
    @Unique private final ThreadLocal<SignalRead> skyweave$reading = new ThreadLocal<>();

    @WrapMethod(method = "getInputSignalIgnoringOwnOutput")
    private int skyweave$scopeInput(Level level, BlockPos pos, BlockState state, boolean flank, Operation<Integer> original) {
        SignalRead previous = skyweave$reading.get();
        skyweave$reading.set(new SignalRead(level, pos.immutable()));
        try {
            return original.call(level, pos, state, flank);
        } finally {
            if (previous == null) skyweave$reading.remove();
            else skyweave$reading.set(previous);
        }
    }

    @Redirect(method = "getInputSignalIgnoringOwnOutput", at = @At(value = "FIELD",
            target = "Ldev/siliconcarbidecube/create_redstone_additions/blocks/Crossroad;readingPos:Lnet/minecraft/core/BlockPos;",
            opcode = Opcodes.PUTFIELD), require = 3, allow = 3)
    private void skyweave$skipGlobalWrite(Crossroad instance, BlockPos pos) {}

    @Redirect(method = "getDirectSignal", at = @At(value = "FIELD",
            target = "Ldev/siliconcarbidecube/create_redstone_additions/blocks/Crossroad;readingPos:Lnet/minecraft/core/BlockPos;",
            opcode = Opcodes.GETFIELD), require = 1, allow = 1)
    private BlockPos skyweave$positionInWorld(Crossroad instance, BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        SignalRead reading = skyweave$reading.get();
        return reading != null && reading.level() == level ? reading.pos() : null;
    }
}
