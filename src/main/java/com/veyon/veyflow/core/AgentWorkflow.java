package com.veyon.veyflow.core;

import com.veyon.veyflow.routing.NodeRouter;
import com.veyon.veyflow.routing.LinearRouter;
import com.veyon.veyflow.state.AgentState;
import com.veyon.veyflow.config.CompileConfig;
import com.veyon.veyflow.state.AgentStateRepository;
import com.veyon.veyflow.state.InMemoryAgentStateRepository;

import java.util.Map;
import java.util.HashMap;
import java.util.Set;
import java.util.HashSet;
import java.util.List;
import java.util.ArrayList;
import java.util.Queue;
import java.util.LinkedList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Represents a workflow of agent nodes.
 * This class is used to define and execute a flow of nodes.
 */
public class AgentWorkflow {
    private static final Logger log = LoggerFactory.getLogger(AgentWorkflow.class);
    
    private final Map<String, AgentNode> nodes;
    private final Map<String, NodeRouter> routers;
    private final String entryNode;
    private final AgentExecutor executor;
    private final AgentStateRepository agentStateRepository;
    
    /**
     * Create a new agent workflow with a specific state repository.
     * 
     * @param entryNode The name of the entry node
     * @param agentStateRepository The repository for agent state
     */
    public AgentWorkflow(String entryNode, AgentStateRepository agentStateRepository) {
        this.nodes = new HashMap<>();
        this.routers = new HashMap<>();
        this.entryNode = entryNode;
        this.agentStateRepository = agentStateRepository;
        this.executor = new AgentExecutor(entryNode, this.agentStateRepository);
    }

    /**
     * Create a new agent workflow with a default InMemoryAgentStateRepository.
     * 
     * @param entryNode The name of the entry node
     */
    public AgentWorkflow(String entryNode) {
        this(entryNode, new InMemoryAgentStateRepository());
    }
    
    /**
     * Add a node to the workflow.
     * 
     * @param node The node to add
     * @return This workflow instance for chaining
     */
    public AgentWorkflow addNode(AgentNode node) {
        nodes.put(node.getName(), node);
        executor.registerNode(node);
        return this;
    }
    
    /**
     * Add a router for a node.
     * 
     * @param nodeName The name of the node
     * @param router The router for the node
     * @return This workflow instance for chaining
     */
    public AgentWorkflow addRouter(String nodeName, NodeRouter router) {
        routers.put(nodeName, router);
        executor.registerRouter(nodeName, router);
        return this;
    }
    
    /**
     * Add a linear edge between two nodes.
     * 
     * @param sourceNode The name of the source node
     * @param targetNode The name of the target node
     * @return This workflow instance for chaining
     */
    public AgentWorkflow addEdge(String sourceNode, String targetNode) {
        LinearRouter router = new LinearRouter(sourceNode, targetNode);
        return addRouter(sourceNode, router);
    }
    
    /**
     * Execute the workflow with the given state.
     * 
     * @param state The initial state
     * @return The final state after execution
     */
    public AgentState execute(AgentState state) {
        log.debug("Executing workflow starting from node: {}", entryNode);
        return executor.execute(state);
    }
    
    /**
     * Compila el workflow para optimizar su ejecución.
     * 
     * @return El workflow compilado
     */
    public CompiledWorkflow compile() {
        return compile(CompileConfig.defaults());
    }
    
    /**
     * Compila el workflow con la configuración especificada.
     * 
     * @param config La configuración de compilación
     * @return El workflow compilado
     */
    public CompiledWorkflow compile(CompileConfig config) {
        log.info("Compiling workflow with entry node: {}", entryNode);
        
        // Verificar que el nodo de entrada existe
        if (!nodes.containsKey(entryNode)) {
            throw new IllegalStateException("Entry node not found: " + entryNode);
        }
        
        // Detectar dependencias circulares y nodos desconectados
        boolean hasCircularDependencies = false;
        Set<String> disconnectedNodes = new HashSet<>();
        Map<String, List<String>> optimizedPaths = new HashMap<>();
        
        if (config.shouldValidateGraph()) {
            // Detectar dependencias circulares
            hasCircularDependencies = detectCircularDependencies();
            
            // Detectar nodos desconectados
            disconnectedNodes = detectDisconnectedNodes();
            
            if (hasCircularDependencies) {
                log.warn("Circular dependencies detected in workflow");
            }
            
            if (!disconnectedNodes.isEmpty()) {
                log.warn("Disconnected nodes detected in workflow: {}", disconnectedNodes);
            }
        }
        
        // Generar rutas optimizadas si está habilitado
        if (config.shouldOptimizeExecution()) {
            optimizedPaths = generateOptimizedPaths();
        }
        
        // Crear y devolver el workflow compilado
        return new CompiledWorkflow(
            nodes,
            routers,
            entryNode,
            config,
            optimizedPaths,
            hasCircularDependencies,
            disconnectedNodes,
            this.agentStateRepository
        );
    }
    
    /**
     * Detecta dependencias circulares en el workflow.
     * 
     * @return true si hay dependencias circulares, false en caso contrario
     */
    private boolean detectCircularDependencies() {
        Set<String> visited = new HashSet<>();
        Set<String> recursionStack = new HashSet<>();
        
        for (String nodeName : nodes.keySet()) {
            if (detectCircularDependenciesUtil(nodeName, visited, recursionStack)) {
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * Función auxiliar para detectar dependencias circulares.
     * 
     * @param nodeName El nombre del nodo actual
     * @param visited Conjunto de nodos visitados
     * @param recursionStack Pila de recursión actual
     * @return true si hay un ciclo, false en caso contrario
     */
    private boolean detectCircularDependenciesUtil(String nodeName, Set<String> visited, Set<String> recursionStack) {
        // Si el nodo no está en el conjunto de visitados, marcarlo como visitado
        if (!visited.contains(nodeName)) {
            visited.add(nodeName);
            recursionStack.add(nodeName);
            
            // Obtener el enrutador para este nodo
            NodeRouter router = routers.get(nodeName);
            if (router != null) {
                // Obtener posibles nodos siguientes (esto es una simplificación, en realidad
                // necesitaríamos simular todas las posibles rutas)
                List<String> possibleNextNodes = getPossibleNextNodes(router);
                
                for (String nextNode : possibleNextNodes) {
                    // Si el siguiente nodo ya está en la pila de recursión, hay un ciclo
                    if (recursionStack.contains(nextNode)) {
                        return true;
                    }
                    
                    // Si el siguiente nodo no ha sido visitado y hay un ciclo en su subgrafo
                    if (!visited.contains(nextNode) && detectCircularDependenciesUtil(nextNode, visited, recursionStack)) {
                        return true;
                    }
                }
            }
        }
        
        // Quitar el nodo de la pila de recursión
        recursionStack.remove(nodeName);
        return false;
    }
    
    /**
     * Detecta nodos desconectados (no alcanzables desde el nodo de entrada).
     * 
     * @return Conjunto de nombres de nodos desconectados
     */
    private Set<String> detectDisconnectedNodes() {
        Set<String> visited = new HashSet<>();
        Queue<String> queue = new LinkedList<>();
        
        // Comenzar BFS desde el nodo de entrada
        queue.add(entryNode);
        visited.add(entryNode);
        
        while (!queue.isEmpty()) {
            String nodeName = queue.poll();
            
            // Obtener el enrutador para este nodo
            NodeRouter router = routers.get(nodeName);
            if (router != null) {
                // Obtener posibles nodos siguientes
                List<String> possibleNextNodes = getPossibleNextNodes(router);
                
                for (String nextNode : possibleNextNodes) {
                    if (!visited.contains(nextNode) && nodes.containsKey(nextNode)) {
                        visited.add(nextNode);
                        queue.add(nextNode);
                    }
                }
            }
        }
        
        // Los nodos desconectados son aquellos que no fueron visitados
        Set<String> disconnected = new HashSet<>(nodes.keySet());
        disconnected.removeAll(visited);
        return disconnected;
    }
    
    /**
     * Genera rutas optimizadas para el workflow.
     * 
     * @return Mapa de rutas optimizadas por nodo
     */
    private Map<String, List<String>> generateOptimizedPaths() {
        Map<String, List<String>> optimizedPaths = new HashMap<>();
        
        for (String nodeName : nodes.keySet()) {
            NodeRouter router = routers.get(nodeName);
            if (router != null) {
                // Determinar si este nodo tiene una única salida posible
                List<String> possibleNextNodes = getPossibleNextNodes(router);
                
                if (possibleNextNodes.size() == 1) {
                    optimizedPaths.put(nodeName, possibleNextNodes);
                    log.debug("Optimized path from {} to {}", nodeName, possibleNextNodes.get(0));
                }
            }
        }
        
        return optimizedPaths;
    }
    
    /**
     * Obtiene los posibles nodos siguientes para un enrutador.
     * Este es un método simplificado, ya que algunos enrutadores pueden tener
     * comportamiento dinámico basado en el estado.
     * 
     * @param router El enrutador
     * @return Lista de posibles nodos siguientes
     */
    private List<String> getPossibleNextNodes(NodeRouter router) {
        // Esta es una implementación simplificada
        // Para enrutadores complejos, necesitaríamos una interfaz adicional
        if (router instanceof LinearRouter) {
            LinearRouter linearRouter = (LinearRouter) router;
            return List.of(linearRouter.getTargetNode());
        }
        
        // Para otros tipos de enrutadores, esto es una aproximación
        // Idealmente, cada tipo de enrutador debería poder reportar sus posibles destinos
        return new ArrayList<>();
    }
    
    /**
     * Get a node by name.
     * 
     * @param nodeName The name of the node
     * @return The node, or null if not found
     */
    public AgentNode getNode(String nodeName) {
        return nodes.get(nodeName);
    }
    
    /**
     * Get a router by node name.
     * 
     * @param nodeName The name of the node
     * @return The router, or null if not found
     */
    public NodeRouter getRouter(String nodeName) {
        return routers.get(nodeName);
    }
    
    /**
     * Get the entry node name.
     * 
     * @return The entry node name
     */
    public String getEntryNode() {
        return entryNode;
    }
    
    /**
     * Shutdown the workflow executor.
     */
    public void shutdown() {
        executor.shutdown();
    }
}
