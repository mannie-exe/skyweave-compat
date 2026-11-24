package design.inherent.skyweavecompat.client;

import design.inherent.skyweavecompat.mixin.client.TouchMotionCapeInvoker;
import java.util.UUID;
import java.util.function.Function;
import net.minecraft.client.model.geom.ModelPart;
import org.joml.Matrix4f;

/** Public reflection facade for Moves 1.0.9b, backed by the existing Touch'n Motion 1.0.5 owner. */
public final class MovesTouchMotionCape {
    private MovesTouchMotionCape() {}

    public static Matrix4f pose(ModelPart torso) { return TouchMotionCapeInvoker.skyweave$pose(torso); }

    public static void keep(UUID player, Matrix4f before, ModelPart after) {
        TouchMotionCapeInvoker.skyweave$keep(player, before, after);
    }

    public static void carry(UUID player, Function<String, ModelPart> parts, Matrix4f torso, Matrix4f unused) {
        // The pinned Moves caller always passes null here. No speculative fourth-matrix conversion.
        if (unused != null) throw new IllegalArgumentException("Unsupported Moves cape override");
        TouchMotionCapeInvoker.skyweave$carry(player, parts, torso);
    }
}
