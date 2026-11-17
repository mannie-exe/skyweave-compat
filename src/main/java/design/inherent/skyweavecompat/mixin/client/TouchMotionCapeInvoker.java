package design.inherent.skyweavecompat.mixin.client;

import java.util.UUID;
import java.util.function.Function;
import net.minecraft.client.model.geom.ModelPart;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Exact 1.0.5 methods, keeping all matrix computation and frame ownership in Touch'n Motion. */
@Mixin(targets = "strm.touchnmotion.cape.CapeAndElytra", remap = false)
public interface TouchMotionCapeInvoker {
    @Invoker("pose")
    static Matrix4f skyweave$pose(ModelPart part) { throw new AssertionError("Cape API mixin was not applied"); }

    @Invoker("keep")
    static void skyweave$keep(UUID player, Matrix4f before, ModelPart after) {
        throw new AssertionError("Cape API mixin was not applied");
    }

    @Invoker("carry")
    static void skyweave$carry(UUID player, Function<String, ModelPart> parts, Matrix4f torso) {
        throw new AssertionError("Cape API mixin was not applied");
    }
}
