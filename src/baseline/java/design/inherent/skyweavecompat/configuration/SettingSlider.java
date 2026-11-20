package design.inherent.skyweavecompat.configuration;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Presentation scale over an existing bounded probability definition; parsing remains with that definition. */
public record SettingSlider(SettingDefinition definition) {
    public SettingSlider {
        if (!supports(definition)) throw new IllegalArgumentException("No probability slider for this field");
    }
    public static boolean supports(SettingDefinition definition) {
        return definition.kind() == SettingDefinition.Kind.DECIMAL && definition.unit().equals("probability")
                && definition.minimum() != null && definition.maximum() != null
                && definition.minimum().signum() >= 0 && definition.maximum().compareTo(BigDecimal.ONE) <= 0
                && definition.minimum().compareTo(definition.maximum()) < 0 && !definition.constraints().exclusiveMinimum();
    }
    public double fraction(String raw) {
        // Parse through the actual numeric type first. Compact exponents must not expand into giant decimals.
        double number = ((Number) definition.parse(raw)).doubleValue();
        return Math.clamp((number - definition.minimum().doubleValue())
                / (definition.maximum().doubleValue() - definition.minimum().doubleValue()), 0, 1);
    }
    public String text(double fraction) {
        if (!Double.isFinite(fraction)) throw new IllegalArgumentException("Invalid slider position");
        var minimum = definition.minimum(); var maximum = definition.maximum();
        BigDecimal value;
        if (fraction <= 0) value = minimum;
        else if (fraction >= 1) value = maximum;
        else {
            var raw = minimum.add(maximum.subtract(minimum).multiply(BigDecimal.valueOf(fraction)));
            value = raw.subtract(minimum).divide(definition.step(), 0, RoundingMode.HALF_UP)
                    .multiply(definition.step()).add(minimum).max(minimum).min(maximum);
        }
        String text = value.stripTrailingZeros().toPlainString(); definition.parse(text); return text;
    }
}
