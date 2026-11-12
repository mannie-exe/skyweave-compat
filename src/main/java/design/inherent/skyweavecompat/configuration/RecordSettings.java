package design.inherent.skyweavecompat.configuration;

import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Bounded owned-record inspection, independent of game and optional integration classes. */
public final class RecordSettings {
    private static final ClassValue<List<RecordComponent>> COMPONENTS = new ClassValue<>() {
        @Override protected List<RecordComponent> computeValue(Class<?> type) {
            if (!type.isRecord()) throw new IllegalArgumentException("Expected an owned record");
            return List.of(type.getRecordComponents());
        }
    };
    public static void validate(Class<? extends Record> type, Object... values) {
        var components = COMPONENTS.get(type);
        if (values.length != components.size()) throw new IllegalArgumentException("Wrong record field count for " + type);
        for (int i = 0; i < values.length; i++) {
            var component = components.get(i);
            if (component.getType().isRecord() || component.getType() == Map.class) continue;
            new SettingDefinition(component.getName(), component.getType(), values[i], component.getAnnotation(SettingSpec.class)).validate(values[i]);
        }
    }
    public static void validateField(Class<? extends Record> type, String key, Object value) {
        var component = COMPONENTS.get(type).stream().filter(field -> field.getName().equals(key)).findFirst().orElseThrow();
        new SettingDefinition(key, component.getType(), value, component.getAnnotation(SettingSpec.class)).validate(value);
    }
    public static List<SettingDefinition> fields(Record defaults) {
        var fields = new ArrayList<SettingDefinition>(); collect(defaults, "", fields); return List.copyOf(fields);
    }
    private static void collect(Object value, String prefix, List<SettingDefinition> fields) {
        if (value instanceof Map<?, ?> map) {
            map.forEach((key, item) -> {
                if (!(key instanceof String text) || text.contains(".")) throw new IllegalArgumentException("Unsupported settings map key");
                collect(item, prefix + key + ".", fields);
            }); return;
        }
        if (!(value instanceof Record record)) throw new IllegalArgumentException("Expected a nested owned record");
        for (var component : COMPONENTS.get(record.getClass())) try {
            Object initial = component.getAccessor().invoke(record);
            String key = prefix + component.getName();
            if (initial instanceof Record || initial instanceof Map<?, ?>) collect(initial, key + ".", fields);
            else fields.add(new SettingDefinition(key, component.getType(), initial, component.getAnnotation(SettingSpec.class)));
        } catch (ReflectiveOperationException failure) { throw new IllegalArgumentException("Cannot inspect owned field " + component.getName(), failure); }
    }
    private RecordSettings() {}
}
