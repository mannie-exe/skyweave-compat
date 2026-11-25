package design.inherent.skyweavecompat.client.model;

import java.util.function.Supplier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/** Actual model-evaluation context, not inferred from the camera or a matching item ID. */
public final class BowModelContext {
    public record Context(ItemStack stack, LivingEntity entity, ItemDisplayContext display) {}
    public record Origin(LivingEntity actor, net.minecraft.world.entity.HumanoidArm arm) {}
    private static final ThreadLocal<Origin> ORIGIN = new ThreadLocal<>();
    public static Origin origin() { return ORIGIN.get(); }
    public static void fromPunchy(LivingEntity actor, net.minecraft.world.entity.HumanoidArm arm, Runnable draw) {
        var previous = ORIGIN.get(); ORIGIN.set(new Origin(actor, arm));
        try { draw.run(); }
        finally { if (previous == null) ORIGIN.remove(); else ORIGIN.set(previous); }
    }
    private static final ThreadLocal<Context> CURRENT = new ThreadLocal<>();
    private BowModelContext() {}
    public static Context current() { return CURRENT.get(); }
    public static <T> T query(ItemStack stack, LivingEntity entity, ItemDisplayContext display, Supplier<T> operation) {
        var previous = CURRENT.get();
        CURRENT.set(new Context(stack, entity, display));
        try { return operation.get(); }
        finally { if (previous == null) CURRENT.remove(); else CURRENT.set(previous); }
    }
}
