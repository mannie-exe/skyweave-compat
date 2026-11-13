package design.inherent.skyweavecompat.configuration;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SettingSliderTest {
    private SettingDefinition definition(String maximum, String step, String unit) {
        return new SettingDefinition("chance", double.class, .5,
                new SettingConstraints(BigDecimal.ZERO, new BigDecimal(maximum), false, new BigDecimal(step), unit, "", -1, -1, false, ""));
    }
    @Test void pointerQuantizationAndExactInputUseOneDefinitionWithoutRoundingTypedValues() {
        var scale = new SettingSlider(definition("1", ".01", "probability"));
        assertEquals(.123456, scale.fraction(".123456"));
        assertEquals("0.12", scale.text(.123456)); assertEquals("0.51", scale.text(.505));
        assertEquals(0, scale.fraction("1e-999999999"));
        assertThrows(IllegalArgumentException.class, () -> scale.fraction("."));
        assertThrows(IllegalArgumentException.class, () -> scale.fraction("1.01"));
        assertThrows(IllegalArgumentException.class, () -> scale.text(Double.NaN));
    }
    @Test void bothEndpointsRemainReachableWhenTheStepDoesNotDivideTheRange() {
        var scale = new SettingSlider(definition(".8", ".03", "probability"));
        assertEquals("0", scale.text(-1)); assertEquals("0.8", scale.text(1)); assertEquals("0.8", scale.text(2));
        assertEquals("0.39", scale.text(.5)); assertEquals(1, scale.fraction(".8"));
        assertFalse(SettingSlider.supports(definition("1", ".01", "ticks")));
        assertFalse(SettingSlider.supports(definition("1000", ".01", "probability")));
        assertThrows(IllegalArgumentException.class, () -> new SettingSlider(definition("1", ".01", "ticks")));
    }
    @Test void realShapeProbabilitiesQualifyWhileOperationalCountsDoNot() {
        var shapes = JsonSettings.find("skyweave_compat-shapes.json");
        var eligible = shapes.fields().stream().filter(SettingSlider::supports).toList();
        assertFalse(eligible.isEmpty());
        for (var definition : eligible) assertDoesNotThrow(() -> new SettingSlider(definition).fraction(definition.defaultText()));
        assertTrue(JsonSettings.find("skyweave_compat-wayfinder.json").fields().stream().noneMatch(SettingSlider::supports));
    }
}
