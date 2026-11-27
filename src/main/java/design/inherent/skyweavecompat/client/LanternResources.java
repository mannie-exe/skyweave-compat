/* SPDX-License-Identifier: Apache-2.0 */
package design.inherent.skyweavecompat.client;

import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.FilePackResources;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackSelectionConfig;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.event.AddPackFindersEvent;

/** Admit only the separately authored, qualified two-item resource bundle. */
public final class LanternResources {
    public static final String PACK_ID = "skyweave_compat/lantern-grip";
    // Updated only with the matching pack owner's immutable resource handoff.
    public static final String SHA256 = "d78995ed180cef318f049c6c765c00377b7cef34759ba9cd9b5bb586eef16aec";
    private static final ResourceLocation MARKER = ResourceLocation.parse("skyweave_compat:animation/lantern_grip.json");

    private LanternResources() {}

    public static void register(AddPackFindersEvent event) {
        if (event.getPackType() != PackType.CLIENT_RESOURCES) return;
        event.addRepositorySource(consumer -> {
            Path path = FMLPaths.CONFIGDIR.get().resolve("skyweave/animation-packs/lantern-grip.zip");
            if (!Files.isRegularFile(path)) return;
            try {
                if (!matches(path)) {
                    LogUtils.getLogger().error("Lantern grip resources were not loaded: {} does not match this Compat version. Restore matching modpack files or disable punchy_moves.lantern_grip and restart.", path);
                    return;
                }
                var pack = Pack.readMetaAndCreate(new PackLocationInfo(PACK_ID, Component.literal("Skyweave lantern grip"),
                        PackSource.BUILT_IN, Optional.empty()), new FilePackResources.FileResourcesSupplier(path),
                        PackType.CLIENT_RESOURCES, new PackSelectionConfig(true, Pack.Position.TOP, false));
                if (pack != null) consumer.accept(pack);
            } catch (IOException failure) {
                LogUtils.getLogger().error("Could not read lantern grip resources at {}. Restore matching modpack files or disable punchy_moves.lantern_grip and restart.", path, failure);
            }
        });
    }

    static boolean matches(Path path) throws IOException {
        try { return SHA256.equals(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)))); }
        catch (java.security.NoSuchAlgorithmException impossible) { throw new AssertionError(impossible); }
    }

    public static boolean owns(ItemStack stack) {
        String model = stack.is(Items.LANTERN) ? "lantern" : stack.is(Items.SOUL_LANTERN) ? "soul_lantern" : null;
        if (model == null) return false;
        var resources = Minecraft.getInstance().getResourceManager();
        return resources.getResource(MARKER).filter(r -> r.sourcePackId().equals(PACK_ID)).isPresent()
                && resources.getResource(ResourceLocation.parse("minecraft:items/" + model + ".json"))
                        .filter(r -> r.sourcePackId().equals(PACK_ID)).isPresent();
    }
}
