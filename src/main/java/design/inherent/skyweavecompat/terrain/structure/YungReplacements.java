package design.inherent.skyweavecompat.terrain.structure;

import design.inherent.skyweavecompat.configuration.YungReplacementBindings;
import java.util.Map;
import java.util.TreeMap;
import net.neoforged.fml.ModList;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.config.ModConfigs;
import net.neoforged.neoforge.common.ModConfigSpec;

/** Read the native validated bindings once per world; never parse or own another mod's files. */
public final class YungReplacements {
    public static boolean suppresses(Map<String, Boolean> snapshot, net.minecraft.world.level.levelgen.structure.Structure structure) {
        var type = net.minecraft.core.registries.BuiltInRegistries.STRUCTURE_TYPE.getKey(structure.type());
        return type != null && Boolean.TRUE.equals(snapshot.get(type.toString()));
    }
    public static Map<String, Boolean> capture() {
        var values = new TreeMap<String, Boolean>();
        for (var target : YungReplacementBindings.TARGETS) {
            if (!ModList.get().isLoaded(target.mod())) continue;
            var config = ModConfigs.getConfigSet(ModConfig.Type.COMMON).stream()
                    .filter(candidate -> candidate.getModId().equals(target.mod()) && candidate.getFileName().equals(target.file()))
                    .findFirst().orElseThrow(() -> new IllegalStateException("Missing registered YUNG config: " + target.file()));
            if (config.getLoadedConfig() == null || !(config.getSpec() instanceof ModConfigSpec spec))
                throw new IllegalStateException("YUNG config is not loaded: " + target.file());
            Object binding = spec.getValues().get(target.path());
            if (!(binding instanceof ModConfigSpec.ConfigValue<?> value) || !(value.get() instanceof Boolean flag))
                throw new IllegalStateException("Review changed YUNG config binding: " + target.file() + " " + target.path());
            values.put(target.nativeType(), flag);
        }
        return Map.copyOf(values);
    }
    private YungReplacements() {}
}
