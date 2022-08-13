package org.rootive.annotation;

import java.lang.annotation.*;

@Retention(RetentionPolicy.RUNTIME)
@Documented
@Target(value = { ElementType.FIELD, ElementType.LOCAL_VARIABLE })
public @interface Reference {
    String value() default "@.null";
    String namespace() default "@.null";
}
