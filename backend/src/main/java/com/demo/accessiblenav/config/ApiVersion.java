package com.demo.accessiblenav.config;

import java.lang.annotation.*;

/**
 * API版本标注
 * 用于标记Controller或方法的API版本
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface ApiVersion {

    /**
     * API版本号，如 "1", "2"
     */
    String value() default "1";
}
