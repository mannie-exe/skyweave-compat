package design.inherent.skyweavecompat.client;

import design.inherent.skyweavecompat.presentation.PackIdentity;
import net.minecraft.network.chat.Component;
import net.neoforged.fml.ModList;

/** Called only when NeoForge rebuilds its branding cache, including resource/language reloads. */
public final class MainMenuIdentity {
    private MainMenuIdentity() {}

    public static String line() {
        var mods = ModList.get();
        var compat = mods.getModContainerById("skyweave_compat").orElseThrow();
        String version = compat.getModInfo().getVersion().toString();
        return PackIdentity.installed(mods)
                .map(pack -> Component.translatable("skyweave_compat.branding.pack", pack.version(), version).getString())
                .orElseGet(() -> Component.translatable("skyweave_compat.branding.standalone", version).getString());
    }
}
