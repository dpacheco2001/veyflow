package com.veyon.veyflow.config;

/**
 * Configuración para la compilación de un workflow de agente.
 * Define opciones y comportamientos para la fase de compilación.
 */
public class CompileConfig {
    private final boolean validateGraph;
    private final boolean optimizeExecution;
    private final boolean cacheNodes;
    private final int maxIterations;
    
    private CompileConfig(boolean validateGraph, boolean optimizeExecution, boolean cacheNodes, int maxIterations) {
        this.validateGraph = validateGraph;
        this.optimizeExecution = optimizeExecution;
        this.cacheNodes = cacheNodes;
        this.maxIterations = maxIterations;
    }
    
    /**
     * Indica si se debe validar la estructura del grafo durante la compilación.
     * Esto incluye verificar ciclos, nodos desconectados, etc.
     * 
     * @return true si se debe validar, false en caso contrario
     */
    public boolean shouldValidateGraph() {
        return validateGraph;
    }
    
    /**
     * Indica si se deben aplicar optimizaciones de ejecución.
     * 
     * @return true si se deben aplicar optimizaciones, false en caso contrario
     */
    public boolean shouldOptimizeExecution() {
        return optimizeExecution;
    }
    
    /**
     * Indica si se deben cachear los nodos para reutilización.
     * 
     * @return true si se deben cachear, false en caso contrario
     */
    public boolean shouldCacheNodes() {
        return cacheNodes;
    }
    
    /**
     * Obtiene el número máximo de iteraciones permitidas en un workflow.
     * 
     * @return Número máximo de iteraciones
     */
    public int getMaxIterations() {
        return maxIterations;
    }
    
    /**
     * Crea un nuevo builder para configurar CompileConfig.
     * 
     * @return Un nuevo builder
     */
    public static Builder builder() {
        return new Builder();
    }
    
    /**
     * Retorna una configuración por defecto.
     * 
     * @return Configuración por defecto
     */
    public static CompileConfig defaults() {
        return builder().build();
    }
    
    /**
     * Builder para crear instancias de CompileConfig.
     */
    public static class Builder {
        private boolean validateGraph = true;
        private boolean optimizeExecution = true;
        private boolean cacheNodes = true;
        private int maxIterations = 100;
        
        /**
         * Establece si se debe validar la estructura del grafo.
         * 
         * @param validateGraph true para validar, false para omitir validación
         * @return Este builder para encadenamiento
         */
        public Builder validateGraph(boolean validateGraph) {
            this.validateGraph = validateGraph;
            return this;
        }
        
        /**
         * Establece si se deben aplicar optimizaciones de ejecución.
         * 
         * @param optimizeExecution true para optimizar, false para ejecución estándar
         * @return Este builder para encadenamiento
         */
        public Builder optimizeExecution(boolean optimizeExecution) {
            this.optimizeExecution = optimizeExecution;
            return this;
        }
        
        /**
         * Establece si se deben cachear los nodos para reutilización.
         * 
         * @param cacheNodes true para cachear, false para recrear
         * @return Este builder para encadenamiento
         */
        public Builder cacheNodes(boolean cacheNodes) {
            this.cacheNodes = cacheNodes;
            return this;
        }
        
        /**
         * Establece el número máximo de iteraciones permitidas.
         * 
         * @param maxIterations Número máximo de iteraciones
         * @return Este builder para encadenamiento
         */
        public Builder maxIterations(int maxIterations) {
            if (maxIterations <= 0) {
                throw new IllegalArgumentException("maxIterations debe ser mayor que 0");
            }
            this.maxIterations = maxIterations;
            return this;
        }
        
        /**
         * Construye la instancia de CompileConfig.
         * 
         * @return La instancia configurada de CompileConfig
         */
        public CompileConfig build() {
            return new CompileConfig(validateGraph, optimizeExecution, cacheNodes, maxIterations);
        }
    }
}
