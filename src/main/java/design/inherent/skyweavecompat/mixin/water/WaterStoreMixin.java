package design.inherent.skyweavecompat.mixin.water;

import com.AdamNew.dynamicwaters.async.MountainRegionStore;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import design.inherent.skyweavecompat.terrain.GenerationIdentity;
import design.inherent.skyweavecompat.water.IslandWaterTerrain;
import design.inherent.skyweavecompat.water.WaterIdentity;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.storage.LevelResource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = MountainRegionStore.class, remap = false)
public abstract class WaterStoreMixin {
    @ModifyReturnValue(method = "stampFor", at = @At("RETURN"))
    private static long identity(long upstream, ServerLevel level) throws Exception {
        if (!IslandWaterTerrain.applies(level.getChunkSource().getGenerator())) return upstream;
        var marker = level.getServer().getWorldPath(LevelResource.ROOT).resolve(GenerationIdentity.FILE);
        if (!Files.isRegularFile(marker)) throw new IllegalStateException("Skyweave generation identity must precede the watershed store");
        var digest = MessageDigest.getInstance("SHA-256");
        digest.update(ByteBuffer.allocate(Long.BYTES).putLong(upstream).array());
        digest.update(WaterIdentity.ALGORITHM.getBytes(StandardCharsets.UTF_8));
        digest.update(Files.readAllBytes(marker));
        return ByteBuffer.wrap(digest.digest()).getLong();
    }
}
