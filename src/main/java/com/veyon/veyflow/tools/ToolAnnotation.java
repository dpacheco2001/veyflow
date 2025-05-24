package com.veyon.veyflow.tools;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation for declaring methods as tools in the agent framework.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface ToolAnnotation {
    /**
     * The description of the tool.
     */
    String value();
    
    /**
     * Whether to automatically recall the LLM after tool execution.
     * Default is true.
     */
    boolean recall() default true;
}
