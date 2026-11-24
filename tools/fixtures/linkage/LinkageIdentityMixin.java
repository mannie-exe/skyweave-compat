package linearreview.mixin;

import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import linearreview.LinkageIdentitySource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import java.util.UUID;

/** Substitute only the fixture's UUID input before native allocation/registration. */
@Mixin(value = SubLevelContainer.class, remap = false)
public abstract class LinkageIdentityMixin {
    @Redirect(method = "allocateNewSubLevel", at = @At(value = "INVOKE",
            target = "Ljava/util/UUID;randomUUID()Ljava/util/UUID;"), require = 1, allow = 1)
    private UUID fixture$identity() {
        return LinkageIdentitySource.next(UUID::randomUUID);
    }
}
