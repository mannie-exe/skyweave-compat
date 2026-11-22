package design.inherent.skyweavecompat.client.input;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.client.settings.KeyMappingLookup;

/** Queries the native lookup, including its modified-owner priority, without consuming clicks. */
public final class NativeInputAdmission {
    // Native lookup instances share the loader's registered map; no second index or scan is built.
    private static final KeyMappingLookup LOOKUP = new KeyMappingLookup();
    private NativeInputAdmission() {}

    public static boolean keyboard(KeyMapping mapping, int key, int scan) {
        return Minecraft.getInstance().isWindowActive()
                && LOOKUP.getAll(InputConstants.getKey(key, scan)).contains(mapping);
    }

    public static boolean active(KeyMapping mapping) {
        return Minecraft.getInstance().isWindowActive() && LOOKUP.getAll(mapping.getKey()).contains(mapping);
    }
}
