package design.inherent.skyweavecompat.mixin.client.input;

import net.minecraft.client.KeyMapping;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Release only a retired Moves-owned mapping, using the native held/click cleanup. */
@Mixin(KeyMapping.class)
public interface MovesKeyReleaseAccess {
    @Invoker("release") void skyweave$releaseMovesBinding();
}
