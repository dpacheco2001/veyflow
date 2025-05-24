package com.veyon.veyflow.core;

import com.veyon.veyflow.config.CompileConfig;
import com.veyon.veyflow.routing.NodeRouter;
import com.veyon.veyflow.state.AgentState;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.List;
import java.util.ArrayList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Representa un workflow de agente compilado y optimizado para ejecución.
 * Esta clase es inmutable y thread-safe, diseñada para alto rendimiento.
 */
public class CompiledWorkflow {
    private static final Logger log = LoggerFactory.getLogger(CompiledWorkflow.class);
    
    private final Map<String, AgentNode> nodes;
    private final Map<String, NodeRouter> routers;
    private final String entryNode;
    private final CompileConfig config;
    private final Map<String, List<String>> optimizedPaths;
    private final boolean hasCircularDependencies;
    private final Set<String> disconnectedNodes;
    
    // Constructor package-private - solo se puede crear desde AgentWorkflow.compile()
    CompiledWorkflow(
            Map<String, AgentNode> nodes,
            Map<String, NodeRouter> routers,
            String entryNode,
            CompileConfig config,
            Map<String, List<String>> optimizedPaths,
            boolean hasCircularDependencies,
            Set<String> disconnectedNodes) {
        this.nodes = new HashMap<>(nodes);
        this.routers = new HashMap<>(routers);
        this.entryNode = entryNode;
        this.config = config;
        this.optimizedPaths = optimizedPaths;
        this.hasCircularDependencies = hasCircularDependencies;
        this.disconnectedNodes = disconnectedNodes;
    }
    
    /**
     * Ejecuta el workflow compilado con el estado proporcionado.
     * Esta implementación es más eficiente que la versión no compilada.
     * 
     * @param state El estado inicial
     * @return El estado final después de la ejecución
     */
    public AgentState execute(AgentState state) {
        log.debug("Executing compiled workflow starting from node: {}", entryNode);
        
        // Inicializar estado con el nodo de entrada
        state.setCurrentNode(entryNode);
        
        int iterations = 0;
        String currentNodeName = entryNode;
        
        // Ejecutar hasta que no haya más transiciones o se alcance el límite de iteraciones
        while (currentNodeName != null && iterations < config.getMaxIterations()) {
            iterations++;
            
            // Obtener el nodo actual
            AgentNode currentNode = nodes.get(currentNodeName);
            if (currentNode == null) {
                log.error("Node not found: {}", currentNodeName);
                break;
            }
            
            // Procesar el estado con el nodo actual
            log.debug("Processing node: {}", currentNodeName);
            state = currentNode.process(state);
            
            // Si hay rutas optimizadas y la optimización está habilitada, usar rutas precalculadas
            if (config.shouldOptimizeExecution() && optimizedPaths.containsKey(currentNodeName)) {
                List<String> possibleNextNodes = optimizedPaths.get(currentNodeName);
                if (possibleNextNodes.size() == 1) {
                    // Camino único optimizado - saltar enrutamiento
                    currentNodeName = possibleNextNodes.get(0);
                    state.setCurrentNode(currentNodeName);
                    continue;
                }
            }
            
            // Obtener el enrutador para el nodo actual
            NodeRouter router = routers.get(currentNodeName);
            if (router == null) {
                log.debug("No router found for node: {}, workflow execution complete", currentNodeName);
                break;
            }
            
            // Calcular el siguiente nodo
            String nextNodeName = router.route(state);
            if (nextNodeName == null) {
                log.debug("Router returned null for node: {}, workflow execution complete", currentNodeName);
                break;
            }
            
            // Actualizar el nodo actual
            currentNodeName = nextNodeName;
            state.setCurrentNode(currentNodeName);
        }
        
        // Verificar si se alcanzó el límite de iteraciones
        if (iterations >= config.getMaxIterations()) {
            log.warn("Workflow execution reached max iterations ({})", config.getMaxIterations());
        }
        
        return state;
    }
    
    /**
     * Obtiene el nombre del nodo de entrada.
     * 
     * @return El nombre del nodo de entrada
     */
    public String getEntryNode() {
        return entryNode;
    }
    
    /**
     * Verifica si el workflow tiene dependencias circulares.
     * 
     * @return true si hay dependencias circulares, false en caso contrario
     */
    public boolean hasCircularDependencies() {
        return hasCircularDependencies;
    }
    
    /**
     * Obtiene los nodos desconectados (no alcanzables desde el nodo de entrada).
     * 
     * @return Conjunto de nombres de nodos desconectados
     */
    public Set<String> getDisconnectedNodes() {
        return new HashSet<>(disconnectedNodes);
    }
    
    /**
     * Obtiene la configuración de compilación utilizada.
     * 
     * @return La configuración de compilación
     */
    public CompileConfig getConfig() {
        return config;
    }
    
    /**
     * Obtiene un nodo por su nombre.
     * 
     * @param nodeName El nombre del nodo
     * @return El nodo, o null si no se encuentra
     */
    public AgentNode getNode(String nodeName) {
        return nodes.get(nodeName);
    }
    
    /**
     * Obtiene todos los nombres de nodos en el workflow.
     * 
     * @return Conjunto de nombres de nodos
     */
    public Set<String> getNodeNames() {
        return new HashSet<>(nodes.keySet());
    }
}
