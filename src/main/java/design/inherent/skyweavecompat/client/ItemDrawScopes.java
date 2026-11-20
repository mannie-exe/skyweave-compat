package design.inherent.skyweavecompat.client;

import java.util.Objects;
import java.util.function.Function;
import net.minecraft.world.item.ItemStack;

/** Carries the selected definition leaf across a deferred draw without linking the optional owners. */
public final class ItemDrawScopes {
    public interface Scope extends AutoCloseable { @Override void close(); }
    public interface Replay { Scope open(ItemStack stack); }
    private static volatile Function<ItemStack, Replay> capture = stack -> null;
    private ItemDrawScopes() {}

    public static void register(Function<ItemStack, Replay> provider) { capture = Objects.requireNonNull(provider); }
    public static Replay capture(ItemStack stack) { return capture.apply(stack); }
}
