package com.veyon.veyflow.state;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Anotación que marca un parámetro como parte del estado del agente.
 * Puede usarse para indicar si un parámetro es obligatorio y para proporcionar
 * una descripción del mismo.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.PARAMETER})
public @interface StateParameter {
    
    /**
     * Indica si el parámetro es obligatorio.
     * 
     * @return true si el parámetro es obligatorio, false en caso contrario
     */
    boolean required() default false;
    
    /**
     * Descripción del parámetro.
     * 
     * @return Descripción del parámetro
     */
    String description() default "";
    
    /**
     * Nombre del parámetro. Si no se especifica, se usará el nombre del campo o parámetro.
     * 
     * @return Nombre del parámetro
     */
    String name() default "";
}
