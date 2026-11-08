package design.inherent.skyweavecompat.terrain;

import net.minecraft.network.chat.Component;
import java.util.*;

/** Server messages remain readable when a recipient lacks matching client language assets. */
public final class LocatorText {
    private static final Map<String, String> ENGLISH = english();
    private LocatorText() {}
    public static Component text(String domain, String key, Object... args) {
        String full = "skyweave_compat." + domain + "." + key;
        return Component.translatableWithFallback(full, Objects.requireNonNull(ENGLISH.get(full), full), args);
    }
    private static Map<String, String> english() {
        try (var stream = LocatorText.class.getResourceAsStream("/assets/skyweave_compat/lang/en_us.json")) {
            if (stream == null) throw new IllegalStateException("Locator language is missing");
            var object = com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(stream,
                    java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
            var result = new HashMap<String, String>();
            object.entrySet().forEach(entry -> result.put(entry.getKey(), entry.getValue().getAsString()));
            return Map.copyOf(result);
        } catch (java.io.IOException failure) { throw new IllegalStateException("Cannot read locator language", failure); }
    }
}
