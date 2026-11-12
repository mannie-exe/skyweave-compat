package design.inherent.skyweavecompat.water;

import com.AdamNew.dynamicwaters.Config;
import com.electronwill.nightconfig.core.UnmodifiableConfig;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.util.TreeMap;
import net.neoforged.neoforge.common.ModConfigSpec;

/** Generation inputs only; audio, render options and force multipliers do not change terrain identity. */
public final class WaterIdentity {
    public static final String ALGORITHM = "island_watersheds_v5";
    private WaterIdentity() {}
    public static JsonObject inputs() {
        var result = new JsonObject();
        result.addProperty("adapter", ALGORITHM);
        result.addProperty("dependency", "13.1.0");
        UnmodifiableConfig generation = Config.SPEC.getValues().get("Generation");
        if (generation == null) throw new IllegalStateException("Dynamic Waters generation settings are unavailable");
        var values = new TreeMap<String, Object>();
        collect(generation, "", values);
        result.add("settings", new Gson().toJsonTree(values));
        return result;
    }
    private static void collect(UnmodifiableConfig config, String prefix, TreeMap<String, Object> result) {
        config.valueMap().forEach((key, value) -> {
            if (value instanceof ModConfigSpec.ConfigValue<?> binding) result.put(prefix + key, binding.get());
            else if (value instanceof UnmodifiableConfig nested) collect(nested, prefix + key + ".", result);
            else throw new IllegalStateException("Unexpected watershed setting " + prefix + key);
        });
    }
}
