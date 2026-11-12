package design.inherent.skyweavecompat.configuration;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Owned record constraints consumed by both constructors and the configuration editor. */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.RECORD_COMPONENT)
public @interface SettingSpec {
    String minimum() default "";
    String maximum() default "";
    boolean exclusiveMinimum() default false;
    String step() default "";
    String unit() default "";
    String group() default "";
    int maximumEntries() default -1;
    int maximumLength() default -1;
    boolean unique() default false;
    String pattern() default "";
}
