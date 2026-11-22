package design.inherent.skyweavecompat.configuration;

import com.electronwill.nightconfig.core.UnmodifiableConfig;
import java.util.ArrayList;
import java.util.List;
import net.neoforged.neoforge.common.ModConfigSpec;

/** Pure native field metadata shared by configuration declarations and the legacy comparison. */
public final class NativeFields {
    public record Field(ModConfigSpec.ConfigValue<?> binding) {
        public String key() { return String.join(".", binding.getPath()); }
        public ModConfigSpec.ValueSpec spec() { return binding.getSpec(); }
        public Object defaultValue() { return binding.getDefault(); }
        public SettingsCatalog.Timing timing(String filename) {
            var declared = SettingsCatalog.timing(filename, key());
            if (declared == SettingsCatalog.Timing.NEW_WORLD) return declared;
            return switch (spec().restartType()) {
                case NONE -> declared;
                case WORLD -> SettingsCatalog.Timing.SERVER_START;
                case GAME -> SettingsCatalog.Timing.GAME_RESTART;
            };
        }
        public List<String> choices() {
            if (defaultValue() instanceof Boolean) return List.of("false", "true");
            if (defaultValue() instanceof Enum<?> value) return java.util.Arrays.stream(value.getDeclaringClass().getEnumConstants()).map(Enum::name).toList();
            return List.of();
        }
        public SettingDefinition definition() {
            return definition("");
        }
        public SettingDefinition definition(String filename) {
            Object initial = defaultValue();
            Class<?> type = initial instanceof List<?> ? List.class : initial instanceof Enum<?> value ? value.getDeclaringClass() : initial.getClass();
            var range = spec().getRange();
            Number minimum = range != null && range.getMin() instanceof Number value ? value : null;
            Number maximum = range != null && range.getMax() instanceof Number value ? value : null;
            var constraints = SettingConstraints.nativeRange(minimum, maximum, initial instanceof Integer || initial instanceof Long,
                    String.join(".", binding.getPath().subList(0, binding.getPath().size() - 1)));
            if (spec() instanceof ModConfigSpec.ListValueSpec list && list.getSizeRange() != null)
                constraints = new SettingConstraints(constraints.minimum(), constraints.maximum(), false, constraints.step(), "", constraints.group(),
                        list.getSizeRange().getMax(), -1, false, "");
            String unit = switch (filename) {
                case "skyweave_compat-rendering-client.toml" -> key().equals("clouds.absolute") || key().equals("clouds.offset") ? "blocks" : "";
                case "skyweave_compat-common.toml" -> java.util.Set.of("minimumGap", "maximumGap", "minimumIslandRadius").contains(key()) ? "blocks" : "";
                default -> "";
            };
            if (!unit.isEmpty()) constraints = constraints.withUnit(unit);
            return new SettingDefinition(key(), type, initial, constraints);
        }
    }
    public static List<Field> collect(ModConfigSpec spec) {
        var fields = new ArrayList<Field>();
        collect(spec.getValues(), fields);
        return List.copyOf(fields);
    }

    private static void collect(UnmodifiableConfig values, List<Field> fields) {
        for (var value : values.valueMap().values()) {
            if (value instanceof ModConfigSpec.ConfigValue<?> binding) fields.add(new Field(binding));
            else if (value instanceof UnmodifiableConfig group) collect(group, fields);
        }
    }

    private NativeFields() {}
}
