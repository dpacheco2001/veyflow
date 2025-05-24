package com.veyon.veyflow.tools;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation for declaring parameters of tool methods.
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface ToolParameter {
    /**
     * The description of the parameter.
     */
    String value();

    /**
     * The JSON schema type of the parameter (e.g., "string", "integer", "boolean", "array", "object").
     */
    String type() default "string"; // Default to string, can be overridden

    /**
     * Whether the parameter is required.
     * Default is true, as most tool parameters are essential.
     */
    boolean required() default true;
}
