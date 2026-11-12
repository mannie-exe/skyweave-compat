package design.inherent.skyweavecompat.configuration;

import java.math.BigDecimal;

/** Common constraints; owned annotations and admitted native specs supply their own metadata. */
public record SettingConstraints(BigDecimal minimum, BigDecimal maximum, boolean exclusiveMinimum,
        BigDecimal step, String unit, String group, int maximumEntries, int maximumLength,
        boolean unique, String pattern, boolean nonBlank) {
    public SettingConstraints(BigDecimal minimum, BigDecimal maximum, boolean exclusiveMinimum,
            BigDecimal step, String unit, String group, int maximumEntries, int maximumLength, boolean unique, String pattern) {
        this(minimum,maximum,exclusiveMinimum,step,unit,group,maximumEntries,maximumLength,unique,pattern,false);
    }
    public static final SettingConstraints NONE = new SettingConstraints(null, null, false, BigDecimal.ONE, "", "", -1, -1, false, "");
    public SettingConstraints {
        if (step.signum() <= 0 || minimum != null && maximum != null && minimum.compareTo(maximum) > 0)
            throw new IllegalArgumentException("Invalid setting constraints");
    }
    public static SettingConstraints from(SettingSpec spec) {
        return spec == null ? NONE : new SettingConstraints(number(spec.minimum()), number(spec.maximum()), spec.exclusiveMinimum(),
                spec.step().isEmpty() ? BigDecimal.ONE : new BigDecimal(spec.step()), spec.unit(), spec.group(),
                spec.maximumEntries(), spec.maximumLength(), spec.unique(), spec.pattern(), spec.nonBlank());
    }
    private static BigDecimal number(String value) { return value.isEmpty() ? null : new BigDecimal(value); }
    public static SettingConstraints nativeRange(Number minimum, Number maximum, boolean integer, String group) {
        return new SettingConstraints(minimum == null ? null : new BigDecimal(minimum.toString()),
                maximum == null ? null : new BigDecimal(maximum.toString()), false,
                integer ? BigDecimal.ONE : new BigDecimal(".1"), "", group, -1, -1, false, "");
    }
}
