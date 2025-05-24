package com.veyon.veyflow.config;

import java.util.HashMap;
import java.util.Map;

/**
 * Configuración para la ejecución de un agente.
 * Define parámetros específicos para la ejecución de un workflow de agente,
 * como identificadores de tenant y thread, y parámetros personalizados.
 */
public class AgentConfig {
    private final String threadId;
    private final String tenantId;
    private final Map<String, Object> parameters;
    
    private AgentConfig(String threadId, String tenantId, Map<String, Object> parameters) {
        this.threadId = threadId;
        this.tenantId = tenantId;
        this.parameters = parameters;
    }
    
    /**
     * Obtiene el ID del thread.
     * 
     * @return El ID del thread
     */
    public String getThreadId() {
        return threadId;
    }
    
    /**
     * Obtiene el ID del tenant.
     * 
     * @return El ID del tenant
     */
    public String getTenantId() {
        return tenantId;
    }
    
    /**
     * Obtiene un parámetro personalizado por su clave.
     * 
     * @param key La clave del parámetro
     * @return El valor del parámetro, o null si no existe
     */
    @SuppressWarnings("unchecked")
    public <T> T getParameter(String key) {
        return (T) parameters.get(key);
    }
    
    /**
     * Verifica si existe un parámetro con la clave especificada.
     * 
     * @param key La clave del parámetro
     * @return true si existe, false en caso contrario
     */
    public boolean hasParameter(String key) {
        return parameters.containsKey(key);
    }
    
    /**
     * Obtiene todos los parámetros personalizados.
     * 
     * @return Mapa con todos los parámetros
     */
    public Map<String, Object> getAllParameters() {
        return new HashMap<>(parameters);
    }
    
    /**
     * Crea un nuevo builder para configurar un AgentConfig.
     * 
     * @return Un nuevo builder
     */
    public static Builder builder() {
        return new Builder();
    }
    
    /**
     * Builder para crear instancias de AgentConfig.
     */
    public static class Builder {
        private String threadId;
        private String tenantId;
        private final Map<String, Object> parameters = new HashMap<>();
        
        /**
         * Establece el ID del thread.
         * 
         * @param threadId El ID del thread
         * @return Este builder para encadenamiento
         */
        public Builder threadId(String threadId) {
            this.threadId = threadId;
            return this;
        }
        
        /**
         * Establece el ID del tenant.
         * 
         * @param tenantId El ID del tenant
         * @return Este builder para encadenamiento
         */
        public Builder tenantId(String tenantId) {
            this.tenantId = tenantId;
            return this;
        }
        
        /**
         * Añade un parámetro personalizado.
         * 
         * @param key La clave del parámetro
         * @param value El valor del parámetro
         * @return Este builder para encadenamiento
         */
        public Builder parameter(String key, Object value) {
            this.parameters.put(key, value);
            return this;
        }
        
        /**
         * Añade múltiples parámetros personalizados.
         * 
         * @param parameters Mapa de parámetros a añadir
         * @return Este builder para encadenamiento
         */
        public Builder parameters(Map<String, Object> parameters) {
            this.parameters.putAll(parameters);
            return this;
        }
        
        /**
         * Construye la instancia de AgentConfig.
         * 
         * @return La instancia configurada de AgentConfig
         * @throws IllegalArgumentException Si faltan parámetros obligatorios
         */
        public AgentConfig build() {
            if (threadId == null || threadId.isEmpty()) {
                throw new IllegalArgumentException("threadId es obligatorio");
            }
            if (tenantId == null || tenantId.isEmpty()) {
                throw new IllegalArgumentException("tenantId es obligatorio");
            }
            
            return new AgentConfig(threadId, tenantId, parameters);
        }
    }
}
